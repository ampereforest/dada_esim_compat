package org.sourceowned.n3.runtime.v2;

public final class Protocol {
    private Protocol() { }
    public static final String SERVICE = "n3_routing_owner_v2";
    public static final String DESCRIPTOR = "org.sourceowned.n3.runtime.v2.IRoutingOwner";
    public static final int VERSION = 2;
    public static final int GET_STATUS = 1, SELECT = 2, BEGIN_LPA = 3, ROOT_DIAGNOSTICS = 4;
    public static final int CONFIRM_SELECTION = 5;
    public static final int PSIM2 = 0, ESIM = 1, MAX_PARCEL_BYTES = 4096;
    public static final String MUTATION_CLOSED = "UNAVAILABLE_LPA_BACKEND";
}
