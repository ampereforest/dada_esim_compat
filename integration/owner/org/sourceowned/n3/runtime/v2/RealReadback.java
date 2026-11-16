package org.sourceowned.n3.runtime.v2;

import android.os.Bundle;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.os.Binder;
import org.sourceowned.n3.runtime.v2.core.Contracts;
import org.sourceowned.n3.runtime.v2.core.Model;
import org.sourceowned.n3.runtime.v2.dada.AndroidDadaMapping;
import org.sourceowned.n3.runtime.v2.dada.DadaBoardAdapter;

/** Useful framework GET subset + honest Oplus route response, no synthetic core generation. */
public final class RealReadback {
    private final AndroidDadaMapping mapping;
    private final FrameworkDiagnostics framework;
    private final BoundedCalls calls = new BoundedCalls();
    private final TelephonyManager tm;
    private final Context context;
    public RealReadback(Context context, Contracts.MonotonicClock clock) {
        this.context = context;
        tm = context.getSystemService(TelephonyManager.class);
        mapping = new AndroidDadaMapping(tm, clock);
        framework = new FrameworkDiagnostics(tm, mapping);
    }
    public Bundle read() {
        Bundle out;
        try { out = calls.get(framework::read, 3000); }
        catch (Exception unavailable) { out = new Bundle(); out.putString("framework_status", "UNAVAILABLE_OR_TIMED_OUT"); }
        // Route completion uses existing telephony APIs, not a custom ServiceManager endpoint.
        try {
            Bundle slots = calls.get(() -> {
                Bundle facts = new Bundle();
                Object[] values = (Object[]) TelephonyManager.class.getMethod("getUiccSlotsInfo").invoke(tm);
                if (values == null || values.length != 2) throw new IllegalStateException("two physical slots required");
                for (int i = 0; i < 2; i++) {
                    Object slot = values[i];
                    if (slot == null) throw new IllegalStateException("slot absent from GET");
                    Class<?> type = Class.forName("android.telephony.UiccSlotInfo");
                    facts.putInt("slot" + i + "_card_state", (Integer) type.getMethod("getCardStateInfo").invoke(slot));
                    facts.putBoolean("slot" + i + "_euicc", (Boolean) type.getMethod("getIsEuicc").invoke(slot));
                    boolean active = false;
                    for (android.telephony.UiccPortInfo port : (java.util.Collection<android.telephony.UiccPortInfo>)
                            type.getMethod("getPorts").invoke(slot)) active |= port.isActive();
                    facts.putBoolean("slot" + i + "_active", active);
                }
                facts.putInt("sim_state0", tm.getSimState(0)); facts.putInt("sim_state1", tm.getSimState(1));
                facts.putString("status", "FRAMEWORK_SLOT_GET_NOT_CARD_GENERATION");
                return facts;
            }, 3000);
            out.putBundle("framework_slots", slots);
        } catch (Exception missing) { out.putString("framework_slots_status", "UNAVAILABLE_OR_TIMED_OUT"); android.util.Log.w("N3Slots", "Slot GET failed", missing); }
        out.putString("authority", "REAL_FRAMEWORK_AND_OPLUS_SUBSET_NO_GLOBAL_GENERATION");
        return out;
    }
    public Model.FullMap map() throws Exception { return calls.get(mapping::read, 3000); }
    public Model.FullMap mapAfterModem() throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 6000;
        Model.FullMap previous = null;
        int consecutive = 0;
        do {
            try {
                Model.FullMap current = map();
                consecutive = current.equals(previous) ? consecutive + 1 : 1;
                previous = current;
                if (consecutive >= 3) return current;
            } catch (Exception transientMap) { previous = null; consecutive = 0; }
            android.os.SystemClock.sleep(500);
        } while (android.os.SystemClock.elapsedRealtime() < deadline);
        throw new java.io.IOException("complete post-modem map unavailable");
    }
    public boolean routeStable(int destination) throws Exception {
        for (int i = 0; i < 3; i++) {
            if (!routeMatches(destination)) return false;
            if (i < 2) android.os.SystemClock.sleep(500);
        }
        return true;
    }
    public boolean routeMatches(int destination) throws Exception {
        return calls.get(() -> {
            Model.FullMap expected = Model.FullMap.forChoice(destination == 1 ? Model.Choice.ESIM : Model.Choice.PHYSICAL_SIM_2);
            if (!mapping.read().equals(expected)) return false;
            boolean found = false;
            for (android.telephony.UiccCardInfo card : tm.getUiccCardsInfo()) {
                for (android.telephony.UiccPortInfo port : card.getPorts()) {
                    if (port.isActive() && port.getLogicalSlotIndex() == 1) {
                        if (found || card.getPhysicalSlotIndex() != 1 || port.getPortIndex() != destination
                                || card.isEuicc() != (destination == 1)) return false;
                        found = true;
                    }
                }
            }
            return found && mapping.read().equals(expected);
        }, 3000);
    }
    public boolean awaitRoute(int destination) {
        long deadline = android.os.SystemClock.elapsedRealtime() + 45000;
        int consecutive = 0;
        do {
            try { consecutive = routeMatches(destination) ? consecutive + 1 : 0; }
            catch (Exception transientState) { consecutive = 0; }
            if (consecutive >= 3) return true;
            android.os.SystemClock.sleep(500);
        } while (android.os.SystemClock.elapsedRealtime() < deadline);
        return false;
    }
    public AndroidDadaMapping mapping() { return mapping; }
    public DadaBoardAdapter.Submission submitMap(Model.FullMap destination) {
        Contracts.Window window = new Contracts.Window(android.os.SystemClock.elapsedRealtimeNanos(), 3_000_000_000L);
        try {
            return calls.get(() -> mapping.submit(destination, window), 3000);
        } catch (Exception uncertain) { return DadaBoardAdapter.Submission.AMBIGUOUS; }
    }
    public boolean safeToRoute() {
        long identity = Binder.clearCallingIdentity();
        try {
            return calls.get(() -> {
                int airplane = airplaneMode();
                if (airplane != 0) { android.util.Log.w("N3Safety", "airplane=" + airplane); return false; }
                int call = tm.getCallState();
                if (call != TelephonyManager.CALL_STATE_IDLE) { android.util.Log.w("N3Safety", "call=" + call); return false; }
                for (int slot = 0; slot < 2; slot++) {
                    int state = tm.getSimState(slot);
                    if (state == TelephonyManager.SIM_STATE_UNKNOWN || state == TelephonyManager.SIM_STATE_PIN_REQUIRED
                            || state == TelephonyManager.SIM_STATE_PUK_REQUIRED || state == TelephonyManager.SIM_STATE_NETWORK_LOCKED)
                        { android.util.Log.w("N3Safety", "slot=" + slot + " sim_state=" + state); return false; }
                }
                return true;
            }, 3000);
        } catch (Exception missing) { android.util.Log.e("N3Safety", "Safety GET failed; no admission", missing); return false; }
        finally { Binder.restoreCallingIdentity(identity); }
    }
    private int airplaneMode() throws Exception {
        int value = android.provider.Settings.Global.getInt(context.getContentResolver(),
                android.provider.Settings.Global.AIRPLANE_MODE_ON);
        if (value != 0 && value != 1) throw new java.io.IOException("airplane GET unknown");
        return value;
    }
}
