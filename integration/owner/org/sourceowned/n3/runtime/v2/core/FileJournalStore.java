package org.sourceowned.n3.runtime.v2.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import static org.sourceowned.n3.runtime.v2.core.Contracts.*;
import static org.sourceowned.n3.runtime.v2.core.Model.*;

/**
 * Generic owned-path host journal: file fsync -> same-directory atomic replacement -> directory fsync.
 * Parent must already exist and be trusted. No device path, mode, label, encryption, or signing claim.
 * Cooperative exclusive lock prevents another core/process from overlapping the ownership interval.
 */
public final class FileJournalStore implements Store {
    public enum Stage { BEFORE_CREATE_STAGING, BEFORE_WRITE_BYTES, BEFORE_FILE_FSYNC,
                        BEFORE_ATOMIC_RENAME, BEFORE_DIRECTORY_FSYNC, AFTER_DIRECTORY_FSYNC }
    public interface Faults { void at(Stage stage) throws IOException; }
    private static final Faults NO_FAULTS = new Faults() { @Override public void at(Stage stage) { } };
    private final Path journal;
    private final Path staging;
    private final Path ownerLock;
    private final Path parent;
    private final Faults faults;

    public FileJournalStore(Path journal) { this(journal, NO_FAULTS); }
    /** Fault injection is for host tests; it cannot turn an fsync/atomic-rename failure into success. */
    public FileJournalStore(Path journal, Faults faults) {
        this.journal = Objects.requireNonNull(journal, "journal").toAbsolutePath().normalize();
        parent = this.journal.getParent();
        if (parent == null || this.journal.getFileName() == null) throw new IllegalArgumentException("file path required");
        staging = parent.resolve(this.journal.getFileName().toString() + ".pending-write");
        ownerLock = parent.resolve(this.journal.getFileName().toString() + ".owner.lock");
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    @Override public Store.Session open() throws StoreFailure {
        FileChannel channel = null;
        try {
            for (Path part = parent; part != null; part = part.getParent()) {
                if (Files.isSymbolicLink(part)) throw new StoreFailure(StoreReason.IO);
            }
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(journal) || Files.isSymbolicLink(ownerLock)) throw new StoreFailure(StoreReason.IO);
            channel = FileChannel.open(ownerLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            FileLock lock = channel.tryLock();
            if (lock == null) throw new StoreFailure(StoreReason.OWNERSHIP_BUSY);
            return new OwnedSession(channel, lock);
        } catch (OverlappingFileLockException busy) {
            closeQuietly(channel); throw new StoreFailure(StoreReason.OWNERSHIP_BUSY);
        } catch (StoreFailure failure) {
            closeQuietly(channel); throw failure;
        } catch (IOException failure) {
            closeQuietly(channel); throw new StoreFailure(StoreReason.IO);
        } catch (RuntimeException failure) {
            closeQuietly(channel); throw new StoreFailure(StoreReason.IO);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) try { channel.close(); } catch (IOException ignored) { /* no success result follows */ }
    }

    private final class OwnedSession implements Store.Session {
        private final FileChannel lockChannel;
        private final FileLock lock;
        private boolean closed;
        private StoreReason poisoned;
        private OwnedSession(FileChannel channel, FileLock lock) { lockChannel = channel; this.lock = lock; }

        @Override public synchronized Load load(Digest expectedBoard) {
            if (closed) return Load.unknown(StoreReason.CLOSED);
            if (poisoned != null) return Load.unknown(poisoned);
            try {
                expectedBoard.requireKind(DigestKind.BOARD);
                if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) return Load.unknown(StoreReason.ORPHAN_STAGING);
                if (!Files.notExists(staging, LinkOption.NOFOLLOW_LINKS)) return Load.unknown(StoreReason.IO);
                if (Files.notExists(journal, LinkOption.NOFOLLOW_LINKS)) return Load.absent();
                if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) return Load.unknown(StoreReason.IO);
                if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) return Load.unknown(StoreReason.CORRUPT);
                try (FileChannel data = FileChannel.open(journal, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                    long size = data.size();
                    if (size <= 0 || size > Journal.MAX_FILE_BYTES) return Load.unknown(StoreReason.CORRUPT);
                    ByteBuffer bytes = ByteBuffer.allocate((int) size);
                    while (bytes.hasRemaining()) if (data.read(bytes) < 0) return Load.unknown(StoreReason.CORRUPT);
                    if (data.size() != size || data.read(ByteBuffer.allocate(1)) != -1) return Load.unknown(StoreReason.CORRUPT);
                    return Load.valid(Journal.Codec.decode(bytes.array(), expectedBoard));
                }
            } catch (Journal.CodecFailure failure) { return Load.unknown(failure.reason); }
            catch (IOException failure) { return Load.unknown(StoreReason.IO); }
            catch (RuntimeException failure) { return Load.unknown(StoreReason.IO); }
        }

        @Override public synchronized void write(Journal.Record next, long expectedRevision) throws StoreFailure {
            if (closed) throw new StoreFailure(StoreReason.CLOSED);
            if (poisoned != null) throw new StoreFailure(poisoned);
            Load loaded = load(next.board);
            if (loaded.status == LoadStatus.UNKNOWN) throw new StoreFailure(loaded.reason);
            long actualRevision = loaded.status == LoadStatus.ABSENT ? 0 : loaded.record.revision;
            if (actualRevision != expectedRevision || expectedRevision == Long.MAX_VALUE
                || next.revision != expectedRevision + 1) throw new StoreFailure(StoreReason.REVISION_CONFLICT);
            byte[] bytes = Journal.Codec.encode(next);
            boolean renamed = false;
            boolean created = false;
            try {
                faults.at(Stage.BEFORE_CREATE_STAGING);
                try (FileChannel data = FileChannel.open(staging, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    created = true;
                    faults.at(Stage.BEFORE_WRITE_BYTES);
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) data.write(buffer);
                    faults.at(Stage.BEFORE_FILE_FSYNC);
                    data.force(true);
                }
                faults.at(Stage.BEFORE_ATOMIC_RENAME);
                // No non-atomic/copy fallback, even if the filesystem does not support atomic replacement.
                Files.move(staging, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                renamed = true;
                faults.at(Stage.BEFORE_DIRECTORY_FSYNC);
                try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) { directory.force(true); }
                faults.at(Stage.AFTER_DIRECTORY_FSYNC);
            } catch (IOException failure) {
                poisoned = StoreReason.WRITE_FAILED;
                if (created && !renamed) try { Files.deleteIfExists(staging); } catch (IOException ignored) { /* retained unknown */ }
                throw new StoreFailure(poisoned);
            } catch (RuntimeException failure) {
                poisoned = StoreReason.WRITE_FAILED;
                if (created && !renamed) try { Files.deleteIfExists(staging); } catch (IOException ignored) { /* retained unknown */ }
                throw new StoreFailure(poisoned);
            }
        }

        @Override public synchronized void close() throws StoreFailure {
            if (closed) return;
            closed = true;
            try { lock.release(); lockChannel.close(); }
            catch (IOException failure) { closeQuietly(lockChannel); throw new StoreFailure(StoreReason.IO); }
        }
    }
}
