package org.sourceowned.n3.runtime.v2;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/** Two-step one-use UI confirmation bound to UID/destination/baseline, not arbitrary app evidence. */
public final class ConsentTokens {
    public static final class Request {
        public final int uid, destination;
        public final String baseline;
        public final long issued;
        Request(int uid, int destination, String baseline, long issued) {
            this.uid = uid; this.destination = destination; this.baseline = baseline; this.issued = issued;
        }
    }
    private final Map<String, Request> pending = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    public synchronized String issue(int uid, int destination, String baseline, long now) {
        if (destination != 0 && destination != 1) throw new IllegalArgumentException("destination");
        pending.entrySet().removeIf(entry -> now - entry.getValue().issued < 0 || now - entry.getValue().issued >= 30_000_000_000L);
        if (pending.size() >= 16) throw new IllegalStateException("consent slots full");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte value : bytes) token.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        pending.put(token.toString(), new Request(uid, destination, baseline, now));
        return token.toString();
    }
    public synchronized Request consume(String token, int uid, long now) {
        if (token == null || !token.matches("[0-9a-f]{64}")) return null;
        Request request = pending.get(token);
        if (request == null || request.uid != uid) return null;
        pending.remove(token); // consumed even if later map/auth/storage/admission fails
        long age = now - request.issued;
        return age >= 0 && age < 30_000_000_000L ? request : null;
    }
}
