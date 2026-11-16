package org.sourceowned.n3.runtime.v2;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.Bundle;
import java.lang.reflect.Array;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Run inside EXISTING com.android.phone UID1001; never instantiate a parallel RIL/HAL client. */
public final class PhoneRilGet {
    private final HandlerThread thread = new HandlerThread("n3-ril-get-callback");
    private final AtomicBoolean busy = new AtomicBoolean();
    private final BoundedCalls calls = new BoundedCalls();
    public PhoneRilGet() {
        if (Process.myUid() != 1001) throw new SecurityException("existing phone process only");
        thread.start();
    }
    public Bundle get() {
        Bundle out = new Bundle();
        out.putString("source", "EXISTING_PHONE_RILEXT_getSimTypeInfo_CALLBACK");
        if (Process.myUid() != 1001 || !busy.compareAndSet(false, true)) {
            out.putString("status", "PHONE_PROCESS_REQUIRED_OR_BUSY"); return out;
        }
        try { return calls.get(this::request, 3500); }
        catch (Exception blocked) {
            // Binder dispatch itself may be hung. Never open a parallel lane/retry.
            out.putString("status", "TIMED_OUT_OR_BUSY_PHONE_LANE"); return out;
        }
    }
    private Bundle request() {
        Bundle out = new Bundle();
        out.putString("source", "EXISTING_PHONE_RILEXT_getSimTypeInfo_CALLBACK");
        CompletableFuture<int[][]> done = new CompletableFuture<>();
        boolean invoked = false;
        Handler callback = new Handler(thread.getLooper()) {
            @Override public void handleMessage(Message message) {
                try {
                    Object async = message.obj;
                    Class<?> ar = Class.forName("android.os.AsyncResult");
                    if (!ar.isInstance(async) || ar.getField("exception").get(async) != null)
                        throw new IllegalStateException("RIL error");
                    Object rows = ar.getField("result").get(async);
                    if (rows == null || Array.getLength(rows) != 2) throw new IllegalStateException("two slot results required");
                    int[][] values = new int[2][2];
                    Class<?> info = Class.forName("com.oplus.telephony.SimTypeInfo");
                    for (int slot = 0; slot < 2; slot++) {
                        Object row = Array.get(rows, slot);
                        if (!info.isInstance(row)) throw new IllegalStateException("wrong RIL type namespace");
                        values[slot][0] = (Integer) info.getMethod("getCurrentSimType").invoke(row);
                        values[slot][1] = (Integer) info.getMethod("getSupportedSimType").invoke(row);
                    }
                    done.complete(values);
                } catch (Exception failure) { done.completeExceptionally(failure); }
            }
        };
        try {
            // Source-backed existing singleton and callback method; no raw AIDL transactions.
            Class<?> controller = Class.forName("com.oplus.internal.telephony.OplusTelephonyController");
            Object owner = controller.getMethod("getInstance").invoke(null);
            if (owner == null) throw new IllegalStateException("phone controller missing");
            Object ril = controller.getMethod("getRILExt", int.class).invoke(owner, 1);
            if (ril == null) throw new IllegalStateException("RIL missing");
            Message message = callback.obtainMessage(1);
            invoked = true;
            Class.forName("com.oplus.internal.telephony.CommandsInterfaceExt")
                    .getMethod("getSimTypeInfo", Message.class).invoke(ril, message);
            int[][] values = done.get(3000, TimeUnit.MILLISECONDS);
            out.putIntArray("oplus_current_types", new int[] {values[0][0], values[1][0]});
            out.putIntArray("oplus_supported_types", new int[] {values[0][1], values[1][1]});
            out.putString("status", "FRESH_CALLBACK_OPLUS_NAMESPACE");
            out.putBoolean("fresh_type_callback", true);
            // Still NOT Android hidden 1/2, current card generation, or complete routing evidence.
        } catch (TimeoutException timeout) {
            out.putString("status", "TIMED_OUT_PENDING_CALLBACK");
            // Keep busy until actual callback; no request retry or fake timeout cancellation.
            done.whenComplete((value, failure) -> busy.set(false));
            return out;
        } catch (Exception failure) {
            out.putString("status", "UNAVAILABLE_RIL_CALLBACK");
            if (invoked && !done.isDone()) {
                done.whenComplete((value, error) -> busy.set(false));
                return out;
            }
        }
        busy.set(false);
        return out;
    }
}
