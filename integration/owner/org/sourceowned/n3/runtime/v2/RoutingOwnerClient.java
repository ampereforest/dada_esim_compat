package org.sourceowned.n3.runtime.v2;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** Explicit authenticated Binder handoff; no custom ServiceManager discovery. */
public final class RoutingOwnerClient {
    private static volatile IBinder installed;
    public static synchronized void install(IBinder owner) throws RemoteException {
        owner.linkToDeath(() -> { synchronized (RoutingOwnerClient.class) { if (installed == owner) installed = null; } }, 0);
        installed = owner;
    }
    private final IBinder binder;
    public RoutingOwnerClient(IBinder binder) {
        if (binder == null) throw new IllegalArgumentException("owner unavailable");
        this.binder = binder;
    }
    /** Only the authenticated endpoint receiver installs this Binder. */
    public static RoutingOwnerClient connect() throws ReflectiveOperationException {
        return new RoutingOwnerClient(installed);
    }
    public Bundle status() throws RemoteException { return call(Protocol.GET_STATUS, -1); }
    public Bundle select(boolean esim) throws RemoteException { return call(Protocol.SELECT, esim ? Protocol.ESIM : Protocol.PSIM2); }
    public Bundle beginLpa(IBinder lifetime) throws RemoteException { return lease(Protocol.BEGIN_LPA, lifetime, null); }
    public Bundle endLpa(String token) throws RemoteException { return lease(6, null, token); }
    private Bundle lease(int code, IBinder lifetime, String token) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(Protocol.DESCRIPTOR); data.writeInt(2);
            if (code == Protocol.BEGIN_LPA) data.writeStrongBinder(lifetime); else data.writeString(token);
            if (!binder.transact(code, data, reply, 0)) throw new RemoteException("unsupported lease");
            reply.readException();
            Bundle result = reply.readBundle(RoutingOwnerClient.class.getClassLoader());
            if (result == null || result.getInt("protocol", -1) != 2) throw new RemoteException("invalid lease response");
            return result;
        } finally { data.recycle(); reply.recycle(); }
    }
    /** Invoke only after explicit user confirmation of the issue-token response in foreground UI. */
    public Bundle confirm(String token) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(Protocol.DESCRIPTOR); data.writeInt(2); data.writeString(token);
            if (!binder.transact(Protocol.CONFIRM_SELECTION, data, reply, 0)) throw new RemoteException("unsupported confirm");
            reply.readException();
            Bundle result = reply.readBundle(RoutingOwnerClient.class.getClassLoader());
            if (result == null || result.getInt("protocol", -1) != 2) throw new RemoteException("invalid response");
            return result;
        } finally { data.recycle(); reply.recycle(); }
    }
    private Bundle call(int code, int destination) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(Protocol.DESCRIPTOR);
            data.writeInt(Protocol.VERSION);
            if (code == Protocol.SELECT) data.writeInt(destination);
            if (!binder.transact(code, data, reply, 0)) throw new RemoteException("unsupported owner protocol");
            reply.readException();
            Bundle result = reply.readBundle(RoutingOwnerClient.class.getClassLoader());
            if (result == null || result.getInt("protocol", -1) != Protocol.VERSION)
                throw new RemoteException("invalid owner response");
            return result;
        } finally { data.recycle(); reply.recycle(); }
    }
}
