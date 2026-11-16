package org.sourceowned.n3.runtime.v2;

import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/** Fixed Android /data storage; init must provision 0700 dir owned by this process, correct label. */
public final class AndroidJournalFilesystem implements DurableJournalStore.Filesystem {
    public static final String ROM_DIR = "/data/system/n3-routing-v2";

    private final String directory;
    private final int uid;
    private final String journal, staging, owner;
    public AndroidJournalFilesystem(boolean rootTest) {
        if (rootTest) throw new SecurityException("ROM only");
        uid = Process.myUid();
        if (uid != 1000) throw new SecurityException("dedicated owner UID required");
        directory = ROM_DIR;
        journal = directory + "/journal.bin";
        staging = directory + "/journal.pending-write";
        owner = directory + "/owner.lock";
    }
    private IOException io(Exception cause) { return new IOException("private journal operation unavailable", cause); }
    private void directoryCheck() throws ErrnoException, IOException {
        String part = directory;
        boolean leaf = true;
        while (true) {
            StructStat stat = Os.lstat(part);
            if (!OsConstants.S_ISDIR(stat.st_mode) || (stat.st_mode & 0002) != 0
                    || (leaf ? stat.st_uid != uid || (stat.st_mode & 0777) != 0700
                             : stat.st_uid != 0 && stat.st_uid != 1000))
                throw new IOException("unsafe journal parent");
            if (!leaf && (stat.st_mode & 0020) != 0 && stat.st_gid != 0 && stat.st_gid != 1000)
                throw new IOException("untrusted writable parent group");
            if (part.equals("/")) break;
            int slash = part.lastIndexOf('/');
            part = slash == 0 ? "/" : part.substring(0, slash);
            leaf = false;
        }
    }
    private void verify(FileDescriptor fd) throws ErrnoException, IOException {
        StructStat stat = Os.fstat(fd);
        if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_uid != uid || stat.st_nlink != 1
                || (stat.st_mode & 0777) != 0600) throw new IOException("unsafe journal member");
    }
    @Override public AutoCloseable acquireExclusive() throws IOException {
        FileDescriptor fd = null;
        FileOutputStream stream = null;
        try {
            directoryCheck();
            fd = Os.open(owner, OsConstants.O_CREAT | OsConstants.O_WRONLY | OsConstants.O_NOFOLLOW
                    | OsConstants.O_CLOEXEC, 0600);
            verify(fd);
            stream = new FileOutputStream(fd);
            FileChannel channel = stream.getChannel();
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException busy) { throw new DurableJournalStore.Busy(); }
            if (lock == null) throw new DurableJournalStore.Busy();
            FileOutputStream held = stream;
            return () -> { try { lock.release(); } finally { held.close(); } };
        } catch (Exception failure) {
            if (stream != null) try { stream.close(); } catch (Exception ignored) { }
            else if (fd != null) try { Os.close(fd); } catch (Exception ignored) { }
            if (failure instanceof DurableJournalStore.Busy) throw (DurableJournalStore.Busy) failure;
            throw io(failure);
        }
    }
    @Override public boolean stagingExists() throws IOException {
        try { Os.lstat(staging); return true; }
        catch (ErrnoException error) { if (error.errno == OsConstants.ENOENT) return false; throw io(error); }
    }
    @Override public byte[] readJournal(int limit) throws IOException {
        FileDescriptor fd = null;
        try {
            directoryCheck();
            try { fd = Os.open(journal, OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC, 0); }
            catch (ErrnoException missing) { if (missing.errno == OsConstants.ENOENT) return null; throw missing; }
            verify(fd);
            long size = Os.fstat(fd).st_size;
            if (size <= 0 || size > limit) throw new IOException("invalid journal size");
            byte[] bytes = new byte[(int) size];
            int offset = 0;
            while (offset < bytes.length) {
                int count = Os.read(fd, bytes, offset, bytes.length - offset);
                if (count <= 0) throw new IOException("short journal");
                offset += count;
            }
            if (Os.read(fd, new byte[1], 0, 1) != 0 || Os.fstat(fd).st_size != size)
                throw new IOException("changing journal");
            return bytes;
        } catch (Exception failure) { throw io(failure); }
        finally { if (fd != null) try { Os.close(fd); } catch (Exception ignored) { } }
    }
    @Override public void createStagingAndSync(byte[] bytes) throws IOException {
        FileDescriptor fd = null;
        try {
            directoryCheck();
            fd = Os.open(staging, OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL
                    | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC, 0600);
            verify(fd);
            int offset = 0;
            while (offset < bytes.length) {
                int count = Os.write(fd, bytes, offset, bytes.length - offset);
                if (count <= 0) throw new IOException("short write");
                offset += count;
            }
            Os.fsync(fd);
            Os.close(fd); fd = null;
        } catch (Exception failure) { throw io(failure); }
        finally { if (fd != null) try { Os.close(fd); } catch (Exception ignored) { } }
    }
    @Override public void renameStaging() throws IOException {
        try { directoryCheck(); Os.rename(staging, journal); }
        catch (Exception failure) { throw io(failure); }
    }
    @Override public void syncDirectory() throws IOException {
        FileDescriptor fd = null;
        try {
            // O_DIRECTORY is not exposed by the SDK; open nofollow and verify descriptor type instead.
            fd = Os.open(directory, OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW
                    | OsConstants.O_CLOEXEC, 0);
            StructStat stat = Os.fstat(fd);
            if (!OsConstants.S_ISDIR(stat.st_mode) || stat.st_uid != uid || (stat.st_mode & 0777) != 0700)
                throw new IOException("unsafe directory descriptor");
            Os.fsync(fd);
            Os.close(fd); fd = null;
        } catch (Exception failure) { throw io(failure); }
        finally { if (fd != null) try { Os.close(fd); } catch (Exception ignored) { } }
    }
    @Override public void clearJournal() throws IOException {
        FileDescriptor fd = null;
        try {
            directoryCheck();
            if (stagingExists()) throw new IOException("incomplete durable write cannot be aborted");
            fd = Os.open(journal, OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC, 0);
            verify(fd);
            StructStat opened = Os.fstat(fd), named = Os.lstat(journal);
            if (opened.st_dev != named.st_dev || opened.st_ino != named.st_ino)
                throw new IOException("journal member changed");
            Os.remove(journal);
        } catch (Exception failure) { throw io(failure); }
        finally { if (fd != null) try { Os.close(fd); } catch (Exception ignored) { } }
    }
}
