package org.sourceowned.n3.runtime.v2;

import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Durable engineering intent, NOT core confirmation/generation/lease. Never silently clear/replay. */
public final class ExperimentalIntent implements AutoCloseable {
    private final DurableJournalStore.Filesystem fs;
    private final AutoCloseable owner;
    private boolean blocked, poisoned;
    private String activeToken;
    private int activeDestination;
    public ExperimentalIntent(DurableJournalStore.Filesystem fs) throws Exception {
        this.fs = fs; owner = fs.acquireExclusive();
        try {
            byte[] bytes = fs.readJournal(16384);
            if (bytes != null) {
                String[] fields = new String(bytes, StandardCharsets.UTF_8).split("\n");
                if (fields.length != 4 || !fields[0].equals("N3-ENGINEERING-INTENT-V2")
                        || !fields[2].matches("[01]") || !fields[3].matches("[0-9a-f]{64}"))
                    throw new IOException("invalid journal");
                activeDestination = Integer.parseInt(fields[2]); activeToken = fields[3];
                blocked = !fields[1].equals("COMMITTED") && !fields[1].equals("FAILED");
            }
            if (fs.stagingExists()) { blocked = true; poisoned = true; }
        }
        catch (Exception failure) { poisoned = true; blocked = true; }
    }
    public synchronized boolean blocked() { return blocked || poisoned; }
    public synchronized int destination() { return activeToken == null ? -1 : activeDestination; }
    /** Only a verified current map/port may reconcile a pending operation; never replays modem work. */
    public synchronized void reconcile() throws IOException {
        if (blocked && !poisoned && activeToken != null) persist("COMMITTED", activeDestination, activeToken);
    }
    /** User-authorized engineering abort: clears pending, never claims COMMITTED.
     * Current original-route readback is not proof against later external/modem change.
     */
    public synchronized void abort(int destination, String token) throws IOException {
        if (poisoned || activeToken == null || !activeToken.equals(token)
                || activeDestination != destination) throw new IOException("abort session mismatch");
        try { fs.clearJournal(); fs.syncDirectory(); }
        catch (IOException failure) { poisoned = true; throw failure; }
        activeToken = null; activeDestination = -1; blocked = false;
    }
    public synchronized void persist(String phase, int destination, String token) throws IOException {
        if (poisoned) throw new IOException("intent unknown");
        if (!phase.matches("PREPARED|SUBMITTED|READBACK_PENDING|AMBIGUOUS|COMMITTED|FAILED")) throw new IOException("phase");
        if (destination != 0 && destination != 1 || !token.matches("[0-9a-f]{64}")) throw new IOException("intent fields");
        if (phase.equals("PREPARED")) {
            if (blocked) throw new IOException("pending intent preserved");
            activeToken = token; activeDestination = destination;
        } else if (!token.equals(activeToken) || destination != activeDestination) {
            throw new IOException("not this session's prepared intent");
        }
        byte[] bytes = ("N3-ENGINEERING-INTENT-V2\n" + phase + "\n" + destination + "\n" + token + "\n").getBytes(StandardCharsets.UTF_8);
        blocked = true;
        try { fs.createStagingAndSync(bytes); fs.renameStaging(); fs.syncDirectory(); }
        catch (IOException failure) { poisoned = true; throw failure; }
        blocked = !phase.equals("COMMITTED") && !phase.equals("FAILED");
    }
    @Override public void close() throws Exception { owner.close(); }
}
