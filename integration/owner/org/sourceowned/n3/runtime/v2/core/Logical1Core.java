package org.sourceowned.n3.runtime.v2.core;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static org.sourceowned.n3.runtime.v2.core.Contracts.*;
import static org.sourceowned.n3.runtime.v2.core.Journal.*;
import static org.sourceowned.n3.runtime.v2.core.Model.*;

/**
 * Serialized preservation-first logical1 owner. There is no automatic restoration, replay,
 * compensation, profile enable/disable/delete, Android API, or board transport implementation.
 */
public final class Logical1Core implements AutoCloseable {
    public enum State {
        UNRECORDED, VERIFIED, SELECTION_REQUIRED, OBSERVATION_UNAVAILABLE,
        PENDING_ROUTE, PENDING_LPA, PARTIAL_OR_UNKNOWN, TIMED_OUT, STORE_UNKNOWN, CLOSED
    }
    public enum Outcome {
        ADOPTED, VERIFIED_BOOT, VERIFIED_NO_OP, COMMITTED, EXPLICIT_SELECTION_REQUIRED,
        OBSERVATION_UNAVAILABLE, UNAUTHORIZED, STALE_CONTEXT, REJECTED, BUSY,
        PENDING, PARTIAL_OR_UNKNOWN, TIMED_OUT, STORE_UNKNOWN, IGNORED_HINT,
        LPA_LEASE_GRANTED, LPA_LEASE_VALID, LPA_RECONCILED, CLOSED
    }
    public enum Reason {
        NONE, AUTHORIZATION, BOARD, GENERATION, CARD_PORT, READBACK, CALL_TIMEOUT,
        EXPLICIT_CHOICE, UNSUPPORTED_PLAN, TRANSPORT, OPERATION_EVIDENCE,
        PROFILE_EVIDENCE, OBSERVATION_BUDGET, OPERATION_ID, LEASE, JOURNAL
    }

    public static final class Config {
        public final long operationTimeoutNanos;
        public final long adapterCallTimeoutNanos;
        public final int reobservationBudget;
        public Config(long operationTimeoutNanos, long adapterCallTimeoutNanos, int reobservationBudget) {
            new Window(0, operationTimeoutNanos);
            new Window(0, adapterCallTimeoutNanos);
            if (adapterCallTimeoutNanos > operationTimeoutNanos || reobservationBudget < 1
                || reobservationBudget > MAX_REOBSERVATIONS) throw new IllegalArgumentException("invalid bounded configuration");
            this.operationTimeoutNanos = operationTimeoutNanos;
            this.adapterCallTimeoutNanos = adapterCallTimeoutNanos;
            this.reobservationBudget = reobservationBudget;
        }
    }

    /** Last-confirmed desired route, pending intent, observations, and enabled identity remain separate. */
    public static final class View {
        public final State state;
        public final Confirmed lastConfirmed;
        public final Choice desiredChoice;
        public final Pending pending;
        public final Choice pendingChoice;
        /** observed is the last accepted snapshot; false means the latest read failed or is stale. */
        public final boolean observationCurrent;
        public final Observation observed;
        public final ProfileEvidence observedEnabledProfile;
        public final LoadStatus storeStatus;
        public final StoreReason storeReason;
        private View(State state, Journal.Record record, Observation observed, boolean current, LoadStatus status, StoreReason reason) {
            this.state = state;
            lastConfirmed = record == null ? null : record.confirmed;
            desiredChoice = lastConfirmed == null ? null : lastConfirmed.choice;
            pending = record == null ? null : record.pending;
            pendingChoice = pending == null ? null : pending.destination;
            observationCurrent = current;
            this.observed = observed;
            observedEnabledProfile = observed == null || !current ? null : observed.enabledProfile;
            storeStatus = status;
            storeReason = reason;
        }
        @Override public String toString() {
            return "View(" + state + ", desired=" + desiredChoice + ", pending=" + pendingChoice + ", identity redacted)";
        }
    }

    public static final class Result {
        public final Outcome outcome;
        public final Reason reason;
        /**
         * Every private view requires a fresh live policy check at return. Denied, gate-busy,
         * ignored and closed requests are bare. An authorized pending-conflict BUSY may carry
         * a view; BUSY itself is never a privacy exception or permission to mutate.
         */
        public final View view;
        public final LpaLease lease;
        private Result(Outcome outcome, Reason reason, View view, LpaLease lease) {
            this.outcome = outcome; this.reason = reason; this.view = view; this.lease = lease;
        }
        @Override public String toString() { return "Result(" + outcome + ", " + reason + ")"; }
    }

    public static final class OwnedLpaContext {
        public final OperationId operation;
        public final CardPortContext cardPort;
        public final Digest owner;
        private OwnedLpaContext(Pending pending) {
            operation = pending.operation; cardPort = pending.binding; owner = pending.owner;
        }
        @Override public String toString() { return "<owned LPA operation context>"; }
    }

    /** Unforgeable in-process capability; a saved pending record does not recreate this capability. */
    public static final class LpaLease {
        public final OwnedLpaContext context;
        private final OperationId localDomain;
        private final Window window;
        private final Permit permit;
        private boolean admitted;
        private LpaLease(Pending pending, OperationId domain, Window window, Permit permit) {
            context = new OwnedLpaContext(pending); localDomain = domain; this.window = window; this.permit = permit;
        }
        @Override public String toString() { return "<local LPA mutation lease>"; }
    }

    /**
     * Injection for one bounded admission of an explicit N1-owned backend operation, not a backend.
     * The backend must enforce this exact operation/card/port/generation and report its own primary
     * outcome. ACCEPTED is not profile success. No retry or admission after recreation is provided.
     */
    public interface LpaAdmission {
        AckStatus admit(OwnedLpaContext context, Window callWindow) throws AdapterFailure;
    }

    private final Digest board;
    private final Store.Session store;
    private final BoardAdapter adapter;
    private final Auth auth;
    private final MonotonicClock clock;
    private final OperationIds ids;
    private final Config config;
    private final ReentrantLock gate = new ReentrantLock();
    private final Set<OperationId> usedIds = new HashSet<OperationId>();
    private final OperationId localDomain;
    private Journal.Record record;
    private LoadStatus storeStatus;
    private StoreReason storeReason;
    private Observation lastObserved;
    private boolean observationCurrent;
    private long ticketNumber;
    private OperationId activeOperation;
    private Window activeWindow;
    private LpaLease activeLease;
    private boolean closed;

    public static Logical1Core open(Digest board, Store store, BoardAdapter adapter, Auth auth,
                                    MonotonicClock clock, OperationIds ids, Config config) throws StoreFailure {
        Objects.requireNonNull(board, "board").requireKind(DigestKind.BOARD);
        Objects.requireNonNull(adapter, "adapter"); Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(clock, "clock"); Objects.requireNonNull(ids, "ids"); Objects.requireNonNull(config, "config");
        Store.Session session = Objects.requireNonNull(store, "store").open();
        try { return new Logical1Core(board, session, adapter, auth, clock, ids, config); }
        catch (RuntimeException failure) {
            try { session.close(); } catch (StoreFailure ignored) { /* construction did not succeed */ }
            throw failure;
        }
    }

    private Logical1Core(Digest board, Store.Session store, BoardAdapter adapter, Auth auth,
                         MonotonicClock clock, OperationIds ids, Config config) {
        this.board = board; this.store = store; this.adapter = adapter; this.auth = auth;
        this.clock = clock; this.ids = ids; this.config = config;
        Load load;
        try { load = Objects.requireNonNull(store.load(board), "load"); }
        catch (RuntimeException failure) { load = Load.unknown(StoreReason.IO); }
        storeStatus = load.status; storeReason = load.reason;
        if (load.status == LoadStatus.VALID && (load.record.revision <= 0 || !board.equals(load.record.board))) {
            storeStatus = LoadStatus.UNKNOWN; storeReason = StoreReason.BOARD_MISMATCH;
        } else record = load.status == LoadStatus.VALID ? load.record : load.status == LoadStatus.ABSENT ? Journal.Record.absentBaseline(board) : null;
        localDomain = nextId();
    }

    /** Observation/adoption only. A mismatch never becomes a boot setter. */
    public Result verifyBoot(Caller caller) {
        if (caller == null) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if (!enter()) return bare(Outcome.BUSY, Reason.LEASE);
        Permit permit = null;
        try {
            if (closed) return bare(Outcome.CLOSED, Reason.NONE);
            if (!storeReady()) return bare(Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            if (record.pending != null) {
                permit = permit(caller, Scope.RECONCILE_PENDING, record.pending.operation,
                    record.pending.binding, record.pending.owner, false);
                return observePending(permit, newWindow());
            }
            permit = permit(caller, Scope.OBSERVE, nextId(), null, caller.principal, false);
            Observation observed = read(permit, null, null, newWindow());
            if (!observed.verifies(observed.route)) {
                boolean matchingRouteMap = record.confirmed != null && observed.route == record.confirmed.choice
                    && record.confirmed.fullMap().equals(observed.mapping.map);
                return result(permit, observed.completeRouting() && !matchingRouteMap ? Outcome.EXPLICIT_SELECTION_REQUIRED : Outcome.OBSERVATION_UNAVAILABLE,
                    observed.completeRouting() && !matchingRouteMap ? Reason.EXPLICIT_CHOICE : Reason.READBACK);
            }
            if (record.confirmed != null && record.confirmed.choice != observed.route) {
                return result(permit, Outcome.EXPLICIT_SELECTION_REQUIRED, Reason.EXPLICIT_CHOICE);
            }
            boolean adoption = record.confirmed == null;
            if (!authorized(permit)) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
            if (!save(new RecordTransition() {
                @Override public Journal.Record build(Journal.Record accepted) { return accepted.confirm(observed, null); }
            })) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            return result(permit, adoption ? Outcome.ADOPTED : Outcome.VERIFIED_BOOT, Reason.NONE);
        } catch (Failure failure) { return failed(permit, failure); }
        catch (OperationIdFailure failure) { return bare(Outcome.REJECTED, Reason.OPERATION_ID); }
        finally { gate.unlock(); }
    }

    /** Explicit selection only; matching route/map is never repaired by a type/GPIO/profile setter. */
    public Result select(Caller caller, Selection selection) {
        Objects.requireNonNull(selection, "selection");
        if (caller == null) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if (!enter()) return bare(Outcome.BUSY, Reason.LEASE);
        OperationId startedOperation = null;
        Permit permit = null;
        try {
            if (closed) return bare(Outcome.CLOSED, Reason.NONE);
            if (!storeReady()) return bare(Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            if (record.pending != null) {
                permit = permit(caller, Scope.RECONCILE_PENDING, record.pending.operation,
                    record.pending.binding, record.pending.owner, selection.explicitUserSelection);
                if (record.pending.kind != OperationKind.ROUTE || record.pending.destination != selection.map.choice
                    || !record.pending.binding.equals(selection.context)) return result(permit, Outcome.BUSY, Reason.LEASE);
                return observePending(permit, newWindow());
            }
            OperationId operation = nextId();
            long started = clock.nowNanos();
            Window operationWindow = new Window(started, config.operationTimeoutNanos);
            permit = permit(caller, Scope.EXPLICIT_ROUTE_SELECTION, operation, selection.context,
                caller.principal, selection.explicitUserSelection);
            if (!selection.explicitUserSelection) return result(permit, Outcome.EXPLICIT_SELECTION_REQUIRED, Reason.EXPLICIT_CHOICE);
            if (!board.equals(selection.context.board)) return result(permit, Outcome.STALE_CONTEXT, Reason.BOARD);
            Observation before = read(permit, selection.context.generation, null, operationWindow);
            // If the route and complete map already match, missing/conflicting type/identity is unavailable,
            // never an excuse to issue an identical route/type/GPIO/map/profile mutation.
            if (before.route == selection.map.choice && selection.map.equals(before.mapping.map)) {
                if (!before.destinationCard.establishes(selection.context)) return result(permit, Outcome.STALE_CONTEXT, Reason.CARD_PORT);
                if (!before.verifies(selection.map.choice)) return result(permit, Outcome.OBSERVATION_UNAVAILABLE, Reason.READBACK);
                if (!authorized(permit)) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
                if (!save(new RecordTransition() {
                    @Override public Journal.Record build(Journal.Record accepted) { return accepted.confirm(before, null); }
                })) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
                return result(permit, Outcome.VERIFIED_NO_OP, Reason.NONE);
            }
            if (!before.completeRouting() || !before.enabledProfile.validIn(before.generation)) {
                return result(permit, Outcome.OBSERVATION_UNAVAILABLE, Reason.READBACK);
            }
            Plan plan;
            Window planWindow = callWindow(operationWindow);
            try { plan = adapter.prepare(new PlanRequest(operation, selection, before, planWindow)); }
            catch (AdapterFailure failure) {
                return result(permit, planWindow.expired(clock.nowNanos()) || operationWindow.expired(clock.nowNanos())
                    ? Outcome.TIMED_OUT : Outcome.REJECTED, planWindow.expired(clock.nowNanos()) ? Reason.CALL_TIMEOUT : Reason.UNSUPPORTED_PLAN);
            }
            catch (RuntimeException failure) {
                return result(permit, planWindow.expired(clock.nowNanos()) || operationWindow.expired(clock.nowNanos())
                    ? Outcome.TIMED_OUT : Outcome.REJECTED, planWindow.expired(clock.nowNanos()) ? Reason.CALL_TIMEOUT : Reason.UNSUPPORTED_PLAN);
            }
            if (planWindow.expired(clock.nowNanos()) || operationWindow.expired(clock.nowNanos())) {
                return result(permit, Outcome.TIMED_OUT, Reason.CALL_TIMEOUT);
            }
            if (!validPlan(plan, operation, selection, before)) return result(permit, Outcome.REJECTED, Reason.UNSUPPORTED_PLAN);
            if (!authorized(permit)) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
            // This write MUST return durable success before even the first mutation admission.
            if (!save(new RecordTransition() {
                @Override public Journal.Record build(Journal.Record accepted) {
                    Pending pending = new Pending(operation, OperationKind.ROUTE, caller.principal,
                        plan.establishedContext, selection.map.choice, before.sequence, before.enabledProfile,
                        Journal.Codec.planDigest(plan), plan.steps().size(), 0, Phase.PREPARED, PendingCause.NONE, 0);
                    return accepted.pending(pending, before);
                }
            })) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            Pending pending = record.pending;
            startedOperation = operation;
            activeOperation = operation; activeWindow = operationWindow;
            for (int index = 0; index < plan.steps().size(); index++) {
                if (operationWindow.expired(clock.nowNanos())) return timeoutPending(permit);
                if (!authorized(permit)) return stopPending(permit, PendingCause.AUTHORIZATION, Reason.AUTHORIZATION);
                if (!progress(Phase.DISPATCHING, PendingCause.NONE, index)) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
                if (operationWindow.expired(clock.nowNanos())) return timeoutPending(permit);
                if (!authorized(permit)) return stopPending(permit, PendingCause.AUTHORIZATION, Reason.AUTHORIZATION);
                Window submissionWindow = callWindow(operationWindow);
                Ack ack;
                observationCurrent = false; // Admission can change hardware even if its ACK is lost.
                try {
                    ack = adapter.submit(new MutationRequest(operation, pending.binding, selection.map,
                        plan.steps().get(index), index, submissionWindow));
                } catch (AdapterFailure failure) {
                    if (submissionWindow.expired(clock.nowNanos()) || operationWindow.expired(clock.nowNanos())) return timeoutPending(permit);
                    return stopAndObserve(permit, operationWindow, PendingCause.TRANSPORT, Reason.TRANSPORT);
                } catch (RuntimeException failure) {
                    if (submissionWindow.expired(clock.nowNanos()) || operationWindow.expired(clock.nowNanos())) return timeoutPending(permit);
                    return stopAndObserve(permit, operationWindow, PendingCause.TRANSPORT, Reason.TRANSPORT);
                }
                if (ack == null || !operation.equals(ack.operation) || !pending.binding.generation.equals(ack.generation)
                    || ack.stepIndex != index || ack.status != AckStatus.ACCEPTED) {
                    return stopAndObserve(permit, operationWindow, PendingCause.TRANSPORT, Reason.TRANSPORT);
                }
                if (!progress(Phase.ACKNOWLEDGED, PendingCause.NONE, index + 1)) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
                if (submissionWindow.expired(clock.nowNanos()) || operationWindow.expired(clock.nowNanos())) return timeoutPending(permit);
            }
            return observePending(permit, operationWindow);
        } catch (Failure failure) {
            if (startedOperation != null && storeReady() && record.pending != null && startedOperation.equals(record.pending.operation)) {
                if (failure.reason == Reason.CALL_TIMEOUT) return timeoutPending(permit);
                return stopPending(permit, failure.reason == Reason.AUTHORIZATION ? PendingCause.AUTHORIZATION : PendingCause.READBACK, failure.reason);
            }
            return failed(permit, failure);
        } catch (OperationIdFailure failure) { return bare(Outcome.REJECTED, Reason.OPERATION_ID); }
        finally { gate.unlock(); }
    }

    /** Events/callbacks supply only an operation/generation hint; fresh authoritative GETs are mandatory. */
    public Result reobserve(Caller caller, Hint hint) {
        Objects.requireNonNull(hint, "hint");
        if (caller == null) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if (!enter()) return bare(Outcome.BUSY, Reason.LEASE);
        Permit permit = null;
        try {
            if (closed) return bare(Outcome.CLOSED, Reason.NONE);
            if (!storeReady()) return bare(Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            if (record.pending == null || !record.pending.operation.equals(hint.operation)
                || !record.pending.binding.generation.equals(hint.generation)) return bare(Outcome.IGNORED_HINT, Reason.OPERATION_EVIDENCE);
            permit = permit(caller, Scope.RECONCILE_PENDING, record.pending.operation,
                record.pending.binding, record.pending.owner, false);
            return observePending(permit, newWindow());
        } catch (Failure failure) { return failed(permit, failure); }
        catch (OperationIdFailure failure) { return bare(Outcome.REJECTED, Reason.OPERATION_ID); }
        finally { gate.unlock(); }
    }

    /** No route change is performed here. A different inaccessible route requires explicit selection first. */
    public Result beginLpaMutation(Caller caller, CardPortContext context, boolean explicitConsent) {
        Objects.requireNonNull(context, "context");
        if (caller == null) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if (!enter()) return bare(Outcome.BUSY, Reason.LEASE);
        Permit permit = null;
        try {
            if (closed) return bare(Outcome.CLOSED, Reason.NONE);
            if (!storeReady()) return bare(Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            OperationId operation = nextId();
            Window window = newWindow();
            permit = permit(caller, Scope.LPA_MUTATION, operation, context, caller.principal, explicitConsent);
            if (!explicitConsent) return result(permit, Outcome.EXPLICIT_SELECTION_REQUIRED, Reason.EXPLICIT_CHOICE);
            if (record.pending != null) return result(permit, Outcome.BUSY, Reason.LEASE);
            if (context.choice() != Choice.ESIM || context.expectedPresence != Presence.PRESENT || !board.equals(context.board)) {
                return result(permit, Outcome.STALE_CONTEXT, Reason.CARD_PORT);
            }
            Observation before = read(permit, context.generation, null, window);
            if (!before.verifies(Choice.ESIM) || !before.destinationCard.establishes(context)) {
                return result(permit, Outcome.OBSERVATION_UNAVAILABLE, Reason.CARD_PORT);
            }
            if (!authorized(permit)) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
            if (!save(new RecordTransition() {
                @Override public Journal.Record build(Journal.Record accepted) {
                    Pending pending = new Pending(operation, OperationKind.LPA_MUTATION, caller.principal,
                        before.destinationCard.boundContext(board), null, before.sequence, before.enabledProfile,
                        null, 0, 0, Phase.LPA_HELD, PendingCause.NONE, 0);
                    return accepted.pending(pending, before);
                }
            })) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            Pending pending = record.pending;
            activeOperation = operation; activeWindow = window;
            activeLease = new LpaLease(pending, localDomain, window, permit);
            return leaseResult(Outcome.LPA_LEASE_GRANTED, activeLease);
        } catch (Failure failure) { return failed(permit, failure); }
        catch (OperationIdFailure failure) { return bare(Outcome.REJECTED, Reason.OPERATION_ID); }
        finally { gate.unlock(); }
    }

    /** Diagnostics only, not permission to bypass admitLpaMutation. Expiry never releases the barrier. */
    public Result checkLpaLease(Caller caller, LpaLease lease) {
        if (!enter()) return bare(Outcome.BUSY, Reason.LEASE);
        try {
            Result invalid = invalidLease(caller, lease);
            if (invalid != null) return invalid;
            return leaseResult(Outcome.LPA_LEASE_VALID, lease);
        } finally { gate.unlock(); }
    }

    /** Execute exactly one external LPA admission under the same non-reentrant serialization gate. */
    public Result admitLpaMutation(Caller caller, LpaLease lease, LpaAdmission admission) {
        Objects.requireNonNull(admission, "admission");
        if (!enter()) return bare(Outcome.BUSY, Reason.LEASE);
        try {
            Result invalid = invalidLease(caller, lease);
            if (invalid != null) return invalid;
            if (lease.admitted) return result(lease.permit, Outcome.REJECTED, Reason.LEASE);
            // Card/port may have changed between granting the lease and starting the backend.
            // Fresh authoritative observation plus backend context enforcement closes that gap.
            Observation observed;
            try { observed = read(lease.permit, lease.context.cardPort.generation, record.pending, lease.window); }
            catch (Failure failure) {
                if (failure.reason == Reason.CALL_TIMEOUT) return timeoutPending(lease.permit);
                if (failure.outcome == Outcome.STORE_UNKNOWN) return failed(lease.permit, failure);
                return stopPending(lease.permit, failure.reason == Reason.AUTHORIZATION ? PendingCause.AUTHORIZATION
                    : PendingCause.STALE_EVIDENCE, failure.reason);
            }
            if (!observed.verifies(Choice.ESIM) || !observed.destinationCard.establishes(lease.context.cardPort)) {
                return stopPending(lease.permit, PendingCause.STALE_EVIDENCE, Reason.CARD_PORT);
            }
            if (!authorized(lease.permit)) return stopPending(lease.permit, PendingCause.AUTHORIZATION, Reason.AUTHORIZATION);
            lease.admitted = true; // Never retried, even if admission throws or its acknowledgement is lost.
            Window window = callWindow(lease.window);
            AckStatus status;
            observationCurrent = false; // External profile state may now change; prior snapshot is historical.
            try { status = admission.admit(lease.context, window); }
            catch (AdapterFailure failure) {
                return window.expired(clock.nowNanos()) || lease.window.expired(clock.nowNanos())
                    ? timeoutPending(lease.permit) : stopPending(lease.permit, PendingCause.TRANSPORT, Reason.TRANSPORT);
            }
            catch (RuntimeException failure) {
                return window.expired(clock.nowNanos()) || lease.window.expired(clock.nowNanos())
                    ? timeoutPending(lease.permit) : stopPending(lease.permit, PendingCause.TRANSPORT, Reason.TRANSPORT);
            }
            if (window.expired(clock.nowNanos()) || lease.window.expired(clock.nowNanos())) return timeoutPending(lease.permit);
            if (status != AckStatus.ACCEPTED) return stopPending(lease.permit, PendingCause.TRANSPORT, Reason.TRANSPORT);
            return result(lease.permit, Outcome.PENDING, Reason.NONE);
        } catch (Failure failure) {
            return failure.reason == Reason.CALL_TIMEOUT && storeReady() && record.pending != null
                ? timeoutPending(lease.permit) : failed(lease.permit, failure);
        }
        finally { gate.unlock(); }
    }

    /** A backend callback is not success and cannot release the barrier without authoritative quiescence. */
    public Result finishLpaMutation(Caller caller, LpaLease lease) {
        if (lease == null) return bare(Outcome.IGNORED_HINT, Reason.LEASE);
        if (caller == null) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if (!enter()) return bare(Outcome.BUSY, Reason.LEASE);
        Permit permit = null;
        try {
            if (closed) return bare(Outcome.CLOSED, Reason.NONE);
            if (!storeReady()) return bare(Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            if (activeLease != lease || !localDomain.equals(lease.localDomain) || record.pending == null
                || record.pending.kind != OperationKind.LPA_MUTATION || !record.pending.operation.equals(lease.context.operation)) {
                return bare(Outcome.IGNORED_HINT, Reason.LEASE);
            }
            permit = permit(caller, Scope.RECONCILE_PENDING, record.pending.operation,
                record.pending.binding, record.pending.owner, false);
            return observePending(permit, newWindow());
        } catch (Failure failure) { return failed(permit, failure); }
        finally { gate.unlock(); }
    }

    private Result invalidLease(Caller caller, LpaLease lease) {
        if (closed) return bare(Outcome.CLOSED, Reason.NONE);
        if (!storeReady()) return bare(Outcome.STORE_UNKNOWN, Reason.JOURNAL);
        if (lease == null || activeLease != lease || !localDomain.equals(lease.localDomain) || record.pending == null
            || record.pending.kind != OperationKind.LPA_MUTATION || !record.pending.operation.equals(lease.context.operation)) {
            return bare(Outcome.REJECTED, Reason.LEASE);
        }
        if (caller == null || !record.pending.owner.equals(caller.principal) || caller.user != lease.permit.request.caller.user) {
            return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        }
        if (record.pending.phase != Phase.LPA_HELD) return result(lease.permit, pendingOutcome(), Reason.LEASE);
        if (lease.window.expired(clock.nowNanos())) return timeoutPending(lease.permit);
        boolean allowed = authorized(lease.permit);
        if (lease.window.expired(clock.nowNanos())) return timeoutPending(lease.permit);
        if (!allowed) return stopPending(lease.permit, PendingCause.AUTHORIZATION, Reason.AUTHORIZATION);
        return null;
    }

    /** A local capability is not live authorization, and a longer permit cannot extend its lease. */
    private Result leaseResult(Outcome outcome, LpaLease lease) {
        boolean allowed = authorized(lease.permit);
        // Policy can block or advance time. Check the shorter operation window AFTER it returns.
        if (lease.window.expired(clock.nowNanos())) return timeoutPending(lease.permit);
        if (!allowed) return stopPending(lease.permit, PendingCause.AUTHORIZATION, Reason.AUTHORIZATION);
        return new Result(outcome, Reason.NONE, view(), lease);
    }

    private Result observePending(Permit permit, Window readWindow) {
        if (!authorized(permit)) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if ((activeOperation == null || !activeOperation.equals(record.pending.operation))
            && record.pending.phase != Phase.TIMED_OUT && record.pending.phase != Phase.PARTIAL_OR_UNKNOWN) {
            // Loaded pending is explicitly interrupted, even if it had only reached PREPARED.
            if (!authorized(permit)) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
            if (!progress(Phase.PARTIAL_OR_UNKNOWN, PendingCause.INTERRUPTION, record.pending.acknowledgedSteps)) {
                return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            }
        }
        if (activeWindow != null && activeOperation != null && activeOperation.equals(record.pending.operation)
            && activeWindow.expired(clock.nowNanos()) && record.pending.phase != Phase.TIMED_OUT) {
            if (!progress(Phase.TIMED_OUT, PendingCause.TIMEOUT, record.pending.acknowledgedSteps)) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
        }
        if (record.pending.reobservations >= config.reobservationBudget) return result(permit, pendingOutcome(), Reason.OBSERVATION_BUDGET);
        if (!authorized(permit)) return bare(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if (!save(new RecordTransition() {
            @Override public Journal.Record build(Journal.Record accepted) {
                return accepted.updatePending(accepted.pending.consumeObservation());
            }
        })) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
        Pending pending = record.pending;
        Observation observed;
        try { observed = read(permit, pending.binding.generation, pending, readWindow); }
        catch (Failure failure) {
            if (failure.outcome == Outcome.STORE_UNKNOWN) return failed(permit, failure);
            if (failure.reason == Reason.AUTHORIZATION) return stopPending(permit, PendingCause.AUTHORIZATION, Reason.AUTHORIZATION);
            if (failure.reason == Reason.CALL_TIMEOUT) return timeoutPending(permit);
            return stopPending(permit, failure.outcome == Outcome.STALE_CONTEXT ? PendingCause.STALE_EVIDENCE : PendingCause.READBACK, failure.reason);
        }
        if (matchesCompletion(pending, observed)) {
            if (!authorized(permit)) return stopPending(permit, PendingCause.AUTHORIZATION, Reason.AUTHORIZATION);
            if (!save(new RecordTransition() {
                @Override public Journal.Record build(Journal.Record accepted) {
                    return pending.kind == OperationKind.ROUTE
                        ? accepted.confirm(observed, pending.operation) : accepted.finishLpa(observed, pending.operation);
                }
            })) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
            activeLease = null; activeOperation = null; activeWindow = null;
            return result(permit, pending.kind == OperationKind.ROUTE ? Outcome.COMMITTED : Outcome.LPA_RECONCILED, Reason.NONE);
        }
        if (!observed.completeRouting() || (pending.kind == OperationKind.ROUTE && !observed.verifies(pending.destination))) {
            return stopPending(permit, PendingCause.READBACK, Reason.READBACK);
        }
        // Complete destination without matching operation/generation/quiescence is still ambiguous.
        return result(permit, pendingOutcome(), Reason.OPERATION_EVIDENCE);
    }

    private boolean matchesCompletion(Pending pending, Observation observed) {
        CompletionEvidence completion = observed.completion;
        if (completion == null || !completion.operation.equals(pending.operation) || completion.kind != pending.kind
            || !completion.generation.equals(pending.binding.generation) || completion.baselineSequence != pending.baselineSequence
            || !completion.complete || !completion.quiescent || observed.sequence <= pending.baselineSequence
            || !observed.destinationCard.establishes(pending.binding)) return false;
        if (pending.kind == OperationKind.LPA_MUTATION) {
            // Reconciles/releases coordination only; it does not claim profile mutation success.
            return observed.verifies(Choice.ESIM) && observed.enabledProfile.accessible();
        }
        if (!observed.verifies(pending.destination)) return false;
        if (pending.priorEnabledProfile.accessible()) {
            if (observed.enabledProfile.accessible()) {
                if (!pending.priorEnabledProfile.sameAccessibleIdentity(observed.enabledProfile)) return false;
            } else if (pending.destination == Choice.ESIM) return false;
        }
        return true;
    }

    private Observation read(Permit permit, Generation required, Pending pending, Window overall) throws Failure {
        if (!authorized(permit)) throw new Failure(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        observationCurrent = false;
        if (ticketNumber == Long.MAX_VALUE) throw new Failure(Outcome.REJECTED, Reason.READBACK);
        Ticket ticket = new Ticket(localDomain, ++ticketNumber);
        Window call = callWindow(overall);
        Observation observed;
        try { observed = adapter.observe(new ReadRequest(ticket, board, required, pending, call)); }
        catch (AdapterFailure failure) {
            throw new Failure(call.expired(clock.nowNanos()) || overall.expired(clock.nowNanos())
                ? Outcome.TIMED_OUT : Outcome.OBSERVATION_UNAVAILABLE,
                call.expired(clock.nowNanos()) ? Reason.CALL_TIMEOUT : Reason.READBACK);
        }
        catch (RuntimeException failure) {
            throw new Failure(call.expired(clock.nowNanos()) || overall.expired(clock.nowNanos())
                ? Outcome.TIMED_OUT : Outcome.OBSERVATION_UNAVAILABLE,
                call.expired(clock.nowNanos()) ? Reason.CALL_TIMEOUT : Reason.READBACK);
        }
        if (!authorized(permit)) throw new Failure(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        if (call.expired(clock.nowNanos()) || overall.expired(clock.nowNanos())) throw new Failure(Outcome.TIMED_OUT, Reason.CALL_TIMEOUT);
        if (observed == null || !ticket.equals(observed.ticket)) throw new Failure(Outcome.STALE_CONTEXT, Reason.READBACK);
        if (!board.equals(observed.board)) throw new Failure(Outcome.STALE_CONTEXT, Reason.BOARD);
        if (!observed.generationEstablishedCurrent) throw new Failure(Outcome.OBSERVATION_UNAVAILABLE, Reason.GENERATION);
        if (required != null && !required.equals(observed.generation)) throw new Failure(Outcome.STALE_CONTEXT, Reason.GENERATION);
        if (olderThan(observed, record.observedGeneration, record.observedSequence)
            || (lastObserved != null && olderThan(observed, lastObserved.generation, lastObserved.sequence))) {
            throw new Failure(Outcome.STALE_CONTEXT, Reason.GENERATION);
        }
        lastObserved = observed;
        // Acceptance advances replay defense even if the caller later rejects a plan, sees a
        // mismatch/unavailable identity, loses authorization, or times out. This metadata-only
        // write cannot adopt a choice or replace pending intent. Failed durability stays unknown.
        if ((!observed.generation.equals(record.observedGeneration) || observed.sequence != record.observedSequence)
            && !saveWatermark(observed)) throw new Failure(Outcome.STORE_UNKNOWN, Reason.JOURNAL);
        if (overall.expired(clock.nowNanos())) throw new Failure(Outcome.TIMED_OUT, Reason.CALL_TIMEOUT);
        observationCurrent = true;
        return observed;
    }

    private static boolean olderThan(Observation observed, Generation watermark, long sequence) {
        return observed.generation.isOlderThan(watermark)
            || (observed.generation.equals(watermark) && observed.sequence < sequence);
    }

    private boolean validPlan(Plan plan, OperationId operation, Selection selection, Observation before) {
        if (plan == null || !operation.equals(plan.operation) || !selection.map.equals(plan.destination)
            || !selection.context.equals(plan.establishedContext) || !plan.targetEvidence.establishes(selection.context)
            || !plan.provenLogical0Preservation || !plan.covers(before)) return false;
        Set<Dimension> assigned = java.util.EnumSet.noneOf(Dimension.class);
        for (Step step : plan.steps()) {
            boolean necessary = false;
            for (Dimension effect : step.effects()) {
                if (!assigned.add(effect)) return false;
                necessary |= (effect == Dimension.ROUTE && before.route != selection.map.choice)
                    || (effect == Dimension.NORMALIZED_TYPE && before.type != SimType.forChoice(selection.map.choice))
                    || (effect == Dimension.COMPLETE_MAP && !selection.map.equals(before.mapping.map));
            }
            if (!necessary) return false;
        }
        return true;
    }

    private Permit permit(Caller caller, Scope scope, OperationId operation, CardPortContext context,
                          Digest owner, boolean explicit) throws Failure {
        if (caller == null) throw new Failure(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        AccessRequest request = new AccessRequest(caller, scope, operation, localDomain, board, context, owner, explicit, clock.nowNanos());
        Permit permit;
        try { permit = auth.authorize(request); }
        catch (AuthFailure failure) { throw new Failure(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION); }
        catch (RuntimeException failure) { throw new Failure(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION); }
        if (permit == null || permit.request != request || !authorized(permit)) throw new Failure(Outcome.UNAUTHORIZED, Reason.AUTHORIZATION);
        return permit;
    }

    private boolean authorized(Permit permit) {
        long now = clock.nowNanos();
        if (permit == null || permit.validity.expired(now)) return false;
        try {
            boolean allowed = auth.remainsAuthorized(permit, now);
            long finished = clock.nowNanos();
            // A slow policy check cannot return a now-expired permit as valid; backwards time fails closed.
            return allowed && finished - now >= 0 && !permit.validity.expired(finished);
        }
        catch (AuthFailure failure) { return false; }
        catch (RuntimeException failure) { return false; }
    }

    private Window newWindow() { return new Window(clock.nowNanos(), config.operationTimeoutNanos); }
    private Window callWindow(Window overall) throws Failure {
        long now = clock.nowNanos();
        if (overall.expired(now)) throw new Failure(Outcome.TIMED_OUT, Reason.CALL_TIMEOUT);
        long remaining = overall.durationNanos - (now - overall.startedAtNanos);
        return new Window(now, Math.min(remaining, config.adapterCallTimeoutNanos));
    }

    private boolean progress(Phase phase, PendingCause cause, int acknowledged) {
        return save(new RecordTransition() {
            @Override public Journal.Record build(Journal.Record accepted) {
                return accepted.updatePending(accepted.pending.progress(phase, cause, acknowledged));
            }
        });
    }
    private Result timeoutPending(Permit permit) {
        if (record.pending.phase != Phase.TIMED_OUT
            && !progress(Phase.TIMED_OUT, PendingCause.TIMEOUT, record.pending.acknowledgedSteps)) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
        return result(permit, Outcome.TIMED_OUT, Reason.CALL_TIMEOUT);
    }
    /** Safety bookkeeping is separate from result disclosure; no adapter admission occurs here. */
    private boolean quarantinePending(PendingCause cause) {
        return record.pending.phase == Phase.TIMED_OUT
            || progress(Phase.PARTIAL_OR_UNKNOWN, cause, record.pending.acknowledgedSteps);
    }
    private Result stopPending(Permit permit, PendingCause cause, Reason reason) {
        if (!quarantinePending(cause)) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
        return reason == Reason.AUTHORIZATION ? bare(pendingOutcome(), reason) : result(permit, pendingOutcome(), reason);
    }
    private Result stopAndObserve(Permit permit, Window window, PendingCause cause, Reason reason) {
        // Never cache a private result and return it after a later failing policy check.
        if (!quarantinePending(cause)) return result(permit, Outcome.STORE_UNKNOWN, Reason.JOURNAL);
        if (window.expired(clock.nowNanos())) return timeoutPending(permit);
        if (!authorized(permit)) return bare(pendingOutcome(), Reason.AUTHORIZATION);
        return observePending(permit, window);
    }
    private Outcome pendingOutcome() {
        if (record.pending.phase == Phase.TIMED_OUT) return Outcome.TIMED_OUT;
        if (record.pending.phase == Phase.PARTIAL_OR_UNKNOWN) return Outcome.PARTIAL_OR_UNKNOWN;
        return Outcome.PENDING;
    }

    /** Only record construction and its durable write are normalized; caller/auth/ID work stays outside. */
    private interface RecordTransition { Journal.Record build(Journal.Record accepted); }
    private boolean save(RecordTransition transition) {
        if (!storeReady()) return false; // A latched UNKNOWN cannot construct, write, or become VALID again.
        try {
            Journal.Record next = transition.build(record);
            store.write(next, record.revision);
            record = next; storeStatus = LoadStatus.VALID; storeReason = StoreReason.NONE; return true;
        }
        catch (StoreFailure failure) { storeStatus = LoadStatus.UNKNOWN; storeReason = failure.reason; return false; }
        catch (RuntimeException failure) { storeStatus = LoadStatus.UNKNOWN; storeReason = StoreReason.WRITE_FAILED; return false; }
    }
    private boolean saveWatermark(Observation observed) {
        return save(new RecordTransition() {
            @Override public Journal.Record build(Journal.Record accepted) { return accepted.watermark(observed); }
        });
    }
    private boolean storeReady() { return storeStatus != LoadStatus.UNKNOWN && record != null; }
    private boolean enter() { return !gate.isHeldByCurrentThread() && gate.tryLock(); }
    private OperationId nextId() {
        for (int attempt = 0; attempt < 8; attempt++) {
            OperationId id;
            try { id = ids.next(); } catch (RuntimeException failure) { throw new OperationIdFailure(); }
            if (id == null) throw new OperationIdFailure();
            if (usedIds.contains(id) || (record != null && record.pending != null && record.pending.operation.equals(id))
                || (record != null && record.confirmed != null && id.equals(record.confirmed.verifiedOperation))) continue;
            usedIds.add(id); return id;
        }
        throw new OperationIdFailure();
    }

    private View view() {
        State state;
        if (closed) state = State.CLOSED;
        else if (!storeReady()) state = State.STORE_UNKNOWN;
        else if (record.pending != null) state = record.pending.phase == Phase.TIMED_OUT ? State.TIMED_OUT
            : record.pending.phase == Phase.PARTIAL_OR_UNKNOWN ? State.PARTIAL_OR_UNKNOWN
            : record.pending.kind == OperationKind.ROUTE ? State.PENDING_ROUTE : State.PENDING_LPA;
        else if (record.confirmed == null && lastObserved == null) state = State.UNRECORDED;
        else if (!observationCurrent) state = State.OBSERVATION_UNAVAILABLE;
        else if (record.confirmed == null) state = lastObserved == null ? State.UNRECORDED : State.SELECTION_REQUIRED;
        else if (lastObserved != null && lastObserved.verifies(record.confirmed.choice)) state = State.VERIFIED;
        else state = lastObserved == null || !lastObserved.completeRouting()
            || (lastObserved.route == record.confirmed.choice && record.confirmed.fullMap().equals(lastObserved.mapping.map))
            ? State.OBSERVATION_UNAVAILABLE : State.SELECTION_REQUIRED;
        return new View(state, record, lastObserved, observationCurrent, storeStatus, storeReason);
    }
    /** The only non-lease private-return constructor; check live policy AFTER all bookkeeping. */
    private Result result(Permit permit, Outcome outcome, Reason reason) {
        if (!authorized(permit)) return bare(outcome, Reason.AUTHORIZATION);
        return new Result(outcome, reason, view(), null);
    }
    private static Result bare(Outcome outcome, Reason reason) { return new Result(outcome, reason, null, null); }
    private Result failed(Permit permit, Failure failure) {
        return failure.outcome == Outcome.UNAUTHORIZED ? bare(failure.outcome, failure.reason) : result(permit, failure.outcome, failure.reason);
    }
    private static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;
        private final Outcome outcome;
        private final Reason reason;
        private Failure(Outcome outcome, Reason reason) { this.outcome = outcome; this.reason = reason; }
    }
    private static final class OperationIdFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private OperationIdFailure() { super("operation ID source unavailable or reused"); }
    }

    /** Process-close does not clear pending, dispatch a saved plan, or repair hardware. */
    @Override public void close() throws StoreFailure {
        if (!enter()) throw new StoreFailure(StoreReason.OWNERSHIP_BUSY);
        try {
            if (closed) return;
            closed = true; activeLease = null; activeOperation = null; activeWindow = null;
            store.close();
        } finally { gate.unlock(); }
    }
}
