package com.dada.coloros.esim

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dada.coloros.esim.ui.EsimManageScreen
import com.dada.coloros.esim.ui.EsimViewModel
import com.dada.coloros.esim.ui.theme.CouiTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EsimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.state.collectAsStateWithLifecycle()
            CouiTheme {
                EsimManageScreen(
                    state = uiState,
                    onBack = { finish() },
                    onRefresh = { viewModel.refresh(showLoading = false) },
                    onSwitchRoute = { toEsim -> viewModel.switchRoute(toEsim) },
                    onSetProfileEnabled = { profile, enable ->
                        viewModel.setProfileEnabled(profile, enable)
                    },
                    onRenameProfile = { iccid, nickname, onDone ->
                        viewModel.renameProfile(iccid, nickname, onDone)
                    },
                    onDownloadProfile = { code, conf, onNeedConf, onDone ->
                        viewModel.downloadProfile(code, conf, onNeedConf, onDone)
                    },
                    onOpenFallbackOpenEuicc = { openFallbackOpenEuicc() },
                    onClearBanner = { viewModel.clearBanner() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh(showLoading = false)
    }

    private fun openFallbackOpenEuicc() {
        try {
            val fallback = Intent("android.service.euicc.action.MANAGE_EMBEDDED_SUBSCRIPTIONS")
                .setComponent(
                    ComponentName(
                        "im.angry.openeuicc",
                        "im.angry.openeuicc.ui.EuiccManageActivity",
                    )
                )
            startActivity(fallback)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to open OpenEUICC directly (${e.javaClass.simpleName}). Dial *#*#3418#*#* instead.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
