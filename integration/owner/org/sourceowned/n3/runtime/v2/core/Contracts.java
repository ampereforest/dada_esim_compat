package org.sourceowned.n3.runtime.v2.core;

import java.util.Objects;

import static org.sourceowned.n3.runtime.v2.core.Model.*;

/** Injectable boundaries. None of these interfaces is a discovered or deployed Android API. */
public final class Contracts {
    private Contracts() { }

    public interface MonotonicClock { long nowNanos(); }
    public interface OperationIds { OperationId next(); }

    /** Local windows are never serialized or reused across process recreation. */
    public static final class Window {
        public static final long MAX_DURATION = Long.MAX_VALUE / 4;
        public final long startedAtNanos;
        public final long durationNanos;
        public Window(long startedAtNanos, long durationNanos) {
            if (durationNanos <= 0 || durationNanos > MAX_DURATION) throw new IllegalArgumentException("invalid local duration");
            this.startedAtNanos = startedAtNanos;
            this.durationNanos = durationNanos;
        }
        public boolean expired(long now) {
            long elapsed = now - startedAtNanos;
            return elapsed < 0 || elapsed >= durationNanos;
        }
    }

    /** The transport owner must derive this from an authenticated live caller, not caller-supplied UID claims. */
    public static final class Caller {
        public final Digest principal;
        public final int user;
        public Caller(Digest principal, int user) {
            this.principal = Objects.requireNonNull(principal, "principal").requireKind(DigestKind.PRINCIPAL);
            if (user < 0) throw new IllegalArgumentException("negative user");
            this.user = user;
        }
        @Override public String toString() { return "<authenticated caller context>"; }
    }

    public enum Scope { OBSERVE, EXPLICIT_ROUTE_SELECTION, LPA_MUTATION, RECONCILE_PENDING }
    public static final class AccessRequest {
        public final Caller caller;
        public final Scope scope;
        public final OperationId operation;
        public final OperationId localDomain;
        public final Digest board;
        public final CardPortContext context;
        public final Digest operationOwner;
        public final boolean explicitUserSelection;
        public final long issuedAtNanos;
        public AccessRequest(Caller caller, Scope scope, OperationId operation, OperationId localDomain,
                             Digest board, CardPortContext context, Digest operationOwner,
                             boolean explicitUserSelection, long issuedAtNanos) {
            this.caller = Objects.requireNonNull(caller, "caller");
            this.scope = Objects.requireNonNull(scope, "scope");
            this.operation = Objects.requireNonNull(operation, "operation");
            this.localDomain = Objects.requireNonNull(localDomain, "local domain");
            this.board = Objects.requireNonNull(board, "board").requireKind(DigestKind.BOARD);
            this.context = context;
            this.operationOwner = Objects.requireNonNull(operationOwner, "owner").requireKind(DigestKind.PRINCIPAL);
            this.explicitUserSelection = explicitUserSelection;
            this.issuedAtNanos = issuedAtNanos;
        }
    }

    public static final class Permit {
        public final AccessRequest request;
        public final Window validity;
        public Permit(AccessRequest request, long durationNanos) {
            this.request = Objects.requireNonNull(request, "request");
            validity = new Window(request.issuedAtNanos, durationNanos);
        }
    }

    /**
     * Must authenticate the original caller/user/board/current consent/policy and revalidate
     * revocation/provider changes. Caller constructors and saved permits/capabilities are not
     * live authorization. The core checks this policy freshly before every private view return,
     * including failure paths; integrations must not substitute claimed UIDs/digests/booleans.
     * Implementations may block; local deadline checks are not safe cancellation guarantees.
     */
    public interface Auth {
        Permit authorize(AccessRequest request) throws AuthFailure;
        boolean remainsAuthorized(Permit permit, long nowNanos) throws AuthFailure;
    }
    public static final class AuthFailure extends Exception {
        private static final long serialVersionUID = 1L;
        public AuthFailure() { super("authorization unavailable or denied"); }
    }
    public static final class DenyAllAuth implements Auth {
        @Override public Permit authorize(AccessRequest request) throws AuthFailure { throw new AuthFailure(); }
        @Override public boolean remainsAuthorized(Permit permit, long nowNanos) { return false; }
    }

    public static final class ReadRequest {
        public final Ticket ticket;
        public final Digest board;
        public final Generation requiredGeneration;
        public final Journal.Pending pending;
        public final Window callWindow;
        public ReadRequest(Ticket ticket, Digest board, Generation requiredGeneration,
                           Journal.Pending pending, Window callWindow) {
            this.ticket = ticket;
            this.board = board;
            this.requiredGeneration = requiredGeneration;
            this.pending = pending;
            this.callWindow = callWindow;
        }
    }

    public static final class PlanRequest {
        public final OperationId operation;
        public final Selection selection;
        public final Observation before;
        public final Window callWindow;
        public PlanRequest(OperationId operation, Selection selection, Observation before, Window window) {
            this.operation = operation;
            this.selection = selection;
            this.before = before;
            this.callWindow = window;
        }
    }

    public static final class MutationRequest {
        public final OperationId operation;
        public final CardPortContext context;
        public final FullMap completeDestination;
        public final SimType normalizedDestinationType;
        public final Step step;
        public final int stepIndex;
        public final Window callWindow;
        public MutationRequest(OperationId operation, CardPortContext context, FullMap destination,
                               Step step, int index, Window window) {
            this.operation = operation;
            this.context = context;
            this.completeDestination = destination;
            this.normalizedDestinationType = SimType.forChoice(destination.choice);
            this.step = step;
            this.stepIndex = index;
            this.callWindow = window;
        }
    }

    public enum AckStatus { ACCEPTED, REJECTED_WITHOUT_MUTATION, AMBIGUOUS }
    /** Submission acknowledgement only. Never completion evidence. */
    public static final class Ack {
        public final OperationId operation;
        public final Generation generation;
        public final int stepIndex;
        public final AckStatus status;
        public Ack(OperationId operation, Generation generation, int stepIndex, AckStatus status) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.generation = Objects.requireNonNull(generation, "generation");
            this.stepIndex = stepIndex;
            this.status = Objects.requireNonNull(status, "status");
        }
    }

    public enum AdapterFailureKind { UNAVAILABLE, NOT_STARTED, AMBIGUOUS }
    public static final class AdapterFailure extends Exception {
        private static final long serialVersionUID = 1L;
        public final AdapterFailureKind kind;
        public AdapterFailure(AdapterFailureKind kind) {
            super("board adapter " + kind);
            this.kind = Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * observe/prepare are nonmutating, bounded GET/validation operations; submit is bounded admission,
     * not an indefinite synchronous modem wait. All receive local deadlines. Implementations must
     * establish ready authoritative evidence and owned-operation attribution, never fabricate it.
     * Plans/order/effects must be independently proven board contracts preserving logical0 globally.
     * Every route-affecting LPA/native path must use this core's durable lease/operation context.
     */
    public interface BoardAdapter {
        Observation observe(ReadRequest request) throws AdapterFailure;
        Plan prepare(PlanRequest request) throws AdapterFailure;
        Ack submit(MutationRequest request) throws AdapterFailure;
    }

    public enum LoadStatus { ABSENT, VALID, UNKNOWN }
    public enum StoreReason {
        NONE, CORRUPT, UNSUPPORTED_SCHEMA, BOARD_MISMATCH, IO, ORPHAN_STAGING,
        OWNERSHIP_BUSY, REVISION_CONFLICT, WRITE_FAILED, CLOSED
    }
    public static final class Load {
        public final LoadStatus status;
        public final Journal.Record record;
        public final StoreReason reason;
        private Load(LoadStatus status, Journal.Record record, StoreReason reason) {
            this.status = status; this.record = record; this.reason = reason;
        }
        public static Load absent() { return new Load(LoadStatus.ABSENT, null, StoreReason.NONE); }
        public static Load valid(Journal.Record record) { return new Load(LoadStatus.VALID, Objects.requireNonNull(record, "record"), StoreReason.NONE); }
        public static Load unknown(StoreReason reason) {
            if (reason == StoreReason.NONE) throw new IllegalArgumentException("unknown needs reason");
            return new Load(LoadStatus.UNKNOWN, null, reason);
        }
    }
    public static final class StoreFailure extends Exception {
        private static final long serialVersionUID = 1L;
        public final StoreReason reason;
        public StoreFailure(StoreReason reason) {
            super("journal " + reason);
            this.reason = Objects.requireNonNull(reason, "reason");
        }
    }
    public interface Store {
        /** Fail-fast exclusive owner, including multiple core instances/processes. */
        Session open() throws StoreFailure;
        interface Session extends AutoCloseable {
            Load load(Digest expectedBoard);
            /** Return only after atomic durable commit; compare-and-set revision. No weak fallback. */
            void write(Journal.Record record, long expectedRevision) throws StoreFailure;
            @Override void close() throws StoreFailure;
        }
    }
}
