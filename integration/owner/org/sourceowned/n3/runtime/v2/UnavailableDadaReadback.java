package org.sourceowned.n3.runtime.v2;

import org.sourceowned.n3.runtime.v2.core.Contracts;
import org.sourceowned.n3.runtime.v2.core.Model;
import org.sourceowned.n3.runtime.v2.dada.DadaBoardAdapter;

/** No synthetic epoch/card, inferred mtb route, guessed type, empty profile or completion. */
public final class UnavailableDadaReadback implements DadaBoardAdapter.Readback {
    @Override public Model.Observation observe(Contracts.ReadRequest request) throws Contracts.AdapterFailure {
        throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.UNAVAILABLE);
    }
    @Override public Model.Plan prepare(Contracts.PlanRequest request) throws Contracts.AdapterFailure {
        throw new Contracts.AdapterFailure(Contracts.AdapterFailureKind.UNAVAILABLE);
    }
    @Override public boolean admit(Contracts.MutationRequest request) { return false; }
}
