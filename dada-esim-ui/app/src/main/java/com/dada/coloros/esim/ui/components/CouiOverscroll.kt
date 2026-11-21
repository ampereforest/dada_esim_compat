// Adapted from suqi8/coui (Apache-2.0) — exact ColorOS 17 SpringOverScroller RK4 physics
// implemented as a native Jetpack Compose OverscrollEffect + OverscrollFactory.
package com.dada.coloros.esim.ui.components

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Stable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Native Compose [OverscrollFactory] providing ColorOS 17 COUI rubber-band overscroll
 * (`COUIRecyclerView` / `SpringOverScroller` RK4 spring physics) via [LocalOverscrollFactory].
 * Note: Compose skips [OverscrollEffect] when a list's content fits entirely inside the viewport;
 * for single-screen lists use [Modifier.couiOverScrollVertical] with `LocalOverscrollFactory provides null`.
 */
object CouiOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = CouiOverscrollEffect()
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = 17
}

/**
 * ColorOS 17 vertical overscroll modifier (`suqi8/coui` [OverscrollNode] + [CouiReboundOverScroller] RK4)
 * that works identically on BOTH single-screen lists (content <= viewport) and multi-screen lists.
 */
@Stable
fun Modifier.couiOverScrollVertical(
    nestedScrollToParent: Boolean = true,
    edgeHapticEnabled: Boolean = true,
): Modifier = this
    .clipToBounds()
    .then(CouiVerticalOverscrollElement(nestedScrollToParent, edgeHapticEnabled))

private data class CouiVerticalOverscrollElement(
    val nestedScrollToParent: Boolean,
    val edgeHapticEnabled: Boolean,
) : ModifierNodeElement<CouiVerticalOverscrollNode>() {
    override fun create(): CouiVerticalOverscrollNode =
        CouiVerticalOverscrollNode(nestedScrollToParent, edgeHapticEnabled)

    override fun update(node: CouiVerticalOverscrollNode) {
        node.nestedScrollToParent = nestedScrollToParent
        node.edgeHapticEnabled = edgeHapticEnabled
        node.invalidatePlacement()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "couiOverScrollVertical"
        properties["nestedScrollToParent"] = nestedScrollToParent
        properties["edgeHapticEnabled"] = edgeHapticEnabled
    }
}

private class CouiVerticalOverscrollNode(
    var nestedScrollToParent: Boolean,
    var edgeHapticEnabled: Boolean,
) : DelegatingNode(),
    CompositionLocalConsumerModifierNode,
    LayoutModifierNode,
    NestedScrollConnection {

    private val dispatcher = NestedScrollDispatcher()
    private var animationJob: Job? = null
    private val offsetThreshold = 1f
    private var lastPlacedOffset = 0f
    private var scrollRange = 2400f
    private var dragTravelY = 0f

    private val axis = CouiOverScrollAxis(2.15f) { value ->
        val rounded = round(value)
        if (rounded != lastPlacedOffset) {
            lastPlacedOffset = rounded
            if (isAttached) invalidatePlacement()
        }
    }

    private val offset: Float get() = axis.offset

    override fun onAttach() {
        super.onAttach()
        updateScrollRange()
        delegate(nestedScrollModifierNode(this, dispatcher))
    }

    override fun onDetach() {
        super.onDetach()
        animationJob?.cancel()
        axis.reset()
    }

    private fun updateScrollRange() {
        val h = currentValueOf(LocalWindowInfo).containerSize.height
        if (h > 0) scrollRange = h.toFloat()
    }

    private fun playEdgeHaptic() {
        if (!edgeHapticEnabled || !isAttached) return
        val view = currentValueOf(LocalView)
        if (view.isAttachedToWindow) {
            view.performHapticFeedback(307)
        }
    }

    private fun startSpringBack(velocity: Float, overFling: Boolean) {
        animationJob?.cancel()
        if (!overFling && abs(offset) <= offsetThreshold) {
            axis.reset()
            return
        }
        animationJob = coroutineScope.launch {
            axis.springBack(velocity = velocity, overFling = overFling, screenSize = scrollRange)
        }
    }

    private fun unwindStaleOffset(consumedDelta: Float) {
        if (abs(offset) <= offsetThreshold || consumedDelta == 0f) return
        if (sign(consumedDelta) != sign(offset)) return
        axis.dragBack(-consumedDelta, scrollRange)
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        updateScrollRange()
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationY = round(offset)
                clip = true
            }
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!isAttached) return Offset.Zero
        if (source != NestedScrollSource.UserInput) {
            return dispatcher.dispatchPreScroll(available, source)
        }
        dragTravelY += available.y
        animationJob?.cancel()

        val parentConsumed = if (nestedScrollToParent) {
            dispatcher.dispatchPreScroll(available, source)
        } else {
            Offset.Zero
        }
        val realAvailable = available - parentConsumed
        val delta = realAvailable.y
        if (abs(offset) <= offsetThreshold || delta == 0f || sign(delta) == sign(offset)) {
            return parentConsumed
        }
        val consumed = axis.dragBack(delta, scrollRange)
        return Offset(parentConsumed.x, consumed + parentConsumed.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!isAttached) return Offset.Zero
        if (source != NestedScrollSource.UserInput) {
            if (animationJob?.isActive != true) unwindStaleOffset(consumed.y)
            return dispatcher.dispatchPostScroll(consumed, available, source)
        }
        animationJob?.cancel()
        unwindStaleOffset(consumed.y)

        val parentConsumed = if (nestedScrollToParent) {
            dispatcher.dispatchPostScroll(consumed, available, source)
        } else {
            Offset.Zero
        }
        val realAvailable = available - parentConsumed
        axis.drag(realAvailable.y, scrollRange)
        return Offset(parentConsumed.x, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!isAttached) return Velocity.Zero
        val moved = abs(dragTravelY) >= CouiOverScrollRelease.CLICK_OFFSET_THRESHOLD
        dragTravelY = 0f
        animationJob?.cancel()

        val parentConsumed = if (nestedScrollToParent) {
            dispatcher.dispatchPreFling(available)
        } else {
            Velocity.Zero
        }
        val realAvailable = available - parentConsumed
        val velocity = realAvailable.y

        if (abs(offset) > offsetThreshold) {
            if (moved) playEdgeHaptic()
            if (CouiOverScrollRelease.isReverseFling(offset, velocity)) {
                var leftover = 0f
                val job = coroutineScope.launch {
                    leftover = axis.flingBack(velocity)
                }
                animationJob = job
                job.join()
                val consumed = if (job.isCancelled) velocity else velocity - leftover
                return parentConsumed + Velocity(0f, consumed)
            }
            startSpringBack(
                velocity = CouiOverScrollRelease.springBackVelocity(offset, velocity),
                overFling = false,
            )
            return parentConsumed + Velocity(0f, realAvailable.y)
        } else if (offset != 0f) {
            axis.reset()
        }
        return parentConsumed
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!isAttached) return Velocity.Zero
        val parentConsumed = if (nestedScrollToParent) {
            dispatcher.dispatchPostFling(consumed, available)
        } else {
            Velocity.Zero
        }
        val realAvailable = available - parentConsumed
        val velocity = realAvailable.y
        if (animationJob?.isActive == true) return parentConsumed
        if (abs(offset) > offsetThreshold) {
            startSpringBack(velocity = 0f, overFling = false)
        } else if (abs(velocity) > 1f) {
            startSpringBack(velocity = velocity, overFling = true)
            playEdgeHaptic()
        } else {
            axis.reset()
            return parentConsumed
        }
        return parentConsumed + Velocity(0f, velocity)
    }
}

class CouiOverscrollEffect(
    private val edgeHapticEnabled: Boolean = true,
) : OverscrollEffect {
    private val offsetThreshold = 1f
    private var lastPlacedOffsetX = 0f
    private var lastPlacedOffsetY = 0f

    private val axisX = CouiOverScrollAxis(HORIZONTAL_SPRING_BACK_TENSION_MULTIPLE) { value ->
        val rounded = round(value)
        if (rounded != lastPlacedOffsetX) {
            lastPlacedOffsetX = rounded
            invalidateNodePlacement?.invoke()
        }
    }
    private val axisY = CouiOverScrollAxis(VERTICAL_SPRING_BACK_TENSION_MULTIPLE) { value ->
        val rounded = round(value)
        if (rounded != lastPlacedOffsetY) {
            lastPlacedOffsetY = rounded
            invalidateNodePlacement?.invoke()
        }
    }

    internal val offsetX: Float get() = axisX.offset
    internal val offsetY: Float get() = axisY.offset

    internal var invalidateNodePlacement: (() -> Unit)? = null
    internal var launchAnimation: ((suspend CoroutineScope.() -> Unit) -> Job)? = null
    internal var performEdgeHaptic: (() -> Unit)? = null
    internal var scrollRangeV = 2400f
    internal var scrollRangeH = 1080f

    private var animationJobX: Job? = null
    private var animationJobY: Job? = null
    private var dragTravelX = 0f
    private var dragTravelY = 0f

    override val isInProgress: Boolean
        get() = abs(offsetX) > offsetThreshold || abs(offsetY) > offsetThreshold

    override val node: DelegatableNode = CouiOverscrollEffectNode(this)

    internal fun resetAll() {
        animationJobX?.cancel()
        animationJobY?.cancel()
        axisX.reset()
        axisY.reset()
    }

    private fun unwindStaleOffset(axis: CouiOverScrollAxis, consumedDelta: Float, range: Float) {
        if (abs(axis.offset) <= offsetThreshold || consumedDelta == 0f) return
        if (sign(consumedDelta) != sign(axis.offset)) return
        axis.dragBack(-consumedDelta, range)
    }

    private fun startSpringBack(
        axis: CouiOverScrollAxis,
        velocity: Float,
        overFling: Boolean,
        screenSize: Float,
    ): Job? {
        if (!overFling && abs(axis.offset) <= offsetThreshold) {
            axis.reset()
            return null
        }
        return launchAnimation?.invoke {
            axis.springBack(velocity = velocity, overFling = overFling, screenSize = screenSize)
        }
    }

    private fun startReverseFling(
        axis: CouiOverScrollAxis,
        velocity: Float,
        onLeftover: (Float) -> Unit,
    ): Job? = launchAnimation?.invoke {
        onLeftover(axis.flingBack(velocity))
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source != NestedScrollSource.UserInput) {
            val consumed = performScroll(delta)
            if (animationJobY?.isActive != true) unwindStaleOffset(axisY, consumed.y, scrollRangeV)
            if (animationJobX?.isActive != true) unwindStaleOffset(axisX, consumed.x, scrollRangeH)
            return consumed
        }

        dragTravelX += delta.x
        dragTravelY += delta.y

        animationJobY?.cancel()
        animationJobX?.cancel()

        var performScrollDeltaY = delta.y
        var extraConsumedY = 0f
        if (abs(axisY.offset) > offsetThreshold && delta.y != 0f && sign(delta.y) != sign(axisY.offset)) {
            extraConsumedY = axisY.dragBack(delta.y, scrollRangeV)
            performScrollDeltaY = delta.y - extraConsumedY
        }

        var performScrollDeltaX = delta.x
        var extraConsumedX = 0f
        if (abs(axisX.offset) > offsetThreshold && delta.x != 0f && sign(delta.x) != sign(axisX.offset)) {
            extraConsumedX = axisX.dragBack(delta.x, scrollRangeH)
            performScrollDeltaX = delta.x - extraConsumedX
        }

        val adjustedDelta = Offset(performScrollDeltaX, performScrollDeltaY)
        val scrollConsumed = performScroll(adjustedDelta)
        val scrollRemaining = adjustedDelta - scrollConsumed

        unwindStaleOffset(axisY, scrollConsumed.y, scrollRangeV)
        unwindStaleOffset(axisX, scrollConsumed.x, scrollRangeH)

        if (scrollRemaining.y != 0f) axisY.drag(scrollRemaining.y, scrollRangeV)
        if (scrollRemaining.x != 0f) axisX.drag(scrollRemaining.x, scrollRangeH)

        return Offset(
            x = extraConsumedX + scrollConsumed.x + scrollRemaining.x,
            y = extraConsumedY + scrollConsumed.y + scrollRemaining.y,
        )
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val isActiveY = abs(offsetY) > offsetThreshold
        val isActiveX = abs(offsetX) > offsetThreshold
        val moved = abs(dragTravelX) >= CouiOverScrollRelease.CLICK_OFFSET_THRESHOLD ||
            abs(dragTravelY) >= CouiOverScrollRelease.CLICK_OFFSET_THRESHOLD
        dragTravelX = 0f
        dragTravelY = 0f

        animationJobY?.cancel()
        animationJobX?.cancel()

        val releasedOverScrolled = isActiveY || isActiveX
        if (releasedOverScrolled && moved && edgeHapticEnabled) {
            performEdgeHaptic?.invoke()
        }

        var performVelocity = velocity
        var reverseJobY: Job? = null
        var reverseJobX: Job? = null
        var leftoverY = 0f
        var leftoverX = 0f

        if (isActiveY) {
            if (CouiOverScrollRelease.isReverseFling(offsetY, velocity.y)) {
                reverseJobY = startReverseFling(axisY, velocity.y) { leftoverY = it }
                animationJobY = reverseJobY
            } else {
                val carried = CouiOverScrollRelease.springBackVelocity(offsetY, velocity.y)
                animationJobY = startSpringBack(axisY, velocity = carried, overFling = false, screenSize = scrollRangeV)
            }
            performVelocity = Velocity(performVelocity.x, 0f)
        } else if (offsetY != 0f) {
            axisY.reset()
        }

        if (isActiveX) {
            if (CouiOverScrollRelease.isReverseFling(offsetX, velocity.x)) {
                reverseJobX = startReverseFling(axisX, velocity.x) { leftoverX = it }
                animationJobX = reverseJobX
            } else {
                val carried = CouiOverScrollRelease.springBackVelocity(offsetX, velocity.x)
                animationJobX = startSpringBack(axisX, velocity = carried, overFling = false, screenSize = scrollRangeH)
            }
            performVelocity = Velocity(0f, performVelocity.y)
        } else if (offsetX != 0f) {
            axisX.reset()
        }

        val reversed = reverseJobY != null || reverseJobX != null
        if (reversed) {
            reverseJobY?.join()
            reverseJobX?.join()
            if (reverseJobY?.isCancelled == true || reverseJobX?.isCancelled == true) {
                return
            }
            performVelocity = Velocity(
                x = if (reverseJobX != null) leftoverX else performVelocity.x,
                y = if (reverseJobY != null) leftoverY else performVelocity.y,
            )
        }

        val consumed = performFling(performVelocity)
        val remaining = performVelocity - consumed

        var hitEdge = false
        val restingY = abs(offsetY) <= offsetThreshold && animationJobY?.isActive != true
        val restingX = abs(offsetX) <= offsetThreshold && animationJobX?.isActive != true
        if (restingY && abs(remaining.y) > MIN_OVER_FLING_VELOCITY) {
            animationJobY = startSpringBack(axisY, velocity = remaining.y, overFling = true, screenSize = scrollRangeV)
            hitEdge = true
        }
        if (restingX && abs(remaining.x) > MIN_OVER_FLING_VELOCITY) {
            animationJobX = startSpringBack(axisX, velocity = remaining.x, overFling = true, screenSize = scrollRangeH)
            hitEdge = true
        }
        if (hitEdge && (!releasedOverScrolled || reversed) && edgeHapticEnabled) {
            performEdgeHaptic?.invoke()
        }
    }

    private companion object {
        const val MIN_OVER_FLING_VELOCITY = 1f
        const val VERTICAL_SPRING_BACK_TENSION_MULTIPLE = 2.15f
        const val HORIZONTAL_SPRING_BACK_TENSION_MULTIPLE = 3.2f
    }
}

private class CouiOverscrollEffectNode(
    val effect: CouiOverscrollEffect,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    LayoutModifierNode {

    override fun onAttach() {
        super.onAttach()
        updateScrollRange()
        effect.launchAnimation = { block -> coroutineScope.launch(block = block) }
        effect.invalidateNodePlacement = { if (isAttached) invalidatePlacement() }
        effect.performEdgeHaptic = {
            if (isAttached) {
                val view = currentValueOf(LocalView)
                if (view.isAttachedToWindow) {
                    view.performHapticFeedback(307)
                }
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        effect.launchAnimation = null
        effect.invalidateNodePlacement = null
        effect.performEdgeHaptic = null
        effect.resetAll()
    }

    private fun updateScrollRange() {
        val size = currentValueOf(LocalWindowInfo).containerSize
        if (size.height > 0) effect.scrollRangeV = size.height.toFloat()
        if (size.width > 0) effect.scrollRangeH = size.width.toFloat()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        updateScrollRange()
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = round(effect.offsetX)
                translationY = round(effect.offsetY)
                clip = true
            }
        }
    }
}

private object CouiPhysicalAnimation {
    private const val OVERSCROLL_DRAG_RATIO = 0.4f
    private const val OVERFLING_MAX_DISTANCE_SCREEN_FACTOR = 0.3731f

    fun overScrollDragFactor(overScroll: Float, maxOverScroll: Float): Float {
        if (maxOverScroll <= 0f) return 0f
        return (1f - min(abs(overScroll) / maxOverScroll, 1f)) * OVERSCROLL_DRAG_RATIO
    }

    fun overFlingDisplayDistance(over: Float, screenSize: Float): Float {
        if (over == 0f || screenSize <= 0f) return 0f
        val limit = screenSize * OVERFLING_MAX_DISTANCE_SCREEN_FACTOR
        return over * limit / sqrt(over * over + limit * limit)
    }
}

private object CouiOverScrollRelease {
    const val FLING_THRESHOLD = 6000f
    const val CLICK_OFFSET_THRESHOLD = 10f

    fun isReverseFling(offset: Float, velocity: Float): Boolean =
        offset != 0f && abs(velocity) > FLING_THRESHOLD && sign(velocity) != sign(offset)

    fun springBackVelocity(offset: Float, velocity: Float): Float =
        if (abs(velocity) > FLING_THRESHOLD && sign(velocity) == sign(offset)) velocity else 0f
}

private class CouiOverScrollAxis(
    springBackTensionMultiple: Float,
    private val onOffsetChanged: (Float) -> Unit,
) {
    private val scroller = CouiReboundOverScroller(springBackTensionMultiple = springBackTensionMultiple)

    var offset = 0f
        private set(value) {
            if (field != value) {
                field = value
                onOffsetChanged(value)
            }
        }

    fun reset() {
        offset = 0f
    }

    fun drag(delta: Float, range: Float) {
        if (delta == 0f || range <= 0f) return
        offset += delta * CouiPhysicalAnimation.overScrollDragFactor(offset, range)
    }

    fun dragBack(delta: Float, range: Float): Float {
        if (delta == 0f || range <= 0f) return 0f
        val factor = CouiPhysicalAnimation.overScrollDragFactor(offset, range).coerceAtLeast(MIN_DRAG_FACTOR)
        val damped = delta * factor
        if (abs(damped) >= abs(offset)) {
            val used = -offset / factor
            offset = 0f
            return used
        }
        offset += damped
        return delta
    }

    suspend fun springBack(velocity: Float, overFling: Boolean, screenSize: Float) {
        val start = if (overFling) 0f else offset
        var startPx = start.roundToInt()
        if (startPx == 0) {
            startPx = when {
                velocity > 0f -> 1
                velocity < 0f -> -1
                else -> 0
            }
        }
        if (startPx == 0) {
            offset = 0f
            return
        }
        scroller.notifyEdgeReached(start = startPx, edge = 0, min = 0, max = 0)
        scroller.velocity = velocity.toDouble()
        var lastFrameNanos = -1L
        var running = true
        while (running && currentCoroutineContext().isActive) {
            running = withFrameNanos { frameTimeNanos ->
                val deltaSec = if (lastFrameNanos < 0L) FIRST_FRAME_INTERVAL_SEC else (frameTimeNanos - lastFrameNanos) / NANOS_PER_SECOND
                lastFrameNanos = frameTimeNanos
                val stillRunning = scroller.update(frameTimeNanos / NANOS_PER_MILLI, deltaSec)
                val position = scroller.position.toFloat()
                offset = if (overFling) CouiPhysicalAnimation.overFlingDisplayDistance(position, screenSize) else position
                stillRunning
            }
        }
        if (!running) offset = 0f
    }

    suspend fun flingBack(velocity: Float): Float {
        val start = offset
        if (start == 0f) return velocity
        if (velocity == 0f || sign(velocity) == sign(start)) {
            springBack(velocity = 0f, overFling = false, screenSize = 0f)
            return 0f
        }
        var leftover = 0f
        var crossed = false
        scroller.runFling(start = start.roundToInt(), velocity = velocity.roundToInt()) { position, speed ->
            if (position == 0.0 || sign(position) != sign(start.toDouble())) {
                offset = 0f
                leftover = speed.toFloat()
                crossed = true
                false
            } else {
                offset = position.toFloat()
                true
            }
        }
        if (!crossed) {
            springBack(velocity = 0f, overFling = false, screenSize = 0f)
            return 0f
        }
        return leftover
    }

    private companion object {
        const val MIN_DRAG_FACTOR = 0.004f
        const val FIRST_FRAME_INTERVAL_SEC = 0.008f
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

private suspend fun CouiReboundOverScroller.runFling(
    start: Int,
    velocity: Int,
    onFrame: (position: Double, velocity: Double) -> Boolean,
) {
    val firstFrameNanos = withFrameNanos { it }
    var lastFrameNanos = firstFrameNanos
    var started = false
    var running = true
    while (running) {
        running = withFrameNanos { frameTimeNanos ->
            val deltaSec = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameTimeNanos
            if (!started) {
                started = true
                val frameInterval = deltaSec.coerceIn(1f / 144f, 1f / 60f)
                fling(start, velocity, firstFrameNanos / 1_000_000L, frameInterval)
            }
            val moving = update(frameTimeNanos / 1_000_000L, deltaSec)
            onFrame(position, this.velocity) && moving
        }
    }
}

private class CouiSpringInterpolator(
    stiffness: Double,
    dampingRatio: Double,
    initialVelocity: Double,
    cutRatio: Float,
    velocityUnit: Float,
    private val isFling: Boolean,
) {
    private val unDampedAngularFreq = sqrt(if (stiffness <= 0.0) DEFAULT_STIFFNESS else stiffness)
    private val dampingRatio = if (dampingRatio <= 0.0) DEFAULT_DAMPING_RATIO else dampingRatio
    private val initialVel = min(abs(initialVelocity), VELOCITY_MAX) / (if (velocityUnit <= 0f) DEFAULT_VELOCITY_UNIT else velocityUnit)
    private val cutRatio = if (cutRatio <= 0f) 1f else cutRatio
    private val angularFreq: Double
    private val impulse: Double
    private var finalValue = -1f

    init {
        val d = this.dampingRatio
        val w = unDampedAngularFreq
        if (d < 1.0) {
            angularFreq = sqrt(1.0 - d * d) * w
            impulse = (d * w - initialVel) / angularFreq
        } else if (d == 1.0) {
            angularFreq = 0.0
            impulse = -initialVel + w
        } else {
            angularFreq = 0.0
            impulse = -initialVel + d * w
        }
    }

    private fun originInterpolation(fraction: Float): Float {
        val f = (if (fraction >= 0f) fraction else 0f) * cutRatio
        val t = f.toDouble()
        var envelope = exp(-dampingRatio * unDampedAngularFreq * t)
        val a: Double
        val b: Double
        val c: Double
        if (dampingRatio < 1.0) {
            a = cos(angularFreq * t)
            b = impulse
            c = sin(angularFreq * t)
        } else {
            if (dampingRatio == 1.0) {
                envelope = impulse * t + 1.0
                return (1.0 - envelope * exp(-f * unDampedAngularFreq)).toFloat()
            }
            b = unDampedAngularFreq * sqrt(dampingRatio * dampingRatio - 1.0)
            if (!isFling) {
                val x = fraction.toDouble() * b
                return (
                    1.0 - (envelope / b) *
                        ((-initialVel + dampingRatio * unDampedAngularFreq) * sinh(x) + b * cosh(x))
                    ).toFloat()
            }
            envelope /= b
            a = (-initialVel + unDampedAngularFreq * dampingRatio) * sinh(dampingRatio * t)
            c = cosh(dampingRatio * t)
        }
        return (1.0 - envelope * (a + b * c)).toFloat()
    }

    fun getInterpolation(fraction: Float): Float {
        if (finalValue == -1f) {
            val atEnd = originInterpolation(1f)
            finalValue = if (atEnd != 0f && !atEnd.isNaN()) atEnd else 1f
        }
        return originInterpolation(fraction) / finalValue
    }

    fun getSpeed(fraction: Float): Float {
        val t = (if (fraction >= 0f) fraction else 0f).toDouble()
        val envelope = exp(-cutRatio * dampingRatio * unDampedAngularFreq * t)
        val d = dampingRatio
        val w = unDampedAngularFreq
        val r = cutRatio.toDouble()
        val speed = if (d < 1.0) {
            abs(
                envelope * (
                    -r * (impulse * d * w + angularFreq) * sin(r * angularFreq * t) +
                        r * (impulse * angularFreq - d * w) * cos(r * angularFreq * t)
                    ),
            )
        } else if (d == 1.0) {
            abs(r * ((impulse - w) - impulse * r * w * t) * exp(-r * w * t))
        } else {
            val b = w * sqrt(d * d - 1.0)
            val k = r * ((b * b + initialVel * d * w) - d * d * w * w)
            if (isFling) {
                abs((envelope / b) * (k * sinh(r * d * t) + r * d * ((d * w - initialVel) - w * b) * cosh(r * d * t)))
            } else {
                val ft = fraction.toDouble()
                abs((envelope / b) * (k * sinh(r * b * ft) + (-r * initialVel * b) * cosh(r * b * ft)))
            }
        }
        return speed.toFloat()
    }

    private companion object {
        const val DEFAULT_DAMPING_RATIO = 1.15
        const val DEFAULT_STIFFNESS = 40.0
        const val DEFAULT_VELOCITY_UNIT = 15000f
        const val VELOCITY_MAX = 20000.0
    }
}

private class CouiReboundOverScroller(
    private val springBackTensionMultiple: Float = 0.83f,
    private val isScrollView: Boolean = true,
) {
    private class Config(friction: Double, tension: Double) {
        var friction: Double = frictionFromOrigami(friction)
        var tension: Double = tensionFromOrigami(tension)

        fun applyFriction(value: Double) {
            friction = frictionFromOrigami(value)
        }

        fun applyTension(value: Double) {
            tension = tensionFromOrigami(value)
        }

        private fun frictionFromOrigami(value: Double): Double =
            if (value.toFloat() == 0f) 0.0 else ((value.toFloat() - 8f) * 3f + 25f).toDouble()

        private fun tensionFromOrigami(value: Double): Double =
            if (value.toFloat() == 0f) 0.0 else ((value.toFloat() - 30f) * 3.62f + 194f).toDouble()
    }

    private val flingFriction = FLING_FRICTION_NORMAL
    private val flingConfig = Config(flingFriction.toDouble(), 0.0)
    private val springBackConfig = Config(SPRING_BACK_FRICTION.toDouble(), SPRING_BACK_TENSION.toDouble())
    private var config = flingConfig

    var position = 0.0
        private set
    var velocity = 0.0
    private var tempPosition = 0.0
    private var tempVelocity = 0.0
    private var startValue = 0.0
    var endValue = 0.0
        private set

    private var count = 1
    private var finished = true
    private var isSpringBack = false
    private var tensionAdjusted = false
    private var withSpring = false
    private var startTime = 0L
    private var splineMinDelta = 0.0
    private var simulateSplineDistance = 0
    private var simulateSplineDuration = 0
    private var interpolator: CouiSpringInterpolator? = null
    private var refreshTime = SOLVER_TIMESTEP_SEC

    val isAtRest: Boolean
        get() = abs(velocity) <= REST_SPEED_THRESHOLD_SPRING &&
            (abs(endValue - position) <= DISPLACEMENT_FROM_REST_THRESHOLD || config.tension == 0.0)

    fun setAtRest() {
        endValue = position
        tempPosition = position
        velocity = 0.0
        isSpringBack = false
    }

    private fun setCurrentValue(value: Double, atRest: Boolean) {
        startValue = value
        if (!isScrollView) tempPosition = 0.0
        position = value
        if (atRest) setAtRest()
    }

    private fun setEndValue(value: Double) {
        if (endValue == value) return
        startValue = position
        endValue = value
        finished = false
    }

    fun springBack(start: Int, min: Int, max: Int): Boolean {
        setCurrentValue(start.toDouble(), false)
        if (start in min..max) {
            config = Config(flingFriction.toDouble(), 0.0)
            return false
        }
        if (start > max) setEndValue(max.toDouble()) else setEndValue(min.toDouble())
        isSpringBack = true
        springBackConfig.applyFriction(SPRING_BACK_FRICTION.toDouble())
        springBackConfig.applyTension((springBackTensionMultiple * SPRING_BACK_TENSION).toDouble())
        config = springBackConfig
        return true
    }

    fun notifyEdgeReached(start: Int, edge: Int, min: Int, max: Int) {
        position = start.toDouble()
        tempPosition = edge.toDouble()
        tempVelocity = velocity
        springBack(start, min, max)
    }

    fun fling(start: Int, velocity: Int, nowMs: Long, frameIntervalSec: Float, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) {
        splineMinDelta = splineMinDelta(abs(velocity).toFloat())
        finished = false
        initFling(start, velocity, nowMs)
        interpolator = CouiSpringInterpolator(DEFAULT_STIFFNESS, DEFAULT_DAMPING_RATIO, velocity.toDouble(), 1f, DEFAULT_VELOCITY_UNIT, true)
        refreshTime = frameIntervalSec
        val durationScale = durationScale(abs(velocity).toFloat())
        val distanceScale = distanceScale(abs(velocity).toFloat())
        val edge = if (velocity >= 0) max.toDouble() else min.toDouble()
        val simulated = calculateFinalPosition(edge, false)
        val simulatedDistance = simulated[0] - start
        simulateSplineDistance = (simulatedDistance * distanceScale).toInt()
        simulateSplineDuration = (simulated[1] * durationScale).toInt()
        val curve = calculateFinalPosition(edge, true)
        withSpring = true
        endValue = curve[0].coerceIn(min, max).toDouble()
    }

    private fun initFling(start: Int, velocity: Int, nowMs: Long) {
        startTime = nowMs
        count = 1
        flingConfig.applyFriction(flingFriction.toDouble())
        flingConfig.applyTension(0.0)
        config = flingConfig
        setCurrentValue(start.toDouble(), true)
        this.velocity = velocity.toDouble()
    }

    private fun calculateFinalPosition(edge: Double, withInterpolator: Boolean): IntArray {
        val savedPosition = position.toInt()
        val savedVelocity = velocity.toInt()
        val savedCount = count
        val savedFinished = finished
        tempPosition = 0.0
        tempVelocity = 0.0
        count = 1
        var reachedEdge = false
        while (!finished) {
            val before = position
            if (withInterpolator) {
                curveStep(count * refreshTime * 1000f / simulateSplineDuration)
            } else {
                reboundStep()
            }
            val after = position
            if (abs(velocity) < SPLINE_MIN_VELOCITY) finished = true
            if (abs(after - before) < splineMinDelta) finished = true
            if (edge != -1.0 && !reachedEdge && (before - edge) * (after - edge) <= 0.0) {
                position = edge
                reachedEdge = true
            }
        }
        val finalPosition = position.toInt()
        val duration = count * refreshTime
        position = savedPosition.toDouble()
        velocity = savedVelocity.toDouble()
        count = savedCount
        tempPosition = 0.0
        tempVelocity = 0.0
        finished = savedFinished
        return intArrayOf(finalPosition, (duration * 1000f).toInt())
    }

    private fun curveStep(fraction: Float) {
        val curve = interpolator ?: return
        position = simulateSplineDistance * curve.getInterpolation(fraction) + startValue
        velocity = (curve.getSpeed(fraction) * simulateSplineDistance / simulateSplineDuration * 1000f).toDouble()
        count++
    }

    private fun reboundStep() {
        if (!isSpringBack && count == 1) {
            if (abs(velocity) > MID_VELOCITY_ADJUST_FRICTION && abs(velocity) < MAX_VELOCITY_ADJUST_FRICTION) {
                config.friction = MID_FLING_BASE_FRICTION
            } else if (abs(velocity) <= MID_VELOCITY_ADJUST_FRICTION) {
                config.friction = SLOW_FLING_BASE_FRICTION
            }
        }
        rk4(refreshTime)
        count++
    }

    private fun rk4(dt: Float) {
        val x = position
        val v = velocity
        val tension = config.tension
        val friction = config.friction
        val end = endValue
        val h = dt.toDouble()
        val a1 = (end - tempPosition) * tension
        val x2 = v * h / 2.0 + x
        val v2 = v + a1 * h / 2.0
        val a2 = (end - x2) * tension - friction * v2
        val x3 = x + v2 * h / 2.0
        val v3 = v + a2 * h / 2.0
        val a3 = (end - x3) * tension - friction * v3
        val x4 = x + v3 * h
        val v4 = v + a3 * h
        val dxdt = (v + (v2 + v3) * 2.0 + v4) * RK4_WEIGHT
        val dvdt = (a1 + (a2 + a3) * 2.0 + ((end - x4) * tension - friction * v4)) * RK4_WEIGHT
        position = x + dxdt * h
        velocity = v + h * dvdt
        tempPosition = x4
        tempVelocity = v4
    }

    fun update(nowMs: Long, frameDeltaSec: Float): Boolean {
        refreshTime = max(MIN_FRAME_INTERVAL, frameDeltaSec)
        if (refreshTime > ERROR_THRESHOLD) refreshTime = MIN_FRAME_INTERVAL
        if (isSpringBack) {
            if (isAtRest) return false
            val before = position
            val displacement = abs(endValue - position)
            if (!tensionAdjusted && displacement < SPRING_BACK_ADJUST_THRESHOLD) {
                tensionAdjusted = true
            } else if (displacement < SPRING_BACK_STOP_THRESHOLD) {
                position = endValue
                tensionAdjusted = false
                isSpringBack = false
                return false
            }
            while (true) {
                rk4(refreshTime)
                if (abs(before - position) > MIN_UPDATE_ONE_STEP || !isSpringBack || isAtRest) break
            }
            count++
            return true
        }
        if (isAtRest) return false
        val before = position
        if (!withSpring) {
            reboundStep()
        } else {
            if (simulateSplineDuration <= 0) return false
            curveStep(max(nowMs - startTime, 0L).toFloat() / simulateSplineDuration)
        }
        val after = position
        if (!withSpring && abs(after - before) < splineMinDelta) return false
        if (withSpring && abs(velocity) < SPLINE_MIN_VELOCITY) return false
        if ((before - endValue) * (after - endValue) <= 0.0) {
            position = endValue
            return false
        }
        if (velocity.isNaN() || position.isNaN()) return false
        return true
    }

    private fun splineMinDelta(velocity: Float): Double = when {
        velocity <= 5000f -> 0.2
        velocity <= 8000f -> 0.3
        else -> 0.35
    }

    private fun distanceScale(velocity: Float): Float = when {
        velocity <= 2000f -> 1f
        velocity <= 6000f -> 1f - (velocity - 2000f) / 4000f * 0.19999999f
        velocity <= 10000f -> 0.8f
        velocity > 20000f -> 0.4f
        else -> 0.8f - (velocity - 10000f) / 10000f * 0.4f
    }

    private fun durationScale(velocity: Float): Float = when {
        velocity <= 2000f -> 1.3f
        velocity <= 6000f -> 1.3f - (velocity - 2000f) / 4000f * 0.49999994f
        velocity <= 10000f -> 0.8f
        velocity > 20000f -> 0.5f
        else -> 0.8f - (velocity - 10000f) / 10000f * 0.3f
    }

    private companion object {
        const val FLING_FRICTION_NORMAL = 0.2f
        const val MID_FLING_BASE_FRICTION = 2.5
        const val SLOW_FLING_BASE_FRICTION = 2.5
        const val MID_VELOCITY_ADJUST_FRICTION = 4000.0
        const val MAX_VELOCITY_ADJUST_FRICTION = 10000.0
        const val SPRING_BACK_FRICTION = 12.19f
        const val SPRING_BACK_TENSION = 16f
        const val SPRING_BACK_ADJUST_THRESHOLD = 180.0
        const val SPRING_BACK_STOP_THRESHOLD = 0.25
        const val REST_SPEED_THRESHOLD_SPRING = 20.0
        const val DISPLACEMENT_FROM_REST_THRESHOLD = 0.05
        const val SPLINE_MIN_VELOCITY = 5.0
        const val MIN_UPDATE_ONE_STEP = 0.5
        const val RK4_WEIGHT = 0.16699999570846558
        const val DEFAULT_STIFFNESS = 40.0
        const val DEFAULT_DAMPING_RATIO = 1.15
        const val DEFAULT_VELOCITY_UNIT = 15000f
        const val MIN_FRAME_INTERVAL = 0.008f
        const val ERROR_THRESHOLD = 0.025f
        const val SOLVER_TIMESTEP_SEC = 0.016f
    }
}
