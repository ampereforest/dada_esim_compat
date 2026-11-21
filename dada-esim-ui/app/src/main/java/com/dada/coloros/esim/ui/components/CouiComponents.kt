package com.dada.coloros.esim.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dada.coloros.esim.ui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

enum class CouiRowPosition {
    None, Head, Middle, Tail, Full;

    val extraTop: Dp get() = if (this == Head || this == Full) 1.dp else 0.dp
    val extraBottom: Dp get() = if (this == Tail || this == Full) 1.dp else 0.dp
}

fun couiRowPositionOf(index: Int, count: Int): CouiRowPosition = when {
    count <= 0 || index !in 0 until count -> CouiRowPosition.None
    count == 1 -> CouiRowPosition.Full
    index == 0 -> CouiRowPosition.Head
    index == count - 1 -> CouiRowPosition.Tail
    else -> CouiRowPosition.Middle
}

@Composable
fun CouiBackButton(
    onClick: () -> Unit,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
) {
    val colors = CouiTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        buttonScale.animateTo(
            targetValue = if (isPressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.70f, stiffness = 600f),
        )
    }

    val isDark = colors.isDark
    val surfaceColor = if (isDark) Color(0x28FFFFFF) else Color(0xB8F6F7F9)

    val glassModifier = if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(4f.dp.toPx())
                lens(
                    refractionHeight = 8f.dp.toPx(),
                    refractionAmount = 18f.dp.toPx(),
                    depthEffect = true,
                )
            },
            highlight = {
                Highlight(
                    width = 0.75.dp,
                    alpha = if (isDark) 0.45f else 0.90f,
                    style = HighlightStyle.Default,
                )
            },
            shadow = {
                Shadow(
                    radius = 16.dp,
                    offset = DpOffset(0.dp, 4.dp),
                    color = Color.Black.copy(alpha = if (isDark) 0.32f else 0.09f),
                )
            },
            layerBlock = {
                scaleX = buttonScale.value
                scaleY = buttonScale.value
            },
            onDrawSurface = {
                drawRect(surfaceColor)
            },
        )
    } else {
        Modifier
            .graphicsLayer {
                scaleX = buttonScale.value
                scaleY = buttonScale.value
            }
            .shadow(
                elevation = if (isDark) 0.dp else 10.dp,
                shape = CircleShape,
                ambientColor = Color(0x24000000),
                spotColor = Color(0x1E000000),
            )
            .clip(CircleShape)
            .background(if (isDark) Color(0x28FFFFFF) else Color(0xCCF6F7F9))
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .then(glassModifier)
            .semantics {
                contentDescription = "Back"
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Exact stock ColorOS 17 COUI back arrow (Settings.apk res/drawable/coui_back_arrow_normal.xml):
        // viewport 24x24: M10.1,4.4 L2.5,12 M2.5,12 L10.1,19.6 M2.5,12 L21.5,12.001, strokeWidth=1.4dp
        val arrowColor = colors.onSurface
        Canvas(modifier = Modifier.size(22.dp)) {
            val sx = size.width / 24f
            val sy = size.height / 24f
            val stroke = 1.4.dp.toPx()

            drawLine(
                color = arrowColor,
                start = Offset(2.5f * sx, 12.0f * sy),
                end = Offset(21.5f * sx, 12.001f * sy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            val chevron = Path().apply {
                moveTo(10.1f * sx, 4.4f * sy)
                lineTo(2.5f * sx, 12.0f * sy)
                lineTo(10.1f * sx, 19.6f * sy)
            }
            drawPath(
                path = chevron,
                color = arrowColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// 1D separable progressive Gaussian blur AGSL shader (100% opaque replacement — zero bloom/sharp ghost).
// Blur radius itself ramps smoothly from 0px at the bottom apron edge (t=0.95) up to maxRadius at top (t=0.35).
private const val PROGRESSIVE_TOP_BLUR_AGSL = """
uniform shader content;
uniform float2 size;
uniform float pad;
uniform float maxRadius;
uniform float2 dir;

half4 main(float2 coord) {
    float t = clamp((coord.y - pad) / max(size.y, 1.0), 0.0, 1.0);
    float u = smoothstep(0.95, 0.35, t);
    float r = maxRadius * u;
    if (r < 0.35) {
        return content.eval(coord);
    }
    float2 maxC = size + float2(pad * 2.0);
    float stepPx = r / 6.0;
    half4 acc = half4(0.0);
    float wsum = 0.0;
    for (int i = -6; i <= 6; i++) {
        float fi = float(i);
        float w = exp(-0.5 * (fi * fi) / 7.5);
        float2 p = clamp(coord + dir * (fi * stepPx), float2(0.0), maxC);
        acc += content.eval(p) * half(w);
        wsum += w;
    }
    return acc / half(wsum);
}
"""

@Composable
fun CouiTopAppBar(
    title: String,
    onBack: () -> Unit,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    showBottomDivider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = CouiTheme.colors
    // Calibrated 1:1 against stock ColorOS 17 Personal hotspot (correct-blur.jpg, 1200x2670):
    // True 2-pass separable AGSL variable-radius Gaussian blur (0.dp at bottom -> 6.5.dp at top)
    // with 100% opaque replacement (zero DstIn alpha bloom, zero sharp ghost, zero bottom seam),
    // paired with the smooth bottom-to-top canvas fade overlay (#F0F1F3 light / #000000 dark).
    val topScrim = Brush.verticalGradient(
        0.00f to colors.surface.copy(alpha = 0.98f),
        0.28f to colors.surface.copy(alpha = 0.94f),
        0.52f to colors.surface.copy(alpha = 0.72f),
        0.74f to colors.surface.copy(alpha = 0.36f),
        0.88f to colors.surface.copy(alpha = 0.12f),
        1.00f to Color.Transparent,
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = {
                                val maxR = 6.5f.dp.toPx()
                                if (maxR > padding) padding = maxR
                                val p = padding
                                val w = size.width
                                val h = size.height
                                runtimeShaderEffect("CouiProgBlurH", PROGRESSIVE_TOP_BLUR_AGSL, "content") {
                                    setFloatUniform("size", w, h)
                                    setFloatUniform("pad", p)
                                    setFloatUniform("maxRadius", maxR)
                                    setFloatUniform("dir", 1f, 0f)
                                }
                                runtimeShaderEffect("CouiProgBlurV", PROGRESSIVE_TOP_BLUR_AGSL, "content") {
                                    setFloatUniform("size", w, h)
                                    setFloatUniform("pad", p)
                                    setFloatUniform("maxRadius", maxR)
                                    setFloatUniform("dir", 0f, 1f)
                                }
                            },
                            highlight = { null },
                            shadow = { null },
                            onDrawSurface = {
                                drawRect(topScrim)
                            },
                        )
                    } else {
                        Modifier.background(topScrim)
                    }
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CouiBackButton(
                    onClick = onBack,
                    backdrop = backdrop,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = CouiTheme.typography.topBarTitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (showBottomDivider) {
                CouiDivider(horizontalInset = 0.dp, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun CouiSmallTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = CouiTheme.typography.sectionTitle,
        color = CouiTheme.colors.onSurfaceSecondary,
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
            .heightIn(min = 16.dp)
            .wrapContentHeight(),
    )
}

@Composable
fun CouiCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = CouiTheme.colors.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor, shape),
        content = content,
    )
}

@Composable
fun CouiDivider(
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 16.dp,
    thickness: Dp = 1.dp,
    color: Color = CouiTheme.colors.dividerLine,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .height(thickness),
    ) {
        val px = thickness.toPx().coerceAtLeast(1f)
        drawLine(
            color = color,
            strokeWidth = px,
            start = Offset(0f, px / 2f),
            end = Offset(size.width, px / 2f),
        )
    }
}

private val RowPressSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

fun performCouiSwitchHaptic(view: android.view.View) {
    if (view.isAttachedToWindow && !view.performHapticFeedback(302)) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun CouiRow(
    modifier: Modifier = Modifier,
    position: CouiRowPosition = CouiRowPosition.None,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    insidePadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    startContent: (@Composable () -> Unit)? = null,
    endContent: (@Composable RowScope.() -> Unit)? = null,
    centerContent: @Composable ColumnScope.() -> Unit,
) {
    val colors = CouiTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tintProgress = remember { Animatable(0f) }

    LaunchedEffect(isPressed, onClick != null && enabled) {
        if (onClick != null && enabled && isPressed) {
            tintProgress.animateTo(1f, RowPressSpring)
        } else if (tintProgress.value > 0f) {
            tintProgress.animateTo(0f, RowPressSpring)
        }
    }

    val fillOverlay = if (tintProgress.value > 0f) {
        lerp(Color.Transparent, colors.surfaceContainerHigh, tintProgress.value * 0.85f)
    } else {
        Color.Transparent
    }

    val clickableMod = if (enabled && onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp + position.extraTop + position.extraBottom)
            .background(fillOverlay)
            .then(clickableMod)
            .padding(insidePadding)
            .padding(top = position.extraTop, bottom = position.extraBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startContent != null) {
            startContent()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            content = centerContent,
        )
        if (endContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = endContent,
            )
        }
    }
}

@Composable
fun CouiSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    position: CouiRowPosition = CouiRowPosition.None,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    insidePadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    centerContent: @Composable ColumnScope.() -> Unit,
) {
    val colors = CouiTheme.colors
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tintProgress = remember { Animatable(0f) }
    val interactive = enabled && !isLoading

    LaunchedEffect(isPressed, interactive) {
        if (interactive && isPressed) {
            tintProgress.animateTo(1f, RowPressSpring)
        } else if (tintProgress.value > 0f) {
            tintProgress.animateTo(0f, RowPressSpring)
        }
    }

    val fillOverlay = if (tintProgress.value > 0f) {
        lerp(Color.Transparent, colors.surfaceContainerHigh, tintProgress.value * 0.85f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp + position.extraTop + position.extraBottom)
            .background(fillOverlay)
            .toggleable(
                value = checked,
                enabled = interactive,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { v ->
                    performCouiSwitchHaptic(view)
                    onCheckedChange(v)
                },
            )
            .padding(insidePadding)
            .padding(top = position.extraTop, bottom = position.extraBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            content = centerContent,
        )
        Spacer(modifier = Modifier.width(12.dp))
        CouiSwitch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            isLoading = isLoading,
        )
    }
}

@Composable
fun CouiArrowRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    statusText: String? = null,
    position: CouiRowPosition = CouiRowPosition.Full,
    enabled: Boolean = true,
    startContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = CouiTheme.colors
    CouiRow(
        modifier = modifier,
        position = position,
        enabled = enabled,
        onClick = onClick,
        startContent = startContent,
        endContent = {
            if (!statusText.isNullOrEmpty()) {
                Text(
                    text = statusText,
                    style = CouiTheme.typography.body,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariantActions,
                modifier = Modifier.size(20.dp),
            )
        },
    ) {
        Text(
            text = title,
            style = CouiTheme.typography.rowTitle,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariantActions,
        )
        if (!summary.isNullOrEmpty()) {
            Text(
                text = summary,
                style = CouiTheme.typography.summary,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private val CouiToggleEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
private val SwitchTrackWidth = 44.dp
private val SwitchTrackHeight = 24.dp
private val SwitchThumbSize = 18.dp
private val SwitchThumbMargin = 3.dp
private val SwitchThumbEndOffset = SwitchTrackWidth - SwitchThumbSize - SwitchThumbMargin

@Composable
fun CouiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val colors = CouiTheme.colors
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val thumbOffset = remember {
        Animatable(
            if (checked) SwitchThumbEndOffset else SwitchThumbMargin,
            Dp.VectorConverter,
        )
    }
    val thumbSquash = remember { Animatable(1f) }
    var isFirst by remember { mutableStateOf(true) }

    LaunchedEffect(checked) {
        if (isFirst) {
            isFirst = false
            return@LaunchedEffect
        }
        thumbOffset.snapTo(if (checked) SwitchThumbMargin else SwitchThumbEndOffset)
        thumbSquash.snapTo(1f)
        launch {
            thumbOffset.animateTo(
                if (checked) SwitchThumbEndOffset else SwitchThumbMargin,
                tween(durationMillis = 383, easing = CouiToggleEasing),
            )
        }
        thumbSquash.animateTo(1.3f, tween(durationMillis = 133, easing = CouiToggleEasing))
        thumbSquash.animateTo(1f, tween(durationMillis = 250, easing = CouiToggleEasing))
    }

    val trackColor by animateColorAsState(
        targetValue = when {
            checked && enabled -> colors.primary
            checked && !enabled -> colors.primary.copy(alpha = 0.35f)
            !checked && enabled -> colors.secondary
            else -> colors.secondary.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 400, easing = CouiToggleEasing),
    )

    val pressOverlay by animateColorAsState(
        targetValue = if (enabled && isPressed) colors.onSurface.copy(alpha = 0.12f) else Color.Transparent,
    )

    val thumbScale = remember { Animatable(1f) }
    val loadingAlpha = remember { Animatable(0f) }
    val loadingScale = remember { Animatable(0.5f) }
    val loadingRotation = remember { Animatable(0f) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            loadingAlpha.snapTo(0f)
            loadingScale.snapTo(0.5f)
            launch { thumbScale.animateTo(0f, tween(433, easing = CouiToggleEasing)) }
            launch { loadingScale.animateTo(1f, tween(550, easing = CouiToggleEasing)) }
            launch { loadingAlpha.animateTo(1f, tween(550, easing = CouiToggleEasing)) }
            while (true) {
                loadingRotation.snapTo(0f)
                loadingRotation.animateTo(360f, tween(800, easing = LinearEasing))
            }
        } else {
            thumbScale.snapTo(1f)
            loadingAlpha.animateTo(0f, tween(100, easing = CouiToggleEasing))
        }
    }

    val interactive = enabled && !isLoading
    val toggleMod = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = interactive,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
            onValueChange = { v ->
                performCouiSwitchHaptic(view)
                currentOnCheckedChange?.invoke(v)
            },
        )
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
            if (!enabled) disabled()
        }
    }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .size(SwitchTrackWidth, SwitchTrackHeight)
            .clip(CircleShape)
            .drawBehind {
                drawRect(trackColor)
                drawRect(pressOverlay)
            }
            .then(toggleMod),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val thumbPx = SwitchThumbSize.roundToPx()
                    val stretchedPx = (thumbPx * thumbSquash.value).roundToInt()
                    val restingLeft = thumbOffset.value.roundToPx()
                    val left = if (checked) restingLeft + thumbPx - stretchedPx else restingLeft
                    IntOffset(left, 0)
                }
                .layout { measurable, _ ->
                    val thumbPx = SwitchThumbSize.roundToPx()
                    val stretchedPx = (thumbPx * thumbSquash.value).roundToInt()
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(stretchedPx, thumbPx),
                    )
                    layout(stretchedPx, thumbPx) { placeable.place(0, 0) }
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = thumbScale.value
                        scaleY = thumbScale.value
                    }
                    .then(
                        if (enabled) Modifier.shadow(2.dp, CircleShape, ambientColor = Color(0x26000000))
                        else Modifier,
                    )
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White,
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val alpha = loadingAlpha.value
                        if (alpha > 0f) {
                            val side = size.minDimension
                            val strokeWidth = side * 0.2f
                            val arcDiameter = side * 0.8f
                            val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
                            val brush = Brush.sweepGradient(
                                0f to Color.White.copy(alpha = 0f),
                                312f / 360f to Color.White.copy(alpha = alpha),
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                            scale(loadingScale.value) {
                                rotate(loadingRotation.value + 150f) {
                                    drawArc(
                                        brush = brush,
                                        startAngle = 0f,
                                        sweepAngle = 312f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = Size(arcDiameter, arcDiameter),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    )
                                }
                            }
                        }
                    },
            )
        }
    }
}

@Composable
fun CouiPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = CouiTheme.colors
    val shape = ContinuousCapsule
    val bg = when {
        primary && enabled -> colors.primary
        primary && !enabled -> colors.primary.copy(alpha = 0.35f)
        !primary && enabled -> colors.secondaryVariant
        else -> colors.secondaryVariant.copy(alpha = 0.5f)
    }
    val fg = when {
        primary && enabled -> colors.onPrimary
        primary && !enabled -> colors.onPrimary.copy(alpha = 0.65f)
        !primary && enabled -> colors.onSurface
        else -> colors.onSurfaceVariantActions
    }
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(bg, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CouiTheme.typography.button,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CouiLoadingDialog(
    title: String,
    subtitle: String? = null,
) {
    val colors = CouiTheme.colors
    val shape = ContinuousRoundedRectangle(24.dp)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceContainer, shape)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = colors.primary,
                trackColor = colors.secondary,
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CouiTheme.typography.rowTitle,
                    color = colors.onSurface,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = CouiTheme.typography.summary,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
