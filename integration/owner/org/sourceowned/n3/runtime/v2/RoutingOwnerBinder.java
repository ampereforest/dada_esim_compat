package org.sourceowned.n3.runtime.v2;

import android.os.*;
import org.sourceowned.n3.runtime.v2.core.Contracts;
import org.sourceowned.n3.runtime.v2.core.Model;
import org.sourceowned.n3.runtime.v2.dada.DadaBoardAdapter;

/** Serialized route changes and provider LPA leases, with observed map/port completion. */
public final class RoutingOwnerBinder extends Binder {
    private final CallerPolicy auth;
    private final ForegroundPolicy foreground;
    private final ConsentTokens tokens = new ConsentTokens();
    private final RealReadback readback;
    private final ExperimentalIntent intent;
    private final boolean test;
    private final NativeRouteClient nativeRoute;
    private final EngineeringSelection selection = new EngineeringSelection();
    private final java.util.concurrent.atomic.AtomicBoolean confirming = new java.util.concurrent.atomic.AtomicBoolean();
    private final Object gate = new Object();
    private IBinder lpaOwner;
    private String lpaToken;
    private int lpaUid;
    private IBinder.DeathRecipient lpaDeath;
    private volatile IBinder providerEndpoint;
    private IBinder.DeathRecipient providerDeath;
    private volatile Runnable onProviderDied;
    public void setOnProviderDied(Runnable callback) { this.onProviderDied = callback; }
    public boolean providerConnected() {
        IBinder ep = providerEndpoint;
        return ep != null && ep.isBinderAlive();
    }
    public RoutingOwnerBinder(CallerPolicy auth, ForegroundPolicy foreground, RealReadback readback,
                              ExperimentalIntent intent, boolean test) {
        this.auth = auth; this.foreground = foreground; this.readback = readback; this.intent = intent; this.test = test;
        this.nativeRoute = new NativeRouteClient(test);
        attachInterface(null, Protocol.DESCRIPTOR);
    }
    private static void end(Parcel data) { if (data.dataAvail() != 0) throw new IllegalArgumentException("extra fields"); }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(Protocol.DESCRIPTOR); return true; }
        if (code < 1 || code > 7) return false;
        if (reply == null || (flags & FLAG_ONEWAY) != 0 || data.dataSize() > Protocol.MAX_PARCEL_BYTES)
            throw new SecurityException("bounded synchronous IPC required");
        data.enforceInterface(Protocol.DESCRIPTOR);
        if (data.readInt() != 2) throw new IllegalArgumentException("version");
        Bundle result;
        if (code == Protocol.ROOT_DIAGNOSTICS) {
            end(data);
            if (!test || Binder.getCallingUid() != 0) throw new SecurityException("root test only");
            result = read();
        } else {
            try (CallerPolicy.InvocationScope invocation = auth.enter()) {
                int uid = Binder.getCallingUid();
                if (code == 7) {
                    IBinder endpoint = data.readStrongBinder(); end(data);
                    if (endpoint == null) throw new SecurityException("missing handoff endpoint");
                    long identity = Binder.clearCallingIdentity();
                    Parcel send = Parcel.obtain(), response = Parcel.obtain();
                    try {
                        send.writeInterfaceToken("im.angry.openeuicc.IN3Endpoint"); send.writeStrongBinder(this);
                        if (!endpoint.transact(1, send, response, 0)) throw new RemoteException("handoff failed");
                        response.readException();
                        synchronized (gate) {
                            if (providerEndpoint != null && providerDeath != null) {
                                try { providerEndpoint.unlinkToDeath(providerDeath, 0); } catch (Exception ignored) { }
                            }
                            IBinder.DeathRecipient death = () -> {
                                boolean cleared = false;
                                synchronized (gate) {
                                    if (providerEndpoint == endpoint) {
                                        providerEndpoint = null;
                                        providerDeath = null;
                                        cleared = true;
                                    }
                                }
                                if (cleared) {
                                    android.util.Log.i("N3OwnerLifecycle", "Provider endpoint died; scheduling re-offer");
                                    Runnable cb = onProviderDied;
                                    if (cb != null) cb.run();
                                }
                            };
                            endpoint.linkToDeath(death, 0);
                            providerEndpoint = endpoint;
                            providerDeath = death;
                        }
                    } finally { send.recycle(); response.recycle(); Binder.restoreCallingIdentity(identity); }
                    result = new Bundle(); result.putString("status", "CONNECTED");
                } else if (code == Protocol.GET_STATUS) { end(data); result = read(); }
                else if (code == Protocol.BEGIN_LPA) {
                    IBinder lifetime = data.readStrongBinder(); end(data);
                    result = beginLpa(uid, lifetime);
                } else if (code == 6) {
                    String lease = data.readString(); end(data);
                    synchronized (gate) {
                        if (lpaOwner == null || uid != lpaUid || !lpaToken.equals(lease)) throw new SecurityException("foreign lease");
                        releaseLpa();
                    }
                    result = new Bundle(); result.putString("status", "LPA_RELEASED");
                } else {
                    if (!foreground.allowed(uid)) throw new SecurityException("foreground unlocked primary-user provider required");
                    if (code == Protocol.SELECT) {
                        int destination = data.readInt(); end(data);
                        if (destination != 0 && destination != 1) throw new IllegalArgumentException("destination");
                        Model.FullMap map = ownerMap();
                        result = new Bundle();
                        result.putString("consent_token", tokens.issue(uid, destination, map.toString(), SystemClock.elapsedRealtimeNanos()));
                        result.putString("status", "CONFIRM_EXPLICIT_ROUTE_IN_FOREGROUND_UI");
                        result.putInt("destination", destination);
                        result.putBoolean("existing_pending", intent.blocked());
                    } else {
                        String token = data.readString(); end(data);
                        ConsentTokens.Request request = tokens.consume(token, uid, SystemClock.elapsedRealtimeNanos());
                        if (request == null) throw new SecurityException("one-use confirmation expired or foreign");
                        boolean acquired;
                        synchronized (gate) { acquired = lpaOwner == null && confirming.compareAndSet(false, true); }
                        if (!acquired) {
                            result = new Bundle(); result.putString("status", "OWNER_BUSY_RECONFIRM_REQUIRED");
                        } else {
                            try { result = confirm(uid, token, request); }
                            finally { confirming.set(false); }
                        }
                    }
                }
                if (!auth.revalidate()) throw new SecurityException("provider changed");
            } catch (Contracts.AuthFailure denied) { throw new SecurityException("signed product provider required"); }
        }
        result.putInt("protocol", 2); result.putBoolean("lpa_leases_supported", true);
        reply.writeNoException(); reply.writeBundle(result); return true;
    }
    private Bundle read() {
        long identity = Binder.clearCallingIdentity();
        try {
            reconcile();
            Bundle result = readback.read(); result.putBoolean("durable_pending", intent.blocked());
            int destination = intent.destination();
            boolean matches = false;
            try { matches = destination >= 0 && !intent.blocked() && readback.routeMatches(destination); } catch (Exception ignored) { }
            result.putString("status", matches ? "COMMITTED" : "REAL_READBACK_SUBSET");
            result.putInt("destination", destination);
            result.putBoolean("lpa_busy", lpaOwner != null);
            return result;
        } finally { Binder.restoreCallingIdentity(identity); }
    }
    private void reconcile() {
        synchronized (gate) {
            if (confirming.get() || lpaOwner != null || !intent.blocked() || intent.destination() < 0) return;
            try { if (readback.routeStable(intent.destination())) intent.reconcile(); } catch (Exception ignored) { }
        }
    }
    private Bundle beginLpa(int uid, IBinder lifetime) throws RemoteException {
        Bundle result = new Bundle();
        synchronized (gate) {
            if (lifetime == null || !lifetime.isBinderAlive()) throw new SecurityException("lease lifetime required");
            if (confirming.get() || lpaOwner != null) { result.putString("status", "OWNER_BUSY_RECONFIRM_REQUIRED"); return result; }
            long identity = Binder.clearCallingIdentity();
            try {
                reconcile();
                if (intent.blocked() || !readback.routeMatches(1)) { result.putString("status", "ESIM_ROUTE_REQUIRED"); return result; }
            } catch (Exception missing) { result.putString("status", "GET_UNAVAILABLE_NO_ADMISSION"); return result; }
            finally { Binder.restoreCallingIdentity(identity); }
            lpaUid = uid; lpaOwner = lifetime; lpaToken = java.util.UUID.randomUUID().toString();
            lpaDeath = () -> { synchronized (gate) { if (lpaOwner == lifetime) releaseLpa(); } };
            try { lifetime.linkToDeath(lpaDeath, 0); } catch (RemoteException dead) { releaseLpa(); throw dead; }
            android.util.Log.i("N3Lease", "LPA_GRANTED uid=" + uid);
            result.putString("status", "LPA_GRANTED"); result.putString("lease_token", lpaToken);
        }
        return result;
    }
    private void releaseLpa() {
        if (lpaOwner != null) android.util.Log.i("N3Lease", "LPA_RELEASED uid=" + lpaUid);
        if (lpaOwner != null) lpaOwner.unlinkToDeath(lpaDeath, 0);
        lpaOwner = null; lpaToken = null; lpaDeath = null;
    }
    private Model.FullMap ownerMap() {
        long identity = Binder.clearCallingIdentity();
        try { return readback.map(); }
        catch (Exception unavailable) { throw new IllegalStateException("complete map unavailable"); }
        finally { Binder.restoreCallingIdentity(identity); }
    }
    private Bundle confirm(int uid, String token, ConsentTokens.Request request) {
        EngineeringSelection.Result admission = selection.confirm(request, token, new EngineeringSelection.Owner() {
            public Model.FullMap map() { return ownerMap(); }
            public Model.FullMap mapAfterModem() throws Exception {
                long identity = Binder.clearCallingIdentity();
                try { return readback.mapAfterModem(); }
                finally { Binder.restoreCallingIdentity(identity); }
            }
            public boolean pending() { return intent.blocked(); }
            public boolean authorized() {
                long age = SystemClock.elapsedRealtimeNanos() - request.issued;
                if (age < 0 || age >= 30_000_000_000L) { android.util.Log.w("N3Admission", "consent age ns=" + age); return false; }
                if (!auth.revalidate()) { android.util.Log.w("N3Admission", "provider revalidation failed"); return false; }
                if (!foreground.allowed(uid)) { android.util.Log.w("N3Admission", "foreground revalidation failed"); return false; }
                return true;
            }
            public boolean safe() { return readback.safeToRoute(); }
            public void persist(String phase, int destination, String value) throws Exception { intent.persist(phase, destination, value); }
            public DadaBoardAdapter.Submission modem(int destination) {
                long identity = Binder.clearCallingIdentity();
                try { return nativeRoute.submit(destination); }
                finally { Binder.restoreCallingIdentity(identity); }
            }
            public DadaBoardAdapter.Submission submitMap(Model.FullMap destination) {
                long identity = Binder.clearCallingIdentity();
                try { return readback.submitMap(destination); }
                finally { Binder.restoreCallingIdentity(identity); }
            }
            public boolean verify(int destination) {
                long identity = Binder.clearCallingIdentity();
                try { return readback.awaitRoute(destination); }
                finally { Binder.restoreCallingIdentity(identity); }
            }
            public boolean originalRouteStable(Model.FullMap original) throws Exception {
                long identity = Binder.clearCallingIdentity();
                try {
                    for (int choice = 0; choice <= 1; choice++) {
                        Model.FullMap expected = Model.FullMap.forChoice(choice == 1 ? Model.Choice.ESIM : Model.Choice.PHYSICAL_SIM_2);
                        if (expected.equals(original)) return readback.routeStable(choice);
                    }
                    return false;
                } finally { Binder.restoreCallingIdentity(identity); }
            }
            public void abort(int destination, String value) throws Exception { intent.abort(destination, value); }
        });
        Bundle result = new Bundle();
        result.putString("status", admission.status); result.putBoolean("admitted", admission.admitted);
        result.putBoolean("durable_pending", intent.blocked());
        if (admission.modemAck != null) result.putString("modem_ack", admission.modemAck);
        if (admission.mapAck != null) result.putString("map_ack", admission.mapAck);
        if (admission.readback) result.putBundle("readback", read());
        return result;
    }
}
