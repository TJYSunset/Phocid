package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.sunsetware.phocid.DRAG_THRESHOLD
import org.sunsetware.phocid.utils.wrap

@Stable
/** Group state updates together to prevent flickering. */
data class CarouselStateBatch<T>(
    val state: T,
    val count: Int,
    val repeat: Boolean,
    val lastIndex: Int,
    val lastIndexEquality: Any?,
    val currentIndex: Int,
    val previousIndex: Int?,
    val nextIndex: Int?,
)

/**
 * Differences between this and Google's Pager:
 * - supports repetition
 * - supports jumping
 * - has 99% less bugs than Google's POS
 *
 * @param content Index is not guaranteed to be valid; this lambda should check for validity itself.
 *
 * TODO: Animation stutters on rapid consecutive swipes
 */
@Composable
inline fun <reified T> TrackCarousel(
    state: T,
    key: Any?,
    crossinline countSelector: @DisallowComposableCalls (T) -> Int,
    crossinline indexSelector: @DisallowComposableCalls (T) -> Int,
    crossinline repeatSelector: @DisallowComposableCalls (T) -> Boolean,
    crossinline indexEqualitySelector: @DisallowComposableCalls (T, Int) -> Any?,
    tapKey: Any?,
    crossinline onTap: () -> Unit,
    crossinline onVerticalDrag: suspend PointerInputScope.() -> Unit,
    crossinline onPrevious: () -> Unit,
    crossinline onNext: () -> Unit,
    modifier: Modifier = Modifier,
    crossinline content: @Composable (T, Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var horizontalDragTotal by remember { mutableFloatStateOf(0f) }

    var stateBatch by remember {
        val count = countSelector(state)
        val repeat = repeatSelector(state)
        val index = indexSelector(state)
        mutableStateOf(
            CarouselStateBatch(
                state,
                count,
                repeat,
                index,
                indexEqualitySelector(state, index),
                index,
                (index - 1).wrap(count, repeat),
                (index + 1).wrap(count, repeat),
            )
        )
    }
    var isLastAdjacent by remember { mutableStateOf(true) }
    var lastNonadjacentDirection by remember { mutableIntStateOf(-1) }
    var width by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }

    @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
    LaunchedEffect(state, key) {
        var (
            _,
            count,
            repeat,
            lastIndex,
            lastIndexEquality,
            currentIndex,
            previousIndex,
            nextIndex) =
            stateBatch
        val index = indexSelector(state)
        count = countSelector(state)
        repeat = repeatSelector(state)
        val currentIndexEquality = indexEqualitySelector(state, index)
        if (lastIndexEquality == currentIndexEquality) {
            isLastAdjacent = true
        } else {
            when (index) {
                nextIndex -> {
                    isLastAdjacent = true
                    offset.snapTo(offset.value + 1)
                }
                previousIndex -> {
                    isLastAdjacent = true
                    offset.snapTo(offset.value - 1)
                }
                currentIndex -> {
                    isLastAdjacent = true
                }
                else -> {
                    isLastAdjacent = false
                    lastNonadjacentDirection = if (index >= currentIndex) -1 else 1
                    offset.snapTo(offset.value - lastNonadjacentDirection)
                }
            }
        }
        lastIndexEquality = currentIndexEquality
        lastIndex = currentIndex
        currentIndex = index
        previousIndex = (index - 1).wrap(count, repeat)
        nextIndex = (index + 1).wrap(count, repeat)

        stateBatch =
            CarouselStateBatch(
                state,
                count,
                repeat,
                lastIndex,
                lastIndexEquality,
                currentIndex,
                previousIndex,
                nextIndex,
            )

        offset.animateTo(0f)
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(0.dp))
                .onSizeChanged { width = it.width }
                .pointerInput(tapKey) { detectTapGestures { onTap() } }
                .pointerInput(Unit) { onVerticalDrag() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDragTotal = 0f
                            isLastAdjacent = true
                        },
                        onDragCancel = {
                            horizontalDragTotal = 0f
                            isLastAdjacent = true
                        },
                        onDragEnd = {
                            if (horizontalDragTotal >= DRAG_THRESHOLD.toPx()) {
                                onPrevious()
                            } else if (horizontalDragTotal <= -DRAG_THRESHOLD.toPx()) {
                                onNext()
                            } else {
                                coroutineScope.launch { offset.animateTo(0f) }
                            }
                            horizontalDragTotal = 0f
                            isLastAdjacent = true
                        },
                    ) { _, dragAmount ->
                        horizontalDragTotal += dragAmount
                        isLastAdjacent = true
                        coroutineScope.launch { offset.snapTo(horizontalDragTotal / width) }
                    }
                }
    ) {
        if (stateBatch.count > 0) {
            key(stateBatch.currentIndex) {
                Box(
                    modifier =
                        Modifier.absoluteOffset {
                            IntOffset(((0 + offset.value) * width).roundToInt(), 0)
                        }
                ) {
                    content(stateBatch.state, stateBatch.currentIndex)
                }
            }
            if (isLastAdjacent) {
                if (stateBatch.previousIndex != null) {
                    key(stateBatch.previousIndex!!) {
                        Box(
                            modifier =
                                Modifier.absoluteOffset {
                                    IntOffset(((-1 + offset.value) * width).roundToInt(), 0)
                                }
                        ) {
                            content(stateBatch.state, stateBatch.previousIndex!!)
                        }
                    }
                }
                if (stateBatch.nextIndex != null) {
                    key(stateBatch.nextIndex!!) {
                        Box(
                            modifier =
                                Modifier.absoluteOffset {
                                    IntOffset(((1 + offset.value) * width).roundToInt(), 0)
                                }
                        ) {
                            content(stateBatch.state, stateBatch.nextIndex!!)
                        }
                    }
                }
            } else {
                key(stateBatch.lastIndex) {
                    Box(
                        modifier =
                            Modifier.offset {
                                IntOffset(
                                    ((lastNonadjacentDirection + offset.value) * width)
                                        .roundToInt(),
                                    0,
                                )
                            }
                    ) {
                        content(stateBatch.state, stateBatch.lastIndex)
                    }
                }
            }
        }
    }
}
