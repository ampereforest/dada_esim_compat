package im.angry.openeuicc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.euicc.EuiccService
import im.angry.openeuicc.ui.EuiccManageActivity

/** The manifest also requires MODIFY_PHONE_STATE; extras are never forwarded. */
class EuiccSecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SECRET_CODE" ||
            intent.data?.toString() != "android_secret_code://3418") return
        context.startActivity(Intent(context, EuiccManageActivity::class.java).apply {
            action = EuiccService.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
