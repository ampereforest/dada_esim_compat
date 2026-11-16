package org.sourceowned.n3.runtime.v2;

import org.sourceowned.n3.runtime.v2.core.Model;
import org.sourceowned.n3.runtime.v2.dada.DadaBoardAdapter.Submission;

/** Actual engineering admission sequence, testable without Android or fake hardware authority. */
public final class EngineeringSelection {
    public interface Owner {
        Model.FullMap map() throws Exception;
        default Model.FullMap mapAfterModem() throws Exception { return map(); }
        boolean pending();
        boolean authorized(); // original Binder UID + refreshed signature/foreground policy
        boolean safe(); // bounded owner framework GETs, not global all-writer quiescence
        void persist(String phase, int destination, String token) throws Exception;
        Submission modem(int destination);
        Submission submitMap(Model.FullMap destination);
        boolean verify(int destination) throws Exception;
        default boolean originalRouteStable(Model.FullMap original) throws Exception { return false; }
        default void abort(int destination, String token) throws Exception { throw new java.io.IOException("abort unavailable"); }
    }
    public static final class Result {
        public String status, modemAck, mapAck;
        public boolean admitted, readback;
        Result(String status) { this.status = status; }
    }
    public synchronized Result confirm(ConsentTokens.Request request, String token, Owner owner) {
        Result result = new Result("UNKNOWN_AFTER_ADMISSION");
        boolean prepared = false;
        Model.FullMap original = null;
        try {
            Model.FullMap before = owner.map();
            original = before;
            if (!before.toString().equals(request.baseline)) { result.status = "STALE_MAP_RECONFIRM_REQUIRED"; return result; }
            Model.FullMap destination = Model.FullMap.forChoice(request.destination == 1 ? Model.Choice.ESIM : Model.Choice.PHYSICAL_SIM_2);
            if (owner.pending()) { result.status = "DURABLE_PENDING_REQUIRES_RECONCILIATION"; return result; }
            if (!owner.authorized() || !owner.safe()) { result.status = "CONSENT_REVOKED_OR_CALL_SIM_UNSAFE"; return result; }
            if (before.equals(destination) && owner.verify(request.destination)) {
                owner.persist("PREPARED", request.destination, token);
                owner.persist("COMMITTED", request.destination, token);
                result.status = "COMMITTED"; result.readback = true; result.mapAck = "MATCH_NO_WRITE"; return result;
            }
            try { owner.persist("PREPARED", request.destination, token); }
            catch (Exception failure) { result.status = "DURABILITY_FAILED_NO_ADMISSION"; return result; }
            prepared = true;
            // Slow fsync must not make stale consent/map/safety sufficient for admission.
            if (!owner.authorized() || !owner.safe() || !owner.map().equals(before)) {
                result.status = "REVOKED_OR_CHANGED_DURABLE_PENDING"; return result;
            }
            Submission modem = owner.modem(request.destination);
            result.modemAck = modem.name(); result.admitted = modem != Submission.NOT_STARTED;
            if (modem != Submission.ACCEPTED) {
                owner.persist(modem == Submission.NOT_STARTED ? "FAILED" : "AMBIGUOUS", request.destination, token);
                result.status = modem == Submission.NOT_STARTED ? "FAILED_NOT_STARTED" : "PENDING_MODEM_NOT_PROVEN_COMPLETE"; return result;
            }
            owner.persist("SUBMITTED", request.destination, token);
            // SIM state can transiently become UNKNOWN after the modem switches. Finish the
            // already-authorized transaction; losing foreground does not cancel hardware work.
            Model.FullMap afterModem;
            try { afterModem = owner.mapAfterModem(); }
            catch (Exception transientMap) {
                owner.persist("READBACK_PENDING", request.destination, token);
                result.readback = true;
                if (owner.verify(request.destination)) {
                    owner.persist("COMMITTED", request.destination, token);
                    result.status = "COMMITTED"; result.mapAck = "MATCH_NO_WRITE";
                } else result.status = "PENDING_ROUTE_READBACK";
                return result;
            }
            if (afterModem.equals(destination)) result.mapAck = "MATCH_NO_WRITE";
            else {
                if (!afterModem.equals(before)) {
                    result.status = "CHANGED_BEFORE_MAP_PENDING"; return result;
                }
                result.mapAck = owner.submitMap(destination).name();
            }
            owner.persist("READBACK_PENDING", request.destination, token);
            result.readback = true;
            if (owner.verify(request.destination)) {
                owner.persist("COMMITTED", request.destination, token);
                result.status = "COMMITTED";
            } else result.status = "PENDING_ROUTE_READBACK";
        } catch (Exception failure) {
            result.status = prepared ? "UNKNOWN_AFTER_PREPARED_INTENT" : "GET_UNAVAILABLE_NO_ADMISSION";
        } finally {
            // Explicit user authorization005 supersedes ambiguous no-abort policy.
            // No automatic modem replay: another switch still needs fresh UI consent.
            if (prepared && !"COMMITTED".equals(result.status)) {
                try {
                    if (owner.originalRouteStable(original)) {
                        owner.abort(request.destination, token);
                        result.status = "ABORTED_ORIGINAL_ROUTE";
                        result.readback = true;
                    }
                } catch (Exception abortFailed) {
                    result.status = "ABORT_FAILED_DURABLE_PENDING";
                }
            }
        }
        return result;
    }
}
