package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.sunsetware.phocid.ui.theme.emphasizedExit

/** Yes, [androidx.compose.material3.SwipeToDismissBox] is yet another Google's useless s***. */
enum class SwipeDirection {
    BOTH,
    START_TO_END,
    END_TO_START,
}

@Composable
inline fun <T> SwipeToDismiss(
    key: T,
    enabled: Boolean,
    swipeThreshold: Dp,
    direction: SwipeDirection = SwipeDirection.BOTH,
    crossinline onDismiss: (T) -> Unit,
    crossinline content: @Composable BoxScope.() -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val dispatcher = Dispatchers.Main.limitedParallelism(1)
    val updatedKey by rememberUpdatedState(key)
    val updatedSwipeThreshold by rememberUpdatedState(swipeThreshold)
    val layoutDirection = LocalLayoutDirection.current
    val offset = remember { Animatable(0f) }

    val directionSign = {
        if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
    }
    val isDirectionAllowed = { value: Float ->
        val sign = directionSign()
        when (direction) {
            SwipeDirection.BOTH -> true
            SwipeDirection.START_TO_END -> value * sign >= 0f
            SwipeDirection.END_TO_START -> value * sign <= 0f
        }
    }

    Box(
        modifier =
            if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {},
                            onDragCancel = {
                                coroutineScope.launch(dispatcher) {
                                    offset.animateTo(0f, emphasizedExit())
                                }
                            },
                            onDragEnd = {
                                val positionalThreshold =
                                    updatedSwipeThreshold.roundToPx().coerceAtMost(size.width / 2)
                                val value = offset.value
                                if (value.absoluteValue >= positionalThreshold &&
                                    isDirectionAllowed(value)
                                ) {
                                    coroutineScope.launch(dispatcher) {
                                        offset.animateTo(value.sign * size.width, emphasizedExit())
                                        onDismiss(updatedKey)
                                    }
                                } else {
                                    coroutineScope.launch(dispatcher) {
                                        offset.animateTo(0f, emphasizedExit())
                                    }
                                }
                            },
                        ) { change, dragAmount ->
                            coroutineScope.launch(dispatcher) {
                                val newValue = offset.value + dragAmount
                                if (isDirectionAllowed(newValue)) {
                                    offset.snapTo(newValue)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
                .graphicsLayer {
                    translationX = offset.value
                    alpha =
                        (1 - (offset.value / size.width).absoluteValue)
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0f, 1f) ?: 1f
                }
    ) {
        content()
    }
}
