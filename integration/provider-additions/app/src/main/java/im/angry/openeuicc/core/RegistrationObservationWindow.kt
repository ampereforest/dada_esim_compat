package im.angry.openeuicc.core

/** Samples only; never performs a modem operation or treats one stale sample as recovery. */
internal class RegistrationObservationWindow {
    private var subscription: Int? = null
    private var consecutive = 0

    fun observe(subscriptionId: Int?, elapsedMillis: Long, inService: Boolean): Boolean {
        if (subscription != subscriptionId) consecutive = 0
        subscription = subscriptionId
        consecutive = if (subscriptionId != null && elapsedMillis >= 5_000 && inService)
            consecutive + 1 else 0
        return consecutive >= 3
    }
}
