package im.angry.openeuicc.n3

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sourceowned.n3.runtime.v2.RoutingOwnerClient
import im.angry.openeuicc.ui.RouteChoiceSafety
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal interface N3Gateway {
    suspend fun status(): Bundle
    suspend fun propose(esim: Boolean): Bundle
    suspend fun confirm(token: String, stillForeground: () -> Boolean): Bundle
}

/** One actual worker process-wide. Timeout does NOT release a stuck Binder's admission.
 * No automatic retry/reconnect/confirm; discovery and every call use this app's own UID.
 */
internal object RuntimeN3Gateway : N3Gateway {
    private val worker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        SynchronousQueue(), { task -> Thread(task, "n3-provider-ipc").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    private suspend fun call(body: () -> Bundle): Bundle = withContext(Dispatchers.IO) {
        // Future is deliberately not canceled: Android Binder cannot revoke an in-flight write.
        val future = worker.submit<Bundle> { body() }
        future.get(65, TimeUnit.SECONDS)
    }
    override suspend fun status() = call { RoutingOwnerClient.connect().status() }
    override suspend fun propose(esim: Boolean) = call { RoutingOwnerClient.connect().select(esim) }
    override suspend fun confirm(token: String, stillForeground: () -> Boolean) = call {
        val client = RoutingOwnerClient.connect()
        check(stillForeground()) { "Foreground consent unavailable" }
        client.confirm(token)
    }
    suspend fun confirmWithPreflight(token: String, stillForeground: () -> Boolean,
        probe: RouteChoiceSafety.Probe) = call {
        val client = RoutingOwnerClient.connect()
        check(stillForeground()) { "Foreground consent unavailable" }
        check(RouteChoiceSafety.allowed(probe)) { "Preflight GET unsafe or unavailable" }
        check(stillForeground()) { "Foreground consent unavailable" }
        client.confirm(token)
    }
}
