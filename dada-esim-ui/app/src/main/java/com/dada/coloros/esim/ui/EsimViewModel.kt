package com.dada.coloros.esim.ui

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dada.coloros.esim.bridge.DadaEsimBridgeClient
import com.dada.coloros.esim.bridge.EsimProfile
import com.dada.coloros.esim.bridge.RouteState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class EsimUiState(
    val loadingInitial: Boolean = true,
    val routeState: RouteState? = null,
    val profiles: List<EsimProfile> = emptyList(),
    val busyTitle: String? = null,
    val busySubtitle: String? = null,
    val switchingRoute: Boolean = false,
    val switchingProfileIccid: String? = null,
    val bannerMessage: String? = null,
    val bannerIsError: Boolean = false,
)

class EsimViewModel(application: Application) : AndroidViewModel(application) {
    private val bridge = DadaEsimBridgeClient(application.applicationContext)

    private val _state = MutableStateFlow(EsimUiState())
    val state: StateFlow<EsimUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun clearBanner() {
        _state.update { it.copy(bannerMessage = null) }
    }

    fun refresh(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loadingInitial = true) }
            try {
                val route = bridge.getRouteState()
                val profiles = if (route.isEsim) {
                    runCatching { bridge.listProfiles() }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                _state.update {
                    it.copy(
                        loadingInitial = false,
                        routeState = route,
                        profiles = profiles,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        loadingInitial = false,
                        bannerMessage = t.message ?: "Unable to connect to OpenEUICC engine",
                        bannerIsError = true,
                    )
                }
            }
        }
    }

    fun switchRoute(toEsim: Boolean) {
        if (_state.value.switchingRoute || _state.value.busyTitle != null) return
        viewModelScope.launch {
            val targetLabel = if (toEsim) "eSIM (Embedded SIM)" else "Physical SIM 2"
            _state.update {
                it.copy(
                    switchingRoute = true,
                    busyTitle = "Switching SIM 2 to $targetLabel",
                    busySubtitle = "Executing atomic hardware route switch & 3x verification…",
                    bannerMessage = null,
                )
            }
            try {
                val res = bridge.switchSlot2Route(toEsim)
                if (res.ok) {
                    // Allow telephony framework a brief moment to settle UICC card state before listing profiles.
                    delay(1200)
                    val route = bridge.getRouteState()
                    val profiles = if (route.isEsim) {
                        runCatching { bridge.listProfiles() }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    _state.update {
                        it.copy(
                            switchingRoute = false,
                            busyTitle = null,
                            busySubtitle = null,
                            routeState = route,
                            profiles = profiles,
                            bannerMessage = "SIM 2 switched to $targetLabel",
                            bannerIsError = false,
                        )
                    }
                } else {
                    val route = runCatching { bridge.getRouteState() }.getOrNull()
                    _state.update {
                        it.copy(
                            switchingRoute = false,
                            busyTitle = null,
                            busySubtitle = null,
                            routeState = route ?: it.routeState,
                            bannerMessage = res.error ?: "Route switch status: ${res.status}",
                            bannerIsError = true,
                        )
                    }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        switchingRoute = false,
                        busyTitle = null,
                        busySubtitle = null,
                        bannerMessage = t.message ?: "Route switch failed",
                        bannerIsError = true,
                    )
                }
            }
        }
    }

    fun setProfileEnabled(profile: EsimProfile, enable: Boolean) {
        if (_state.value.busyTitle != null) return
        val name = profile.displayName.ifEmpty { profile.iccid }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    switchingProfileIccid = profile.iccid,
                    busyTitle = if (enable) "Activating $name" else "Disabling $name",
                    busySubtitle = "Updating eUICC & waiting up to ~20s for network registration…",
                    bannerMessage = null,
                )
            }
            try {
                val res = bridge.setProfileEnabled(profile.iccid, enable)
                val route = runCatching { bridge.getRouteState() }.getOrNull()
                val profiles = runCatching { bridge.listProfiles() }.getOrDefault(_state.value.profiles)
                _state.update {
                    it.copy(
                        switchingProfileIccid = null,
                        busyTitle = null,
                        busySubtitle = null,
                        routeState = route ?: it.routeState,
                        profiles = profiles,
                        bannerMessage = if (res.ok) {
                            if (enable) "$name activated" else "$name disabled"
                        } else {
                            res.error ?: "Failed to update profile"
                        },
                        bannerIsError = !res.ok,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        switchingProfileIccid = null,
                        busyTitle = null,
                        busySubtitle = null,
                        bannerMessage = t.message ?: "Profile switch failed",
                        bannerIsError = true,
                    )
                }
            }
        }
    }

    fun renameProfile(iccid: String, newNickname: String, onDone: () -> Unit) {
        if (_state.value.busyTitle != null) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busyTitle = "Renaming eSIM Profile",
                    busySubtitle = "Writing nickname to eUICC…",
                    bannerMessage = null,
                )
            }
            try {
                val res = bridge.renameProfile(iccid, newNickname)
                val profiles = runCatching { bridge.listProfiles() }.getOrDefault(_state.value.profiles)
                _state.update {
                    it.copy(
                        busyTitle = null,
                        busySubtitle = null,
                        profiles = profiles,
                        bannerMessage = if (res.ok) "Profile renamed" else (res.error ?: "Rename failed"),
                        bannerIsError = !res.ok,
                    )
                }
                if (res.ok) onDone()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        busyTitle = null,
                        busySubtitle = null,
                        bannerMessage = t.message ?: "Rename failed",
                        bannerIsError = true,
                    )
                }
            }
        }
    }

    fun downloadProfile(
        activationCode: String,
        confirmationCode: String?,
        onNeedConfirmation: () -> Unit,
        onSuccess: () -> Unit,
    ) {
        if (_state.value.busyTitle != null) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busyTitle = "Downloading eSIM",
                    busySubtitle = "Connecting to SM-DP+ server & installing profile…",
                    bannerMessage = null,
                )
            }
            try {
                val res = bridge.downloadProfile(activationCode, confirmationCode)
                if (res.confirmationCodeRequired) {
                    _state.update {
                        it.copy(
                            busyTitle = null,
                            busySubtitle = null,
                            bannerMessage = "Carrier requires a confirmation code",
                            bannerIsError = true,
                        )
                    }
                    onNeedConfirmation()
                    return@launch
                }
                val profiles = runCatching { bridge.listProfiles() }.getOrDefault(_state.value.profiles)
                _state.update {
                    it.copy(
                        busyTitle = null,
                        busySubtitle = null,
                        profiles = profiles,
                        bannerMessage = if (res.ok) "eSIM downloaded successfully" else (res.error ?: "Download failed"),
                        bannerIsError = !res.ok,
                    )
                }
                if (res.ok) onSuccess()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        busyTitle = null,
                        busySubtitle = null,
                        bannerMessage = t.message ?: "Download failed",
                        bannerIsError = true,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        bridge.close()
        super.onCleared()
    }
}
