package im.angry.openeuicc.ui;

/** Additional UI preflight veto only. Not an owner lease, quiescence proof or race fence. */
public final class RouteChoiceSafety {
    public interface Probe {
        boolean callIdle() throws Exception;
        boolean slotSafe(int slot) throws Exception;
        boolean noNativeVsim() throws Exception;
        boolean airplaneOff() throws Exception;
    }
    private RouteChoiceSafety() {}
    public static boolean allowed(Probe probe) {
        try {
            return probe != null && probe.callIdle() && probe.slotSafe(0) && probe.slotSafe(1)
                    && probe.noNativeVsim() && probe.airplaneOff();
        } catch (Exception | LinkageError unavailable) { return false; }
    }
}
