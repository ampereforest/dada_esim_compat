package com.dada.coloros.esim.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dada.coloros.esim.bridge.EsimProfile
import com.dada.coloros.esim.ui.components.CouiArrowRow
import com.dada.coloros.esim.ui.components.CouiCard
import com.dada.coloros.esim.ui.components.CouiDivider
import com.dada.coloros.esim.ui.components.CouiLoadingDialog
import com.dada.coloros.esim.ui.components.CouiPillButton
import com.dada.coloros.esim.ui.components.CouiRow
import com.dada.coloros.esim.ui.components.CouiRowPosition
import com.dada.coloros.esim.ui.components.CouiSmallTitle
import com.dada.coloros.esim.ui.components.CouiSwitch
import com.dada.coloros.esim.ui.components.CouiSwitchRow
import com.dada.coloros.esim.ui.components.CouiTopAppBar
import com.dada.coloros.esim.ui.components.couiOverScrollVertical
import com.dada.coloros.esim.ui.components.couiRowPositionOf
import com.dada.coloros.esim.ui.components.performCouiSwitchHaptic
import com.dada.coloros.esim.ui.theme.CouiTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EsimManageScreen(
    state: EsimUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSwitchRoute: (Boolean) -> Unit,
    onSetProfileEnabled: (EsimProfile, Boolean) -> Unit,
    onRenameProfile: (String, String, () -> Unit) -> Unit,
    onDownloadProfile: (String, String?, () -> Unit, () -> Unit) -> Unit,
    onOpenFallbackOpenEuicc: () -> Unit,
    onClearBanner: () -> Unit,
) {
    val colors = CouiTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var selectedProfile by remember { mutableStateOf<EsimProfile?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var addSheetMode by remember { mutableStateOf(AddSheetMode.CHOICE) }
    var manualCode by remember { mutableStateOf("") }
    var confirmationCode by remember { mutableStateOf("") }
    var needConfirmationHint by remember { mutableStateOf(false) }

    val route = state.routeState
    val isEsim = route?.isEsim == true
    val eid = route?.eid?.takeIf { it.isNotBlank() }
        ?: state.profiles.firstOrNull { it.eid.isNotBlank() }?.eid.orEmpty()
    val backdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        // Scrollable content container captured by LayerBackdrop for real-time Liquid Glass refraction
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(colors.surface),
        ) {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .couiOverScrollVertical()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = statusBarTop + 68.dp,
                        bottom = 28.dp,
                    ),
                ) {
                    if (!state.bannerMessage.isNullOrEmpty()) {
                        item(key = "banner") {
                            val bannerShape = ContinuousRoundedRectangle(16.dp)
                            val bg = if (state.bannerIsError) colors.errorContainer else colors.tertiaryContainer
                            val fg = if (state.bannerIsError) colors.error else colors.onTertiaryContainer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(bannerShape)
                                    .background(bg, bannerShape)
                                    .clickable(onClick = onClearBanner)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.bannerMessage,
                                    style = CouiTheme.typography.body,
                                    color = fg,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // 1. SIM 2 / eSIM Hardware Mode Card (clean ColorOS 17 card, no bulky icon box, no inline pills)
                    item(key = "hardware_mode") {
                        CouiCard {
                            CouiSwitchRow(
                                checked = isEsim,
                                enabled = !state.loadingInitial && route?.durablePending != true,
                                isLoading = state.switchingRoute,
                                position = if (eid.isNotEmpty()) CouiRowPosition.Head else CouiRowPosition.Full,
                                onCheckedChange = { targetEsim ->
                                    onSwitchRoute(targetEsim)
                                },
                            ) {
                                Text(
                                    text = "Use eSIM on SIM 2",
                                    style = CouiTheme.typography.rowTitle,
                                    color = colors.onSurface,
                                )
                                val sub = when {
                                    state.loadingInitial && route == null ->
                                        "Checking SIM 2 routing…"
                                    route?.durablePending == true ->
                                        "Hardware route switch pending reconciliation"
                                    isEsim ->
                                        "SIM slot 2 is routed to Embedded eSIM"
                                    else ->
                                        "SIM slot 2 is routed to Physical SIM card 2"
                                }
                                Text(
                                    text = sub,
                                    style = CouiTheme.typography.summary,
                                    color = colors.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }

                            if (eid.isNotEmpty()) {
                                CouiDivider()
                                CouiRow(
                                    position = CouiRowPosition.Tail,
                                    onClick = {
                                        performCouiSwitchHaptic(view)
                                        copyToClipboard(context, "EID", eid)
                                    },
                                    endContent = {
                                        Text(
                                            text = "Copy",
                                            style = CouiTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                                            color = colors.primary,
                                        )
                                    },
                                ) {
                                    Text(
                                        text = "eUICC ID (EID)",
                                        style = CouiTheme.typography.rowTitle,
                                        color = colors.onSurface,
                                    )
                                    Text(
                                        text = formatEid(eid),
                                        style = CouiTheme.typography.summary.copy(fontFamily = FontFamily.Monospace),
                                        color = colors.onSurfaceVariantSummary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    // 2. eSIM Profiles Card Group
                    item(key = "profiles_header") {
                        Spacer(modifier = Modifier.height(8.dp))
                        CouiSmallTitle(text = "eSIM Profiles")
                    }

                    item(key = "profiles_card") {
                        CouiCard {
                            when {
                                !isEsim -> {
                                    CouiRow(position = CouiRowPosition.Full) {
                                        Text(
                                            text = "eSIM hardware is currently inactive",
                                            style = CouiTheme.typography.rowTitle,
                                            color = colors.onSurface,
                                        )
                                        Text(
                                            text = "Turn on \"Use eSIM on SIM 2\" above to view, switch, or download eSIM profiles.",
                                            style = CouiTheme.typography.summary,
                                            color = colors.onSurfaceVariantSummary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }

                                state.profiles.isEmpty() -> {
                                    CouiRow(position = CouiRowPosition.Full) {
                                        Text(
                                            text = if (state.loadingInitial) "Loading eSIM profiles…" else "No eSIM profiles installed",
                                            style = CouiTheme.typography.rowTitle,
                                            color = colors.onSurface,
                                        )
                                        Text(
                                            text = "Tap \"Add eSIM\" below to scan a QR code or enter an activation code.",
                                            style = CouiTheme.typography.summary,
                                            color = colors.onSurfaceVariantSummary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }

                                else -> {
                                    state.profiles.forEachIndexed { index, profile ->
                                        if (index > 0) CouiDivider()
                                        val title = profile.displayName.ifEmpty {
                                            profile.providerName.ifEmpty { "eSIM Profile ${index + 1}" }
                                        }
                                        val providerSubtitle = buildString {
                                            if (profile.providerName.isNotEmpty() && profile.providerName != title) {
                                                append(profile.providerName)
                                                append(" • ")
                                            }
                                            append(formatIccid(profile.iccid))
                                        }
                                        CouiRow(
                                            position = couiRowPositionOf(index, state.profiles.size),
                                            onClick = { selectedProfile = profile },
                                            endContent = {
                                                CouiSwitch(
                                                    checked = profile.enabled,
                                                    isLoading = state.switchingProfileIccid == profile.iccid,
                                                    enabled = state.busyTitle == null,
                                                    onCheckedChange = { enable ->
                                                        onSetProfileEnabled(profile, enable)
                                                    },
                                                )
                                            },
                                        ) {
                                            Text(
                                                text = title,
                                                style = CouiTheme.typography.rowTitle,
                                                color = colors.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = providerSubtitle,
                                                style = CouiTheme.typography.summary,
                                                color = colors.onSurfaceVariantSummary,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Add eSIM Card Row (clean ColorOS 17 compact card without icon box)
                    item(key = "add_esim_card") {
                        Spacer(modifier = Modifier.height(12.dp))
                        CouiCard {
                            CouiArrowRow(
                                title = "Add eSIM",
                                summary = if (isEsim) {
                                    "Scan a carrier QR code or enter activation code"
                                } else {
                                    "Switch SIM 2 to eSIM to download a new profile"
                                },
                                enabled = isEsim && state.busyTitle == null,
                                position = CouiRowPosition.Full,
                                onClick = {
                                    addSheetMode = AddSheetMode.CHOICE
                                    manualCode = ""
                                    confirmationCode = ""
                                    needConfirmationHint = false
                                    showAddSheet = true
                                },
                            )
                        }
                    }

                    // 4. Advanced / Fallback Card
                    item(key = "fallback_card") {
                        Spacer(modifier = Modifier.height(8.dp))
                        CouiSmallTitle(text = "Advanced")
                        CouiCard {
                            CouiArrowRow(
                                title = "Open Advanced LPAC UI (OpenEUICC)",
                                summary = "Direct fallback engine • Dial *#*#3418#*#* anytime",
                                position = CouiRowPosition.Full,
                                onClick = onOpenFallbackOpenEuicc,
                            )
                        }
                    }
                }
            }
        }

        // Sibling Floating ColorOS 17 Liquid Glass Top App Bar (matches Personal hotspot 1:1, no right clutter)
        CouiTopAppBar(
            title = "Manage eSIM",
            onBack = onBack,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
            showBottomDivider = false,
        )
    }

    // Profile Detail & Rename Bottom Sheet (Delete omitted per safety rules)
    selectedProfile?.let { profile ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var nicknameInput by remember(profile.iccid) {
            mutableStateOf(profile.displayName)
        }
        ModalBottomSheet(
            onDismissRequest = { selectedProfile = null },
            sheetState = sheetState,
            containerColor = colors.surfaceContainer,
            scrimColor = colors.windowDimming,
            shape = ContinuousRoundedRectangle(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = profile.displayName.ifEmpty { "eSIM Profile" },
                    style = CouiTheme.typography.topBarTitle,
                    color = colors.onSurface,
                )
                if (profile.providerName.isNotEmpty()) {
                    Text(
                        text = profile.providerName,
                        style = CouiTheme.typography.summary,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(14.dp))
                        .background(colors.surface)
                        .clickable {
                            performCouiSwitchHaptic(view)
                            copyToClipboard(context, "ICCID", profile.iccid)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ICCID",
                            style = CouiTheme.typography.sectionTitle,
                            color = colors.onSurfaceSecondary,
                        )
                        Text(
                            text = formatIccid(profile.iccid),
                            style = CouiTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                            color = colors.onSurface,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy ICCID",
                        tint = colors.onSurfaceSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { if (it.length <= 63) nicknameInput = it },
                    label = { Text("Profile nickname") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.dividerLine,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedLabelColor = colors.primary,
                        unfocusedLabelColor = colors.onSurfaceSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CouiPillButton(
                        text = "Cancel",
                        primary = false,
                        onClick = { selectedProfile = null },
                        modifier = Modifier.weight(1f),
                    )
                    CouiPillButton(
                        text = "Save",
                        primary = true,
                        enabled = nicknameInput.isNotBlank(),
                        onClick = {
                            onRenameProfile(profile.iccid, nicknameInput.trim()) {
                                selectedProfile = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Add eSIM Bottom Sheet (QR Scan + Manual LPA Entry)
    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            containerColor = colors.surfaceContainer,
            scrimColor = colors.windowDimming,
            shape = ContinuousRoundedRectangle(topStart = 28.dp, topEnd = 28.dp),
        ) {
            when (addSheetMode) {
                AddSheetMode.CHOICE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Add eSIM",
                            style = CouiTheme.typography.topBarTitle,
                            color = colors.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose how to install your carrier eSIM profile",
                            style = CouiTheme.typography.summary,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        CouiCard(backgroundColor = colors.surface) {
                            CouiArrowRow(
                                title = "Scan QR Code",
                                summary = "Use the camera to scan a carrier QR code",
                                position = CouiRowPosition.Head,
                                startContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                                onClick = { addSheetMode = AddSheetMode.QR },
                            )
                            CouiDivider()
                            CouiArrowRow(
                                title = "Enter Activation Code Manually",
                                summary = "Paste or type an LPA:1\$... activation string",
                                position = CouiRowPosition.Tail,
                                startContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.SimCard,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                                onClick = { addSheetMode = AddSheetMode.MANUAL },
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                AddSheetMode.QR -> {
                    Box(modifier = Modifier.navigationBarsPadding()) {
                        QrScannerPanel(
                            onScanned = { lpaCode ->
                                manualCode = lpaCode
                                addSheetMode = AddSheetMode.MANUAL
                            },
                            onCancel = { addSheetMode = AddSheetMode.CHOICE },
                        )
                    }
                }

                AddSheetMode.MANUAL -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Activation Code",
                            style = CouiTheme.typography.topBarTitle,
                            color = colors.onSurface,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualCode,
                            onValueChange = { manualCode = it },
                            label = { Text("Activation code (LPA:1\$...)") },
                            placeholder = { Text("LPA:1\$smdp.example.com\$MATCHING_ID") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.dividerLine,
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface,
                                focusedLabelColor = colors.primary,
                                unfocusedLabelColor = colors.onSurfaceSecondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = confirmationCode,
                            onValueChange = { confirmationCode = it },
                            label = {
                                Text(
                                    if (needConfirmationHint) "Confirmation code (required)"
                                    else "Confirmation code (optional)"
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = colors.dividerLine,
                                focusedTextColor = colors.onSurface,
                                unfocusedTextColor = colors.onSurface,
                                focusedLabelColor = colors.primary,
                                unfocusedLabelColor = colors.onSurfaceSecondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CouiPillButton(
                                text = "Back",
                                primary = false,
                                onClick = { addSheetMode = AddSheetMode.CHOICE },
                                modifier = Modifier.weight(1f),
                            )
                            CouiPillButton(
                                text = "Download eSIM",
                                primary = true,
                                enabled = manualCode.trim().startsWith("LPA:", ignoreCase = true),
                                onClick = {
                                    onDownloadProfile(
                                        manualCode.trim(),
                                        confirmationCode.trim().ifEmpty { null },
                                        { needConfirmationHint = true },
                                        { showAddSheet = false },
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Animated ColorOS Loading Dialog during Route Switch / Profile Switch / Download
    state.busyTitle?.let { title ->
        CouiLoadingDialog(
            title = title,
            subtitle = state.busySubtitle,
        )
    }
}

private enum class AddSheetMode {
    CHOICE, QR, MANUAL
}

private fun formatIccid(iccid: String): String =
    iccid.chunked(4).joinToString(" ")

private fun formatEid(eid: String): String =
    eid.chunked(4).joinToString(" ")

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
