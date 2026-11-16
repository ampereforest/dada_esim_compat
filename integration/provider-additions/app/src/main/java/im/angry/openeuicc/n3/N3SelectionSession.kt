package im.angry.openeuicc.n3

import im.angry.openeuicc.ui.RouteChoiceSession

/** Main-thread UI session. Nothing is saved to disk, Bundle state, logs, or retained ViewModels. */
internal class N3SelectionSession(
    private val gateway: N3Gateway,
    private val now: () -> Long,
    private val foreground: () -> Boolean
) {
    enum class Phase { IDLE, READING, PROPOSING, PROPOSAL, CONFIRMING, COMMITTED,
        CONFIRMED_PENDING, CONFIRMED_NO_WRITES_UNVERIFIED, CONFIRMED_UNVERIFIED,
        CONFIRMED_UNKNOWN, ABORTED_ORIGINAL_ROUTE, UNAVAILABLE }
    var phase = Phase.IDLE; private set
    var report = ""; private set
    var destination: Boolean? = null; private set
    var busy = false; private set
    @Volatile private var epoch = 0
    private val consent = RouteChoiceSession()
    @Volatile private var deadline = 0L
    fun invalidate() {
        epoch++
        consent.invalidate()
        if (phase == Phase.PROPOSAL || phase == Phase.PROPOSING) {
            phase = Phase.IDLE
            report = "Proposal discarded. Request fresh consent in foreground."
        } else if (phase == Phase.CONFIRMING) {
            phase = Phase.CONFIRMED_UNKNOWN
            report = "Confirmation may already be submitted. Read status; do not retry automatically."
        }
    }
    fun canConfirm(): Boolean = !busy && phase == Phase.PROPOSAL && consent.ready() &&
        now() < deadline && foreground()
    fun expire() { if (phase == Phase.PROPOSAL && now() >= deadline) invalidate() }
    suspend fun readStatus() {
        if (busy) return
        invalidate(); busy = true; phase = Phase.READING
        try { report = N3Report.json(gateway.status()); phase = Phase.IDLE }
        catch (_: Exception) { report = N3Report.unavailable(); phase = Phase.UNAVAILABLE }
        finally { busy = false }
    }
    suspend fun propose(esim: Boolean) {
        if (busy || !foreground()) return
        invalidate(); val requestEpoch = epoch
        destination = esim; deadline = now() + 25_000L
        busy = true; phase = Phase.PROPOSING
        try {
            val result = gateway.propose(esim)
            if (requestEpoch != epoch) return
            if (!foreground() || now() >= deadline) { invalidate(); return }
            report = N3Report.json(result)
            val issued = result.get("consent_token") as? String
            if (N3Report.valid(result) && N3Report.status(result) == "CONFIRM_EXPLICIT_ROUTE_IN_FOREGROUND_UI" &&
                result.get("destination") == (if (esim) 1 else 0) && issued != null && issued.length in 1..256) {
                check(consent.accept(epoch.toLong(), if (esim) 1 else 0,
                    result.getInt("destination", -1), N3Report.status(result), issued))
                phase = Phase.PROPOSAL
            } else { phase = Phase.UNAVAILABLE }
        } catch (_: Exception) {
            if (requestEpoch == epoch) { report = N3Report.unavailable(); phase = Phase.UNAVAILABLE }
        } finally { busy = false }
    }
    /** This is called ONLY by the Activity Confirm button, never proposal completion/status/root. */
    suspend fun confirmFromUserButton() {
        if (!canConfirm()) { expire(); return }
        val token = checkNotNull(consent.consume()) // one attempt, even on timeout/denial
        // R2 consume invalidates its epoch; keep the lifecycle epoch synchronized.
        epoch++
        val requestEpoch = epoch
        busy = true; phase = Phase.CONFIRMING
        try {
            val result = gateway.confirm(token) { requestEpoch == epoch && foreground() && now() < deadline }
            if (requestEpoch != epoch) return
            report = N3Report.json(result)
            phase = when {
                !N3Report.valid(result) -> Phase.CONFIRMED_UNKNOWN
                N3Report.status(result) == "COMMITTED" && !N3Report.pending(result) -> Phase.COMMITTED
                N3Report.pending(result) -> Phase.CONFIRMED_PENDING
                N3Report.status(result) == "ABORTED_ORIGINAL_ROUTE" -> Phase.ABORTED_ORIGINAL_ROUTE
                N3Report.status(result) == "MAP_MATCH_NO_WRITES_ROUTE_TYPE_NOT_VERIFIED" -> Phase.CONFIRMED_NO_WRITES_UNVERIFIED
                N3Report.status(result) == "UNRECOGNIZED_NOT_COMMITTED" -> Phase.CONFIRMED_UNKNOWN
                else -> Phase.CONFIRMED_UNVERIFIED
            }
        } catch (_: Exception) {
            if (requestEpoch == epoch) { report = N3Report.unavailable(); phase = Phase.CONFIRMED_UNKNOWN }
        } finally { busy = false }
    }
}
