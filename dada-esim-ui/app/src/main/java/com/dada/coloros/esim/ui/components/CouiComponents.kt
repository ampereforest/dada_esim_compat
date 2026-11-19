package com.dada.coloros.esim.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dada.coloros.esim.ui.theme.CouiTheme
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class CouiRowPosition {
    None, Head, Middle, Tail, Full;

    val extraTop: Dp get() = if (this == Head || this == Full) 2.dp else 0.dp
    val extraBottom: Dp get() = if (this == Tail || this == Full) 2.dp else 0.dp
}

fun couiRowPositionOf(index: Int, count: Int): CouiRowPosition = when {
    count <= 0 || index !in 0 until count -> CouiRowPosition.None
    count == 1 -> CouiRowPosition.Full
    index == 0 -> CouiRowPosition.Head
    index == count - 1 -> CouiRowPosition.Tail
    else -> CouiRowPosition.Middle
}

@Composable
fun CouiTopAppBar(
    title: String,
    onBack: () -> Unit,
    showBottomDivider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = CouiTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circular white elevated back button matching mobile-network-settings.jpg
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(
                        elevation = if (colors.isDark) 0.dp else 3.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x18000000),
                        spotColor = Color(0x18000000),
                    )
                    .clip(CircleShape)
                    .background(colors.surfaceContainer)
                    .border(
                        width = 0.5.dp,
                        color = if (colors.isDark) Color(0x26FFFFFF) else Color(0x10000000),
                        shape = CircleShape,
                    )
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onSurface,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
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
        if (showBottomDivider) {
            CouiDivider(horizontalInset = 0.dp)
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
    thickness: Dp = 0.33.dp,
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

@Composable
fun CouiRow(
    modifier: Modifier = Modifier,
    position: CouiRowPosition = CouiRowPosition.None,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    insidePadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
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
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = endContent,
            )
        }
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
                    color = colors.onSurfaceSecondary,
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
                if (view.isAttachedToWindow && !view.performHapticFeedback(302)) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
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
    val shape = ContinuousRoundedRectangle(22.dp)
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
