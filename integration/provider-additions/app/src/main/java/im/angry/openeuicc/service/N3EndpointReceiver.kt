package im.angry.openeuicc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import org.sourceowned.n3.runtime.v2.RoutingOwnerClient

/** ROM-only callback: protected receiver + owner protocol + direct system UID.
 * UID1000 is the privileged system shared UID, not unique system_server identity.
 * No reflected SID/PID-context API is required for provider handoff.
 */
class N3EndpointReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val offer = intent.extras?.getBinder("n3_offer") ?: return
        val pending = goAsync()
        Thread {
            val endpoint = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code != 1 || reply == null || flags and IBinder.FLAG_ONEWAY != 0 || data.dataSize() > 1024) return false
                    if (Binder.getCallingUid() != 1000) throw SecurityException("Direct system UID owner required")
                    data.enforceInterface("im.angry.openeuicc.IN3Endpoint")
                    val owner = requireNotNull(data.readStrongBinder()); require(data.dataAvail() == 0)
                    RoutingOwnerClient.install(owner)
                    reply.writeNoException(); return true
                }
            }
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("org.sourceowned.n3.runtime.v2.IRoutingOwner")
                data.writeInt(2); data.writeStrongBinder(endpoint)
                check(offer.transact(7, data, reply, 0)); reply.readException()
            } catch (failure: Exception) { android.util.Log.e("N3Endpoint", "ROM system-UID handoff denied", failure) }
            finally { data.recycle(); reply.recycle(); pending.finish() }
        }.start()
    }
}
