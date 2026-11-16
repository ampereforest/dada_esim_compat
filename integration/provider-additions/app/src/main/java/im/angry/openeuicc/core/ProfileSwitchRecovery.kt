package im.angry.openeuicc.core

import android.content.Context
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.os.SystemClock
import android.util.Log
import im.angry.openeuicc.util.simSlotMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Best-effort, finite recovery under the existing LPA lease, without APDU replay.
 * Never use default-subscription APIs, airplane mode, rebootModem or all-radio reset.
 * We intentionally avoid an off/on pair which could strand a radio if this app dies.
 */
internal class ProfileSwitchRecovery(
    private val context: Context,
    private val tm: TelephonyManager,
    private val sm: SubscriptionManager,
) {
    private fun activeTarget(operation: EuiccOperationContext): TelephonyManager? {
        if (operation.physicalSlotId != 1 || operation.portId != 1 ||
            operation.logicalSlotId != 1 || operation.cardId == null) return null
        val map = tm.simSlotMapping
        if (map.size != 2 || map.count { it.logicalSlotIndex == 0 &&
                it.physicalSlotIndex == 0 && it.portIndex == 0 } != 1 ||
            map.count { it.logicalSlotIndex == 1 && it.physicalSlotIndex == 1 &&
                it.portIndex == 1 } != 1) return null
        val card = tm.uiccCardsInfo.singleOrNull { it.cardId == operation.cardId } ?: return null
        if (!card.isEuicc || card.physicalSlotIndex != 1) return null
        val port = card.ports.singleOrNull { it.isActive } ?: return null
        if (port.logicalSlotIndex != 1 || port.portIndex != 1) return null
        val sub = sm.activeSubscriptionInfoList?.singleOrNull {
            it.simSlotIndex == 1 && it.cardId == operation.cardId && it.portIndex == 1
        } ?: return null
        if (!SubscriptionManager.isValidSubscriptionId(sub.subscriptionId)) return null
        return tm.createForSubscriptionId(sub.subscriptionId)
    }

    suspend fun recover(operation: EuiccOperationContext) {
        try {
            // Fresh lookup each time: enabling a profile changes its subscription ID.
            // Disable with no replacement requests only the subscription refresh.
            val started = SystemClock.elapsedRealtime()
            var refreshed = false
            var carrierRefreshed = false
            val observations = RegistrationObservationWindow()
            repeat(20) { attempt ->
                val target = activeTarget(operation)
                if (target != null) {
                    if (!refreshed) {
                        // refreshUiccProfile is a blocked test API on this ROM.
                        // Do not bypass hidden-API policy; the accepted switch already
                        // requested embedded-subscription refresh. Use SystemApi carrier
                        // configuration refresh here instead.
                        context.getSystemService(CarrierConfigManager::class.java)
                            ?.notifyConfigChangedForSubId(target.subscriptionId)
                        refreshed = true
                        Log.i("N3Refresh", "logical1 carrier configuration refresh requested")
                    }
                    val state = target.serviceState
                    // A single early sample may still describe the old profile or a
                    // transient registration during the modem's second refresh.
                    // Observe through the settling interval, without APDU replay.
                    val elapsed = SystemClock.elapsedRealtime() - started
                    if (observations.observe(target.subscriptionId, elapsed,
                            state?.state == ServiceState.STATE_IN_SERVICE)) {
                        Log.i("N3Refresh", "logical1 voice IN_SERVICE stable for three observations after ${elapsed}ms")
                        return
                    }
                    if (attempt >= 8 && !carrierRefreshed && state != null &&
                        state.state != ServiceState.STATE_IN_SERVICE &&
                        state.state != ServiceState.STATE_POWER_OFF &&
                        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, -1) == 0 &&
                        tm.callState == TelephonyManager.CALL_STATE_IDLE) {
                        // OEM implementation takes this instance's explicit subId. This
                        // resets carrier actions on logical1, not the shared modem.
                        TelephonyManager::class.java.getMethod("resetAllCarrierActions").invoke(target)
                        carrierRefreshed = true
                        Log.i("N3Refresh", "logical1 carrier actions refresh requested")
                    }
                } else observations.observe(null, SystemClock.elapsedRealtime() - started, false)
                delay(1_000)
            }
            Log.w("N3Refresh", "stable registration not observed after ${SystemClock.elapsedRealtime() - started}ms; profile remains verified, no replay")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Log.w("N3Refresh", "targeted recovery unavailable; profile remains verified, no replay")
        }
    }
}
