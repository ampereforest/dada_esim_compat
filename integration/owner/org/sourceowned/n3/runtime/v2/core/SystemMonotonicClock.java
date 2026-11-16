package org.sourceowned.n3.runtime.v2.core;

/** System.nanoTime is local monotonic elapsed time, not a persisted expiry/time-of-day. */
public final class SystemMonotonicClock implements Contracts.MonotonicClock {
    @Override public long nowNanos() { return System.nanoTime(); }
}
