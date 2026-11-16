package org.sourceowned.n3.runtime.v2;

import java.util.concurrent.*;

/** One outstanding GET maximum; timeout is not Binder cancellation. Hung lane denies later GETs. */
public final class BoundedCalls {
    private final ExecutorService lane = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "n3-bounded-get"); thread.setDaemon(true); return thread;
    });
    private Future<?> outstanding;
    public <T> T get(Callable<T> getter, long timeoutMillis) throws Exception {
        if (timeoutMillis < 1 || timeoutMillis > 10000) throw new IllegalArgumentException("GET timeout");
        final Future<T> future;
        synchronized (this) {
            if (outstanding != null && !outstanding.isDone()) throw new IllegalStateException("GET lane still busy");
            future = lane.submit(getter); outstanding = future;
        }
        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
