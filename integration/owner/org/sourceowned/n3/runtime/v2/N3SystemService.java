package org.sourceowned.n3.runtime.v2;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import com.android.server.SystemService;

/** Genuine system_server lifecycle owner; never publishes a servicemanager name. */
public final class N3SystemService extends SystemService {
    private Handler worker;
    private RoutingOwnerBinder owner;
    private boolean started;
    public N3SystemService(Context context) { super(context); }
    @Override public void onStart() {
        // Deliberately no publishBinderService, root bootstrap or routing operation.
    }
    @Override public void onBootPhase(int phase) {
        if (phase < PHASE_ACTIVITY_MANAGER_READY || started) return;
        started = true;
        HandlerThread thread = new HandlerThread("N3OwnerLifecycle");
        thread.start(); worker = new Handler(thread.getLooper());
        worker.post(new Runnable() {
            @Override public void run() {
                try {
                    if (android.os.Process.myUid() != 1000) throw new SecurityException("system UID required");
                    String context = (String) Class.forName("android.os.SELinux")
                            .getMethod("getContext").invoke(null);
                    if (!"u:r:system_server:s0".equals(context)) throw new SecurityException("system_server required");
                    if (owner == null) {
                        owner = RomOwnerFactory.create(getContext());
                        final Runnable poll = this;
                        owner.setOnProviderDied(() -> {
                            worker.removeCallbacks(poll);
                            worker.postDelayed(poll, 500);
                        });
                    }
                    if (!owner.providerConnected()) {
                        Intent offer = new Intent().setComponent(new ComponentName(CallerPolicy.PACKAGE,
                                CallerPolicy.PACKAGE + ".service.N3EndpointReceiver"));
                        offer.addFlags(0x01000000 | 0x10000000);
                        Bundle binders = new Bundle(); binders.putBinder("n3_offer", owner);
                        offer.putExtras(binders);
                        getContext().sendBroadcast(offer);
                    }
                } catch (Exception unavailable) {
                    android.util.Log.e("N3OwnerLifecycle", "Owner unavailable; no route replay", unavailable);
                }
                long delayMs = (owner != null && owner.providerConnected()) ? 15000L : 2000L;
                worker.removeCallbacks(this);
                worker.postDelayed(this, delayMs);
            }
        });
    }
}
