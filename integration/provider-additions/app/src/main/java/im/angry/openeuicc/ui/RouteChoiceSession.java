package im.angry.openeuicc.ui;

/** No saved-state/Intent token. UI state is disposable; N3 owns token age/UID/revalidation. */
public final class RouteChoiceSession {
    private long epoch;
    private String token;
    private int destination = -1;
    public long invalidate() { token = null; destination = -1; return ++epoch; }
    public boolean accept(long requestEpoch, int requested, int reported, String status, String value) {
        if (requestEpoch != epoch || requested != reported || (requested != 0 && requested != 1)
                || !"CONFIRM_EXPLICIT_ROUTE_IN_FOREGROUND_UI".equals(status)
                || value == null || value.isEmpty() || value.length() > 256) return false;
        destination = requested; token = value; return true;
    }
    public int destination() { return destination; }
    public String consume() { String value = token; invalidate(); return value; }
    public boolean matches(long value) { return value == epoch; }
    public boolean ready() { return token != null; }
}
