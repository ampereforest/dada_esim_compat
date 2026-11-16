package org.sourceowned.n3.runtime.v2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.sourceowned.n3.runtime.v2.core.Contracts;
import org.sourceowned.n3.runtime.v2.core.Model;

/** Identity comes from Binder, facts from PackageManager, never a parcel or supplied UID. */
public final class CallerPolicy implements Contracts.Auth {
    public static final String PACKAGE = "im.angry.openeuicc";
    public static final String CERT_SHA256 = "879b92491d4b8623227a06e6f4dc63bbd298cd3ad91b3a1ca25d849da24bb3c0";
    public interface Identity { int callingUid(); }
    public interface Packages { Facts inspect(int uid) throws Exception; }
    public static final class Facts {
        public final int uid;
        public final String packageName, certificate, sourcePath;
        public final boolean soleUid, privileged, enabled, permissions;
        public Facts(int uid, String packageName, String certificate, String sourcePath,
                     boolean soleUid, boolean privileged, boolean enabled, boolean permissions) {
            this.uid = uid; this.packageName = packageName; this.certificate = certificate;
            this.sourcePath = sourcePath; this.soleUid = soleUid; this.privileged = privileged;
            this.enabled = enabled; this.permissions = permissions;
        }
    }
    private final Identity identity;
    private final Packages packages;
    private final Model.Digest board;
    private final ThreadLocal<Invocation> active = new ThreadLocal<>();
    private static final class Invocation {
        final int uid;
        final Contracts.Caller caller;
        Invocation(int uid, Contracts.Caller caller) { this.uid = uid; this.caller = caller; }
    }
    public CallerPolicy(Identity identity, Packages packages, Model.Digest board) {
        this.identity = Objects.requireNonNull(identity);
        this.packages = Objects.requireNonNull(packages);
        this.board = Objects.requireNonNull(board);
    }
    /** Call on the Binder thread BEFORE clearing identity; close before leaving the transaction. */
    public InvocationScope enter() throws Contracts.AuthFailure {
        if (active.get() != null) throw new Contracts.AuthFailure();
        int uid = identity.callingUid();
        Model.Digest principal = authenticate(uid);
        Contracts.Caller caller = new Contracts.Caller(principal, 0);
        active.set(new Invocation(uid, caller));
        return new InvocationScope(caller);
    }
    public final class InvocationScope implements AutoCloseable {
        public final Contracts.Caller caller;
        private InvocationScope(Contracts.Caller caller) { this.caller = caller; }
        @Override public void close() { active.remove(); }
    }
    public boolean revalidate() {
        Invocation invocation = active.get();
        if (invocation == null || identity.callingUid() != invocation.uid) return false;
        try { return invocation.caller.principal.equals(authenticate(invocation.uid)); }
        catch (Contracts.AuthFailure denied) { return false; }
    }
    private Model.Digest authenticate(int uid) throws Contracts.AuthFailure {
        // Primary-user application only. root/system/shell are not product-provider identities.
        if (uid < 10000 || uid >= 100000) throw new Contracts.AuthFailure();
        try {
            Facts facts = packages.inspect(uid);
            if (facts == null || facts.uid != uid || !PACKAGE.equals(facts.packageName)
                    || !CERT_SHA256.equals(facts.certificate) || !facts.soleUid || !facts.privileged
                    || !facts.enabled || !facts.permissions || facts.sourcePath == null
                    || !facts.sourcePath.startsWith("/product/priv-app/")
                    || facts.sourcePath.contains("/../")) throw new Contracts.AuthFailure();
            return Model.Digest.ofSha256(Model.DigestKind.PRINCIPAL, MessageDigest.getInstance("SHA-256")
                    .digest((PACKAGE + "\n" + CERT_SHA256 + "\n" + uid).getBytes(StandardCharsets.UTF_8)));
        } catch (Contracts.AuthFailure denied) { throw denied; }
        catch (Exception unavailable) { throw new Contracts.AuthFailure(); }
    }
    @Override public Contracts.Permit authorize(Contracts.AccessRequest request) throws Contracts.AuthFailure {
        Invocation invocation = active.get();
        // Legacy core permits remain closed: engineering selection uses its separate tested
        // two-step admission path, never fabricates a core Permit/generation or LPA lease.
        if (!revalidate() || invocation == null || request.caller != invocation.caller
                || !board.equals(request.board) || request.caller.user != 0
                || !request.operationOwner.equals(request.caller.principal)
                || (request.scope != Contracts.Scope.OBSERVE
                    && request.scope != Contracts.Scope.RECONCILE_PENDING)) throw new Contracts.AuthFailure();
        return new Contracts.Permit(request, 30_000_000_000L);
    }
    @Override public boolean remainsAuthorized(Contracts.Permit permit, long nowNanos) {
        Invocation invocation = active.get();
        return invocation != null && permit != null && permit.request.caller == invocation.caller
                && board.equals(permit.request.board) && !permit.validity.expired(nowNanos) && revalidate()
                && permit.request.operationOwner.equals(invocation.caller.principal)
                && (permit.request.scope == Contracts.Scope.OBSERVE
                    || permit.request.scope == Contracts.Scope.RECONCILE_PENDING);
    }
}
