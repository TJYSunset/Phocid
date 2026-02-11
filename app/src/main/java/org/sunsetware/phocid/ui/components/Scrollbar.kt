@file:OptIn(ExperimentalComposeUiApi::class)

package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sunsetware.phocid.TNUM
import org.sunsetware.phocid.ui.theme.EXIT_DURATION
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.emphasized
import org.sunsetware.phocid.utils.coerceInOrMin
import org.sunsetware.phocid.utils.roundToIntOrZero

val SCROLLBAR_DEFAULT_WIDTH = 4.dp
val SCROLLBAR_DEFAULT_COLOR
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val SCROLLBAR_MIN_SIZE = 48.dp
val SCROLLBAR_INTERACTIVE_WIDTH = 24.dp
const val SCROLLBAR_MIN_SIZE_MAX_FRACTION = 0.5f
val scrollbarEnter = emphasized<Float>(50)
val scrollbarExit = emphasized<Float>(EXIT_DURATION, 1000)

fun adjustThumbOffsets(
    thumbStart: Float,
    thumbEnd: Float,
    height: Float,
    density: Float,
): Pair<Float, Float> {
    val minSize =
        (SCROLLBAR_MIN_SIZE.value * density / height).coerceIn(0f, SCROLLBAR_MIN_SIZE_MAX_FRACTION)

    var start = thumbStart.coerceIn(0f, 1f)
    var end = thumbEnd.coerceIn(0f, 1f)
    if (end - start < minSize) {
        val progress = start / (1 - (end - start))
        start = progress * (1 - minSize)
        end = start + minSize
    }

    return start to end
}

fun DrawScope.drawThumb(width: Dp, color: Color, alpha: Float, start: Float, end: Float) {
    val widthPx = width.toPx()
    val cornerRadius = widthPx / 2

    drawRoundRect(
        color = color,
        topLeft =
            Offset(
                if (layoutDirection == LayoutDirection.Ltr) size.width - widthPx else 0f,
                start * size.height,
            ),
        size = Size(width.toPx(), (end - start) * size.height),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        alpha = alpha,
    )
}

private const val sqrt2 = 1.414213562373095048801688724f
private const val cot225 = 2.41421356237f

fun DrawScope.drawHint(
    textMeasurer: TextMeasurer,
    thumbWidth: Dp,
    color: Color,
    alpha: Float,
    hint: String,
    start: Float,
) {
    val padding = 16.dp.toPx()
    val marginY = 16.dp.toPx()
    val marginX = marginY + thumbWidth.toPx()
    val style =
        Typography.headlineLarge.let {
            it.copy(
                fontFeatureSettings = TNUM,
                lineHeight =
                    TextUnit(it.fontSize.value + (padding * 2).toSp().value, TextUnitType.Sp),
                lineHeightStyle =
                    LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
        }
    val text = textMeasurer.measure(hint, style, maxLines = 1)
    val height = text.size.height.toFloat()
    val width = (text.size.width + padding * 2).takeIf { it >= height * 1.1f } ?: height
    val offsetY =
        (start * size.height - height / 2 - marginY).coerceInOrMin(
            0f,
            size.height - height - marginY * 2,
        )
    val r = 8.dp.toPx()
    val r2 = 16.dp.toPx()
    val c = height + (cot225 - 1) * 2 * r2
    val a = c / sqrt2
    val b = (a - cot225 * r2 - r) / sqrt2

    val ltr = layoutDirection == LayoutDirection.Ltr
    val direction = if (ltr) 1 else -1
    val origin = if (ltr) size.width else 0f
    val offset =
        Offset(
            origin +
                direction * (-marginX - c / 2 + r2 * 2 + (sqrt2 - 1) * r - width) +
                (if (ltr) 0f else -width),
            marginY + offsetY,
        )

    val triangle = Path()
    triangle.moveTo(
        origin + direction * (-marginX - (1 - 1 / sqrt2) * r),
        marginY + c / 2 - (cot225 - 1) * r2 - (sqrt2 / 2) * r + offsetY,
    )
    triangle.relativeLineTo(0f, sqrt2 * r)
    triangle.relativeLineTo(direction * -b, b)
    triangle.relativeLineTo(direction * -(1 + 1 / sqrt2) * r2, -1 / sqrt2 * r2)
    triangle.relativeLineTo(0f, -(c - cot225 * 2 * r2))
    triangle.relativeLineTo(direction * (1 + 1 / sqrt2) * r2, -1 / sqrt2 * r2)
    triangle.close()

    drawIntoCanvas { canvas ->
        canvas.withSaveLayer(size.toRect(), Paint().apply { this.alpha = alpha }) {
            val paint = Paint().apply { this.color = color }
            canvas.drawPath(triangle, paint)
            canvas.drawCircle(
                Offset(
                    origin + direction * (-marginX - r),
                    marginY + c / 2 - (cot225 - 1) * r2 + offsetY,
                ),
                r,
                paint,
            )
            canvas.drawRoundRect(
                offset.x,
                offset.y,
                offset.x + width,
                offset.y + height,
                r2,
                r2,
                paint,
            )
        }
    }
    drawText(text, color.contentColor(), offset + Offset((width - text.size.width) / 2, 0f), alpha)
}

@Composable
inline fun ScrollbarThumb(
    width: Dp,
    color: Color,
    alwaysShowHintOnScroll: Boolean,
    crossinline alpha: () -> Float,
    crossinline thumbRange: () -> Pair<Float, Float>,
    crossinline totalItemsCount: () -> Int,
    crossinline onRequestScrollToItem: (Int) -> Unit,
    crossinline onSetIsThumbDragging: (Boolean) -> Unit,
    crossinline hint: () -> String?,
    crossinline content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    var isThumbDragging by remember { mutableStateOf(false) }
    val independentHintAlpha = animateFloatAsState(if (isThumbDragging) 1f else 0f)
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier =
            Modifier.drawWithContent {
                    drawContent()
                    val (start, end) = thumbRange()
                    drawThumb(width, color, alpha(), start, end)
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val (start, end) = thumbRange()
                        val isXInRange = {
                            if (layoutDirection == LayoutDirection.Ltr) {
                                down.position.x >= size.width - SCROLLBAR_INTERACTIVE_WIDTH.toPx()
                            } else {
                                down.position.x <= SCROLLBAR_INTERACTIVE_WIDTH.toPx()
                            }
                        }
                        val isYInRange = {
                            val relativeY = down.position.y / size.height
                            relativeY in start..end
                        }
                        if (alpha() > 0 && isXInRange() && isYInRange()) {
                            isThumbDragging = true
                            onSetIsThumbDragging(true)
                            val yOffset = start * size.height - down.position.y
                            down.consume()

                            outer@ while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                for (change in event.changes) {
                                    change.consume()
                                    if (!change.pressed) break@outer

                                    val totalItemsCount = totalItemsCount()
                                    onRequestScrollToItem(
                                        ((change.position.y + yOffset) / size.height *
                                                totalItemsCount)
                                            .roundToIntOrZero()
                                            .coerceAtMost(totalItemsCount - 1)
                                            .coerceAtLeast(0)
                                    )
                                }
                            }
                        }
                        isThumbDragging = false
                        onSetIsThumbDragging(false)
                    }
                }
    ) {
        content()
        Box(
            modifier =
                Modifier.drawBehind {
                        val hint = hint()
                        val hintAlpha =
                            if (alwaysShowHintOnScroll) alpha() else independentHintAlpha.value
                        if (hintAlpha > 0 && hint != null) {
                            drawHint(
                                textMeasurer,
                                width,
                                color,
                                hintAlpha,
                                hint,
                                thumbRange().first,
                            )
                        }
                    }
                    .fillMaxSize()
        )
    }
}

@Composable
inline fun Scrollbar(
    state: LazyListState,
    crossinline hint: (Int) -> String?,
    alwaysShowHintOnScroll: Boolean,
    alwaysVisible: Boolean = false,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
    noinline content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbRange by
        remember(state) {
            derivedStateOf {
                adjustThumbOffsets(
                    state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                        (it.index - it.offset.toFloat() / it.size) /
                            state.layoutInfo.totalItemsCount
                    } ?: 0f,
                    state.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                        (it.index +
                            (state.layoutInfo.viewportEndOffset - it.offset).toFloat() / it.size) /
                            state.layoutInfo.totalItemsCount
                    } ?: 1f,
                    state.layoutInfo.viewportSize.height.toFloat(),
                    density.density,
                )
            }
        }
    val totalItemsCount by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val isScrollbarDragging = remember { AtomicBoolean(false) }

    val alpha = remember { Animatable(0f) }
    LaunchedEffect(alwaysVisible) {
        if (alwaysVisible) {
            alpha.snapTo(1f)
        } else {
            // Reading state.isScrollInProgress outside of LaunchedEffect will trigger a recomposition
            while (isActive) {
                val isScrolling =
                    (thumbRange.first > 0 || thumbRange.second < 1) &&
                        (state.isScrollInProgress || isScrollbarDragging.get())
                if (alpha.targetValue != 1f && isScrolling) {
                    coroutineScope.launch { alpha.animateTo(1f, scrollbarEnter) }
                } else if (alpha.targetValue != 0f && !isScrolling) {
                    coroutineScope.launch { alpha.animateTo(0f, scrollbarExit) }
                }
                delay(17.milliseconds)
            }
        }
    }

    ScrollbarThumb(
        width,
        color,
        alwaysShowHintOnScroll,
        { if (alwaysVisible) 1f else alpha.value },
        { thumbRange },
        { totalItemsCount },
        { state.requestScrollToItem(it) },
        { isScrollbarDragging.set(it) },
        {
            state.layoutInfo.visibleItemsInfo
                .firstOrNull { it.offset > -it.size / 2 }
                ?.index
                ?.let { hint(it) }
        },
        content,
    )
}

@Composable
inline fun Scrollbar(
    state: LazyGridState,
    crossinline hint: (Int) -> String?,
    alwaysShowHintOnScroll: Boolean,
    alwaysVisible: Boolean = false,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
    noinline content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val itemsPerRow by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo
                    .firstOrNull()
                    ?.size
                    ?.width
                    ?.let { state.layoutInfo.viewportSize.width.toFloat() / it }
                    ?.roundToIntOrZero()
                    ?.coerceAtLeast(1) ?: 1
            }
        }
    val density = LocalDensity.current
    val thumbRange by
        remember(state) {
            derivedStateOf {
                adjustThumbOffsets(
                    state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                        (ceil(it.index.toFloat() / itemsPerRow) -
                            it.offset.y.toFloat() / it.size.height) /
                            ceil(state.layoutInfo.totalItemsCount.toFloat() / itemsPerRow)
                    } ?: 0f,
                    state.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                        (ceil(it.index.toFloat() / itemsPerRow) +
                            (state.layoutInfo.viewportEndOffset - it.offset.y).toFloat() /
                                it.size.height) /
                            ceil(state.layoutInfo.totalItemsCount.toFloat() / itemsPerRow)
                    } ?: 1f,
                    state.layoutInfo.viewportSize.height.toFloat(),
                    density.density,
                )
            }
        }
    val totalItemsCount by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val isScrollbarDragging = remember { AtomicBoolean(false) }

    val alpha = remember { Animatable(0f) }
    LaunchedEffect(alwaysVisible) {
        if (alwaysVisible) {
            alpha.snapTo(1f)
        } else {
            // Reading state.isScrollInProgress outside of LaunchedEffect will trigger a recomposition
            while (isActive) {
                val isScrolling =
                    (thumbRange.first > 0 || thumbRange.second < 1) &&
                        (state.isScrollInProgress || isScrollbarDragging.get())
                if (alpha.targetValue != 1f && isScrolling) {
                    coroutineScope.launch { alpha.animateTo(1f, scrollbarEnter) }
                } else if (alpha.targetValue != 0f && !isScrolling) {
                    coroutineScope.launch { alpha.animateTo(0f, scrollbarExit) }
                }
                delay(17.milliseconds)
            }
        }
    }

    ScrollbarThumb(
        width,
        color,
        alwaysShowHintOnScroll,
        { if (alwaysVisible) 1f else alpha.value },
        { thumbRange },
        { totalItemsCount },
        { state.requestScrollToItem(it) },
        { isScrollbarDragging.set(it) },
        {
            state.layoutInfo.visibleItemsInfo
                .firstOrNull { it.offset.y > -it.size.height / 2 }
                ?.index
                ?.let { hint(it) }
        },
        content,
    )
}
