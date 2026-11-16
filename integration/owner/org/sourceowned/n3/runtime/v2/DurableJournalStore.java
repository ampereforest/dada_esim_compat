package org.sourceowned.n3.runtime.v2;

import java.io.IOException;
import java.util.Objects;
import org.sourceowned.n3.runtime.v2.core.Contracts;
import org.sourceowned.n3.runtime.v2.core.Journal;
import org.sourceowned.n3.runtime.v2.core.Model;
import static org.sourceowned.n3.runtime.v2.core.Contracts.*;

/** File bytes -> file fsync -> atomic rename -> directory fsync. No recovery/delete fallback. */
public final class DurableJournalStore implements Store {
    /** Paths are fixed private-directory members. Implementation must reject symlinks/unsafe metadata. */
    public interface Filesystem {
        AutoCloseable acquireExclusive() throws IOException;
        boolean stagingExists() throws IOException;
        byte[] readJournal(int limit) throws IOException; // null only for authoritative ENOENT
        void createStagingAndSync(byte[] bytes) throws IOException; // exclusive create, preserve on error
        void renameStaging() throws IOException; // same-directory POSIX atomic rename, no copy fallback
        void syncDirectory() throws IOException;
        /** Engineering recovery only; legacy core storage never invokes deletion. */
        default void clearJournal() throws IOException { throw new IOException("journal deletion unsupported"); }
    }
    private final Filesystem fs;
    public DurableJournalStore(Filesystem fs) { this.fs = Objects.requireNonNull(fs); }
    @Override public Session open() throws StoreFailure {
        try { return new Owned(fs.acquireExclusive()); }
        catch (Busy failure) { throw new StoreFailure(StoreReason.OWNERSHIP_BUSY); }
        catch (Exception failure) { throw new StoreFailure(StoreReason.IO); }
    }
    public static final class Busy extends IOException {
        private static final long serialVersionUID = 1L;
        public Busy() { super("owner busy"); }
    }
    private final class Owned implements Session {
        private final AutoCloseable lock;
        private boolean closed;
        private StoreReason poisoned;
        Owned(AutoCloseable lock) { this.lock = Objects.requireNonNull(lock); }
        private Load unknown(StoreReason reason) { poisoned = reason; return Load.unknown(reason); }
        @Override public synchronized Load load(Model.Digest board) {
            if (closed) return Load.unknown(StoreReason.CLOSED);
            if (poisoned != null) return Load.unknown(poisoned);
            try {
                if (fs.stagingExists()) return unknown(StoreReason.ORPHAN_STAGING);
                byte[] bytes = fs.readJournal(Journal.MAX_FILE_BYTES);
                return bytes == null ? Load.absent() : Load.valid(Journal.Codec.decode(bytes, board));
            } catch (Journal.CodecFailure corrupt) { return unknown(corrupt.reason); }
            catch (Exception failure) { return unknown(StoreReason.IO); }
        }
        @Override public synchronized void write(Journal.Record next, long expected) throws StoreFailure {
            if (closed) throw new StoreFailure(StoreReason.CLOSED);
            if (poisoned != null) throw new StoreFailure(poisoned);
            Load loaded = load(next.board);
            if (loaded.status == LoadStatus.UNKNOWN) throw new StoreFailure(loaded.reason);
            long actual = loaded.status == LoadStatus.ABSENT ? 0 : loaded.record.revision;
            if (expected < 0 || actual != expected || expected == Long.MAX_VALUE || next.revision != expected + 1)
                throw new StoreFailure(StoreReason.REVISION_CONFLICT);
            try {
                byte[] bytes = Journal.Codec.encode(next);
                fs.createStagingAndSync(bytes);
                fs.renameStaging();
                fs.syncDirectory();
            } catch (Exception failure) {
                poisoned = StoreReason.WRITE_FAILED;
                // Preserve both old/new journal and any orphan staging; do not retry or clean up.
                throw new StoreFailure(poisoned);
            }
        }
        @Override public synchronized void close() throws StoreFailure {
            if (closed) return;
            closed = true;
            try { lock.close(); } catch (Exception failure) { throw new StoreFailure(StoreReason.IO); }
        }
    }
}
