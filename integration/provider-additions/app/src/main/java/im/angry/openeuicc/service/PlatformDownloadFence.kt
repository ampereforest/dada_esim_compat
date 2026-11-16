package im.angry.openeuicc.service

import android.content.Context
import im.angry.openeuicc.core.EuiccOperationContext
import im.angry.openeuicc.util.LPAString

/**
 * Private CE replay fence, not an N3 route record. Persist before starting an ES9+ session.
 * A process-lost/evicted session must never be automatically opened again for the same order.
 * No activation code, EID, ICCID or IMEI is stored. Backup is disabled in the application.
 * Exhaustion/corruption/storage failure fails closed; clearing a fence requires separate recovery.
 */
internal class PlatformDownloadFence(context: Context) {
    private val preferences = context.getSharedPreferences("platform_download_fence_v1", Context.MODE_PRIVATE)

    private fun key(code: LPAString) = EuiccOperationContext.bindEid(code.toString())

    fun contains(code: LPAString) = preferences.contains(key(code))

    fun reserve(code: LPAString, operation: EuiccOperationContext) {
        check(!contains(code)) { "Order has a prior operation; reobserve instead of redownloading" }
        check(preferences.all.size < 32) { "Operation replay fence is full" }
        check(preferences.edit().putString(key(code), operation.operationId).commit()) {
            "Unable to retain the operation replay fence"
        }
    }
}
