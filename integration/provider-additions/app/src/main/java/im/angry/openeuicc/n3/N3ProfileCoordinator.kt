package im.angry.openeuicc.n3

import android.os.Binder
import im.angry.openeuicc.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sourceowned.n3.runtime.v2.RoutingOwnerClient

/** All privileged profile actions use the same owner gate as route switching. */
object N3ProfileCoordinator : ProfileOperationCoordinator {
    private val mutex = Mutex()
    override suspend fun <T> withProfileOperation(context: EuiccOperationContext,
        operation: ProfileOperation, block: suspend () -> T): T = mutex.withLock {
        require(context.physicalSlotId == 1 && context.portId == 1 && context.logicalSlotId == 1)
        check(operation != ProfileOperation.Delete && operation != ProfileOperation.Reset) {
            "Profile deletion and reset are disabled in this integration"
        }
        val client = RoutingOwnerClient.connect()
        val lifetime = Binder()
        // Acquire without cancellation so a granted lease is always paired with finally.
        val grant = withContext(Dispatchers.IO + NonCancellable) { client.beginLpa(lifetime) }
        check(grant.getString("status") == "LPA_GRANTED") { "Select eSIM first, or wait for the current operation" }
        val token = checkNotNull(grant.getString("lease_token"))
        try { block() }
        finally { withContext(Dispatchers.IO + NonCancellable) { client.endLpa(token) } }
    }
}
