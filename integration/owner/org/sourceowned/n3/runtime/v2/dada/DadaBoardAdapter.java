package org.sourceowned.n3.runtime.v2.dada;

import java.util.Objects;
import java.util.Collections;
import org.sourceowned.n3.runtime.v2.core.Contracts;
import org.sourceowned.n3.runtime.v2.core.Model;

/** Fixed dada transport binding. No boot repair and no profile mutation operations. */
public final class DadaBoardAdapter implements Contracts.BoardAdapter {
    /** Platform owner must supply fresh route/type/card evidence and its validated step plan. */
    public interface Readback {
        Model.Observation observe(Contracts.ReadRequest request) throws Contracts.AdapterFailure;
        Model.Plan prepare(Contracts.PlanRequest request) throws Contracts.AdapterFailure;
        boolean admit(Contracts.MutationRequest request) throws Contracts.AdapterFailure;
    }

    public enum Submission { ACCEPTED, NOT_STARTED, AMBIGUOUS }

    /** Implementations are local to a protected owner, never an exported arbitrary command API. */
    public interface Transport {
        Submission selectModem(Model.Choice choice, Contracts.Window window);
        Submission setCompleteMapping(Model.FullMap mapping, Contracts.Window window);
    }

    private final Readback readback;
    private final Transport transport;
    private final Contracts.MonotonicClock clock;

    public DadaBoardAdapter(Readback readback, Transport transport, Contracts.MonotonicClock clock) {
        this.readback = Objects.requireNonNull(readback, "readback");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public Model.Observation observe(Contracts.ReadRequest request) throws Contracts.AdapterFailure {
        requireWindow(request.callWindow);
        return readback.observe(request);
    }

    @Override public Model.Plan prepare(Contracts.PlanRequest request) throws Contracts.AdapterFailure {
        requireWindow(request.callWindow);
        Model.Plan plan = readback.prepare(request);
        if (plan == null || !plan.operation.equals(request.operation)
                || !plan.destination.equals(request.selection.map)
                || !plan.provenLogical0Preservation) {
            throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.UNAVAILABLE);
        }
        // mtb affects board routing; its undocumented normalized-type effects are NOT invented.
        for (Model.Step step : plan.steps()) {
            if (step.kind != Model.MutationKind.LOGICAL1_ROUTE
                    && step.kind != Model.MutationKind.COMPLETE_SLOT_MAP) {
                throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.UNAVAILABLE);
            }
            Model.Dimension expected = step.kind == Model.MutationKind.LOGICAL1_ROUTE
                    ? Model.Dimension.ROUTE : Model.Dimension.COMPLETE_MAP;
            if (!step.effects().equals(Collections.singleton(expected))) {
                throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.UNAVAILABLE);
            }
        }
        return plan;
    }

    @Override public Contracts.Ack submit(Contracts.MutationRequest request) throws Contracts.AdapterFailure {
        requireWindow(request.callWindow);
        if (!request.context.choice().equals(request.completeDestination.choice)
                || !readback.admit(request)) {
            throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.NOT_STARTED);
        }
        Model.Dimension expected = request.step.kind == Model.MutationKind.LOGICAL1_ROUTE
                ? Model.Dimension.ROUTE : Model.Dimension.COMPLETE_MAP;
        if (!request.step.effects().equals(Collections.singleton(expected))) {
            throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.NOT_STARTED);
        }
        requireWindow(request.callWindow);
        final Submission result;
        if (request.step.kind == Model.MutationKind.LOGICAL1_ROUTE) {
            result = transport.selectModem(request.completeDestination.choice, request.callWindow);
        } else if (request.step.kind == Model.MutationKind.COMPLETE_SLOT_MAP) {
            result = transport.setCompleteMapping(request.completeDestination, request.callWindow);
        } else {
            throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.NOT_STARTED);
        }
        Contracts.AckStatus status = result == Submission.ACCEPTED ? Contracts.AckStatus.ACCEPTED
                : result == Submission.NOT_STARTED ? Contracts.AckStatus.REJECTED_WITHOUT_MUTATION
                : Contracts.AckStatus.AMBIGUOUS;
        // Completion comes only from readback, never an echoed request or a command exit.
        return new Contracts.Ack(request.operation, request.context.generation, request.stepIndex, status);
    }

    private void requireWindow(Contracts.Window window) throws Contracts.AdapterFailure {
        if (window == null || window.expired(clock.nowNanos())) {
            throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.NOT_STARTED);
        }
    }
}
