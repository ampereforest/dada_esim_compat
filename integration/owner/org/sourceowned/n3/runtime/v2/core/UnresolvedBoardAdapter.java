package org.sourceowned.n3.runtime.v2.core;

import static org.sourceowned.n3.runtime.v2.core.Contracts.*;
import static org.sourceowned.n3.runtime.v2.core.Model.*;

/** Explicit integration placeholder: no Android/native implementation and no successful observation. */
public final class UnresolvedBoardAdapter implements BoardAdapter {
    @Override public Observation observe(ReadRequest request) throws AdapterFailure {
        throw new AdapterFailure(AdapterFailureKind.UNAVAILABLE);
    }
    @Override public Plan prepare(PlanRequest request) throws AdapterFailure {
        throw new AdapterFailure(AdapterFailureKind.UNAVAILABLE);
    }
    @Override public Ack submit(MutationRequest request) throws AdapterFailure {
        throw new AdapterFailure(AdapterFailureKind.UNAVAILABLE);
    }
}
