package im.angry.openeuicc.ui

import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings
import android.telephony.TelephonyManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.R
import im.angry.openeuicc.n3.N3SelectionSession
import im.angry.openeuicc.n3.N3Gateway
import im.angry.openeuicc.n3.RuntimeN3Gateway
import kotlinx.coroutines.launch

/** Nonexported native Manage child. Trusted signed engineering UI, NOT OS button attestation. */
class DadaRouteChoiceActivity : AppCompatActivity() {
    internal companion object {
        var gatewayFactory: (Context) -> N3Gateway = { context ->
            object : N3Gateway {
                override suspend fun status() = RuntimeN3Gateway.status()
                override suspend fun propose(esim: Boolean) = RuntimeN3Gateway.propose(esim)
                override suspend fun confirm(token: String, stillForeground: () -> Boolean) =
                    RuntimeN3Gateway.confirmWithPreflight(token, stillForeground, safetyProbe(context))
            }
        }
        // R2 provider-ui exact native preflight GET path, incorporated into the bounded worker.
        // Veto only: no owner serialization, continuity proof, or profile-operation lease.
        private fun safetyProbe(context: Context): RouteChoiceSafety.Probe {
            val tm = context.getSystemService(TelephonyManager::class.java)
            return object : RouteChoiceSafety.Probe {
                @Suppress("DEPRECATION")
                override fun callIdle() = tm != null && tm.callState == TelephonyManager.CALL_STATE_IDLE
                override fun slotSafe(slot: Int): Boolean {
                    val state = tm?.getSimState(slot) ?: return false
                    return state == TelephonyManager.SIM_STATE_ABSENT || state == TelephonyManager.SIM_STATE_READY
                }
                override fun noNativeVsim(): Boolean {
                    val type = Class.forName("android.telephony.OplusTelephonyManager")
                    val singleton = type.getMethod("getInstance", Context::class.java).invoke(null, context)
                    return singleton != null && type.getMethod("getVsimId").invoke(singleton) as Int != 1
                }
                override fun airplaneOff() = Settings.Global.getInt(context.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON, -1) == 0
            }
        }
    }
    @Volatile private var resumed = false
    @Volatile private var focused = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var session: N3SelectionSession
    private fun foreground() = resumed && focused &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private val expiry = object : Runnable {
        override fun run() {
            if (!resumed) return
            session.expire(); render()
            handler.postDelayed(this, 250)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_n3_selection)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.n3_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        session = N3SelectionSession(gatewayFactory(this), SystemClock::elapsedRealtime, ::foreground)
        for (id in listOf(R.id.n3_status, R.id.n3_esim, R.id.n3_psim, R.id.n3_confirm)) {
            findViewById<Button>(id).filterTouchesWhenObscured = true
        }
        findViewById<Button>(R.id.n3_status).setOnClickListener { runAction { session.readStatus() } }
        findViewById<Button>(R.id.n3_esim).setOnClickListener { runAction { session.propose(true) } }
        findViewById<Button>(R.id.n3_psim).setOnClickListener { runAction { session.propose(false) } }
        findViewById<Button>(R.id.n3_confirm).setOnClickListener {
            // The only confirmation entry; no intents/extras/onResume/status/proposal invoke it.
            runAction { session.confirmFromUserButton() }
        }
        render()
    }
    private fun runAction(action: suspend () -> Unit) {
        lifecycleScope.launch { action(); render() }
        render()
    }
    private fun render() {
        if (!::session.isInitialized) return
        val ready = foreground() && !session.busy
        for (id in listOf(R.id.n3_status, R.id.n3_esim, R.id.n3_psim)) findViewById<Button>(id).isEnabled = ready
        findViewById<Button>(R.id.n3_confirm).apply {
            visibility = if (session.phase == N3SelectionSession.Phase.PROPOSAL) View.VISIBLE else View.GONE
            isEnabled = session.canConfirm()
            text = getString(if (session.destination == true) R.string.n3_confirm_esim else R.string.n3_confirm_psim)
        }
        val phaseText = when (session.phase) {
            N3SelectionSession.Phase.IDLE -> R.string.n3_idle
            N3SelectionSession.Phase.READING -> R.string.n3_reading
            N3SelectionSession.Phase.PROPOSING -> R.string.n3_proposing
            N3SelectionSession.Phase.PROPOSAL -> R.string.n3_proposal
            N3SelectionSession.Phase.CONFIRMING -> R.string.n3_confirming
            N3SelectionSession.Phase.COMMITTED -> R.string.n3_committed
            N3SelectionSession.Phase.ABORTED_ORIGINAL_ROUTE -> R.string.n3_aborted_original_route
            N3SelectionSession.Phase.CONFIRMED_PENDING -> R.string.n3_confirmed_pending
            N3SelectionSession.Phase.CONFIRMED_NO_WRITES_UNVERIFIED -> R.string.n3_no_writes
            N3SelectionSession.Phase.CONFIRMED_UNVERIFIED -> R.string.n3_unverified
            N3SelectionSession.Phase.CONFIRMED_UNKNOWN -> R.string.n3_unknown
            N3SelectionSession.Phase.UNAVAILABLE -> R.string.n3_unavailable
        }
        findViewById<TextView>(R.id.n3_phase).setText(phaseText)
        findViewById<TextView>(R.id.n3_report).text = session.report
    }
    override fun onResume() { super.onResume(); resumed = true; handler.post(expiry); render() }
    override fun onPause() {
        resumed = false; handler.removeCallbacks(expiry)
        session.invalidate(); render()
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focused = hasFocus
        if (::session.isInitialized) {
            if (!hasFocus) session.invalidate()
            render()
        }
    }
    // No token/session in onSaveInstanceState; recreation starts a fresh IDLE session.
}
