package com.dada.coloros.esim.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import im.angry.openeuicc.bridge.IDadaEsimBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class RouteState(
    val ok: Boolean,
    val ownerAvailable: Boolean,
    val mode: String,
    val isEsim: Boolean,
    val eid: String,
    val durablePending: Boolean,
    val lpaBusy: Boolean,
    val map: String,
    val slot0Active: Boolean,
    val slot0Carrier: String,
    val slot0Number: String,
    val slot1Active: Boolean,
    val slot1Carrier: String,
    val slot1Number: String,
    val slot1SimState: Int,
)

data class EsimProfile(
    val iccid: String,
    val name: String,
    val nickname: String,
    val providerName: String,
    val displayName: String,
    val enabled: Boolean,
    val isdpAid: String,
    val eid: String,
)

data class BridgeOpResult(
    val ok: Boolean,
    val status: String = "",
    val error: String? = null,
    val durablePending: Boolean = false,
    val confirmationCodeRequired: Boolean = false,
    val iccid: String = "",
)

class DadaEsimBridgeClient(private val appContext: Context) {
    companion object {
        private const val OPENEUI_PKG = "im.angry.openeuicc"
        private const val BRIDGE_CLS = "im.angry.openeuicc.service.DadaEsimBridgeService"
        private const val BRIDGE_ACTION = "im.angry.openeuicc.action.DADA_ESIM_BRIDGE"
    }

    private val connectMutex = Mutex()
    @Volatile
    private var service: IDadaEsimBridge? = null
    private var activeConnection: ServiceConnection? = null

    private suspend fun ensureConnected(): IDadaEsimBridge = connectMutex.withLock {
        val current = service
        if (current != null && current.asBinder().isBinderAlive) {
            return current
        }
        disconnectLocked()

        val deferred = CompletableDeferred<IDadaEsimBridge>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val iface = IDadaEsimBridge.Stub.asInterface(binder)
                service = iface
                if (!deferred.isCompleted) deferred.complete(iface)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }

            override fun onBindingDied(name: ComponentName?) {
                service = null
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(IllegalStateException("OpenEUICC bridge binding died"))
                }
            }
        }
        val intent = Intent(BRIDGE_ACTION).setComponent(ComponentName(OPENEUI_PKG, BRIDGE_CLS))
        // BIND_IMPORTANT elevates OpenEUICC to PROCESS_STATE_BOUND_TOP while DadaEsimUI is foreground,
        // satisfying N3Owner ForegroundPolicy without modifying OpenEUICC's existing UI or N3Owner.
        val bound = appContext.bindService(
            intent,
            conn,
            Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        )
        if (!bound) {
            runCatching { appContext.unbindService(conn) }
            throw SecurityException("Unable to bind OpenEUICC bridge service")
        }
        activeConnection = conn
        return withTimeout(10_000L) { deferred.await() }
    }

    private fun disconnectLocked() {
        activeConnection?.let { conn ->
            runCatching { appContext.unbindService(conn) }
        }
        activeConnection = null
        service = null
    }

    fun close() {
        activeConnection?.let { runCatching { appContext.unbindService(it) } }
        activeConnection = null
        service = null
    }

    suspend fun getRouteState(): RouteState = withContext(Dispatchers.IO) {
        val b = ensureConnected().routeState
        RouteState(
            ok = b.getBoolean("ok", false),
            ownerAvailable = b.getBoolean("owner_available", false),
            mode = b.getString("mode", "UNKNOWN"),
            isEsim = b.getBoolean("is_esim", false),
            eid = b.getString("eid", ""),
            durablePending = b.getBoolean("durable_pending", false),
            lpaBusy = b.getBoolean("lpa_busy", false),
            map = b.getString("map", ""),
            slot0Active = b.getBoolean("slot0_active", false),
            slot0Carrier = b.getString("slot0_carrier", ""),
            slot0Number = b.getString("slot0_number", ""),
            slot1Active = b.getBoolean("slot1_active", false),
            slot1Carrier = b.getString("slot1_carrier", ""),
            slot1Number = b.getString("slot1_number", ""),
            slot1SimState = b.getInt("slot1_sim_state", 0),
        )
    }

    suspend fun switchSlot2Route(toEsim: Boolean): BridgeOpResult = withContext(Dispatchers.IO) {
        val b: Bundle = ensureConnected().switchSlot2Route(toEsim)
        BridgeOpResult(
            ok = b.getBoolean("ok", false),
            status = b.getString("status", ""),
            error = b.getString("error"),
            durablePending = b.getBoolean("durable_pending", false),
        )
    }

    suspend fun listProfiles(): List<EsimProfile> = withContext(Dispatchers.IO) {
        val list = ensureConnected().listProfiles() ?: emptyList()
        list.map { b ->
            EsimProfile(
                iccid = b.getString("iccid", ""),
                name = b.getString("name", ""),
                nickname = b.getString("nickname", ""),
                providerName = b.getString("provider_name", ""),
                displayName = b.getString("display_name", ""),
                enabled = b.getBoolean("enabled", false),
                isdpAid = b.getString("isdp_aid", ""),
                eid = b.getString("eid", ""),
            )
        }
    }

    suspend fun setProfileEnabled(iccid: String, enabled: Boolean): BridgeOpResult = withContext(Dispatchers.IO) {
        val b = ensureConnected().setProfileEnabled(iccid, enabled)
        BridgeOpResult(
            ok = b.getBoolean("ok", false),
            error = b.getString("error"),
        )
    }

    suspend fun renameProfile(iccid: String, newNickname: String): BridgeOpResult = withContext(Dispatchers.IO) {
        val b = ensureConnected().renameProfile(iccid, newNickname)
        BridgeOpResult(
            ok = b.getBoolean("ok", false),
            error = b.getString("error"),
        )
    }

    suspend fun downloadProfile(smdpActivationCode: String, confirmationCode: String?): BridgeOpResult =
        withContext(Dispatchers.IO) {
            val b = ensureConnected().downloadProfile(smdpActivationCode, confirmationCode)
            BridgeOpResult(
                ok = b.getBoolean("ok", false),
                error = b.getString("error"),
                confirmationCodeRequired = b.getBoolean("confirmation_code_required", false),
                iccid = b.getString("iccid", ""),
            )
        }
}
