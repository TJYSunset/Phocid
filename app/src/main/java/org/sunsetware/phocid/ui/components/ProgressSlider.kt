package org.sunsetware.phocid.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.cos
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.utils.coerceInOrMin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    animate: Boolean,
    squiggly: Boolean,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    color: Color = LocalContentColor.current,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val transparentColor = color.copy(alpha = color.alpha * INACTIVE_ALPHA)
    val colors =
        SliderColors(
            thumbColor = color,
            activeTrackColor = color,
            activeTickColor = color,
            inactiveTrackColor = transparentColor,
            inactiveTickColor = transparentColor,
            disabledThumbColor = color,
            disabledActiveTrackColor = color,
            disabledActiveTickColor = color,
            disabledInactiveTrackColor = color,
            disabledInactiveTickColor = color,
        )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(16.dp).negativePadding(horizontal = 22.dp),
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = { ProgressSliderThumb(interactionSource, colors) },
        track = { sliderState ->
            if (squiggly) {
                ProgressSliderTrack(
                    colors = colors,
                    sliderState = sliderState,
                    animate = animate,
                    modifier = Modifier.height(2.dp),
                )
            } else {
                ProgressSliderTrackSimple(
                    colors = colors,
                    sliderState = sliderState,
                    modifier = Modifier.height(2.dp),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressSliderTrackSimple(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
) {
    Canvas(modifier = modifier.fillMaxWidth().height(16.dp)) {
        val progress =
            with(sliderState) {
                lerpInv(
                        valueRange.start,
                        valueRange.endInclusive,
                        value.coerceInOrMin(valueRange.start, valueRange.endInclusive),
                    )
                    .coerceIn(0f, 1f)
            }
        val y = center.y
        val stroke = 2.dp.toPx()
        drawLine(
            color = colors.inactiveTrackColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = colors.inactiveTrackColor.alpha,
        )
        drawLine(
            color = colors.activeTrackColor,
            start = Offset(0f, y),
            end = Offset(size.width * progress, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = colors.activeTrackColor.alpha,
        )
    }
}

@Composable
fun ProgressSliderThumb(interactionSource: MutableInteractionSource, colors: SliderColors) {
    val interactions = remember { mutableStateListOf<Interaction>() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> interactions.add(interaction)
                is PressInteraction.Release -> interactions.remove(interaction.press)
                is PressInteraction.Cancel -> interactions.remove(interaction.press)
                is DragInteraction.Start -> interactions.add(interaction)
                is DragInteraction.Stop -> interactions.remove(interaction.start)
                is DragInteraction.Cancel -> interactions.remove(interaction.start)
            }
        }
    }

    Box(
        modifier =
            Modifier.size(48.dp)
                .indication(interactionSource = interactionSource, indication = null)
                .hoverable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(4.dp, 16.dp).background(colors.thumbColor, RoundedCornerShape(2.dp)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressSliderTrack(
    sliderState: SliderState,
    animate: Boolean,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val density = LocalDensity.current
    val progress =
        remember(density) {
            val progress = SquigglyProgress()
            // Measurements source:
            // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/SystemUI/res/values/dimens.xml;l=1207
            with(density) {
                with(progress) {
                    waveLength = 20.dp.toPx()
                    lineAmplitude = 1.5.dp.toPx()
                    phaseSpeed = 8.dp.toPx()
                    strokeWidth = 2.dp.toPx()
                }
            }
            progress
        }
    val heightFraction by animateFloatAsState(targetValue = if (animate) 1f else 0f)

    Canvas(modifier = modifier.fillMaxWidth().height(16.dp)) {
        progress.draw(
            this,
            animate,
            heightFraction,
            with(sliderState) {
                lerpInv(
                        valueRange.start,
                        valueRange.endInclusive,
                        value.coerceInOrMin(valueRange.start, valueRange.endInclusive),
                    )
                    .coerceIn(0f, 1f)
            },
            colors.activeTrackColor,
            colors.inactiveTrackColor,
        )
    }
}

private const val TWO_PI = (Math.PI * 2f).toFloat()

/**
 * [Source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/packages/SystemUI/src/com/android/systemui/media/controls/ui/SquigglyProgress.kt)
 *
 * Modified from AOSP. Original copyright notice below.
 *
 * ```text
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * ```
 */
private class SquigglyProgress {
    private val path = Path()
    private var phaseOffset = 0f
    private var lastFrameTime = -1L
    /* distance over which amplitude drops to zero, measured in wavelengths */
    private val transitionPeriods = 1.5f
    /* wave endpoint as percentage of bar when play position is zero */
    private val minWaveEndpoint = 0.2f
    /* wave endpoint as percentage of bar when play position matches wave endpoint */
    private val matchedWaveEndpoint = 0.6f
    // Horizontal length of the sine wave
    var waveLength = 0f
    // Height of each peak of the sine wave
    var lineAmplitude = 0f
    // Line speed in px per second
    var phaseSpeed = 0f
    // Progress stroke width, both for wave and solid line
    var strokeWidth = 0f

    // Enables a transition region where the amplitude
    // of the wave is reduced linearly across it.
    var transitionEnabled = true

    /**
     * Disables wave endpoint adjustments. (I don't observe that behavior on media3 notifications.)
     */
    var disableWaveEndpointAdjustments = true

    fun draw(
        drawScope: DrawScope,
        animate: Boolean,
        heightFraction: Float,
        progress: Float,
        waveColor: Color,
        lineColor: Color,
    ) {
        if (animate) {
            val now = SystemClock.uptimeMillis()
            phaseOffset += (now - lastFrameTime) / 1000f * phaseSpeed
            phaseOffset %= waveLength
            lastFrameTime = now
        }
        val totalWidth = drawScope.size.width
        val totalProgressPx = totalWidth * progress
        val waveProgressPx =
            totalWidth *
                (if (
                    !transitionEnabled ||
                        progress > matchedWaveEndpoint ||
                        disableWaveEndpointAdjustments
                )
                    progress
                else
                    lerp(
                        minWaveEndpoint,
                        matchedWaveEndpoint,
                        lerpInv(0f, matchedWaveEndpoint, progress),
                    ))
        // Build Wiggly Path
        val waveStart = -phaseOffset - waveLength / 2f
        val waveEnd = if (transitionEnabled) totalWidth else waveProgressPx
        // helper function, computes amplitude for wave segment
        val computeAmplitude: (Float, Float) -> Float = { x, sign ->
            if (transitionEnabled) {
                val length = transitionPeriods * waveLength
                val coeff =
                    lerpInv(waveProgressPx + length / 2f, waveProgressPx - length / 2f, x)
                        .coerceIn(0f, 1f)
                sign * heightFraction * lineAmplitude * coeff
            } else {
                sign * heightFraction * lineAmplitude
            }
        }
        val pathStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        // Reset path object to the start
        path.rewind()
        path.moveTo(waveStart, 0f)
        // Build the wave, incrementing by half the wavelength each time
        var currentX = waveStart
        var waveSign = 1f
        var currentAmp = computeAmplitude(currentX, waveSign)
        val dist = waveLength / 2f
        while (currentX < waveEnd) {
            waveSign = -waveSign
            val nextX = currentX + dist
            val midX = currentX + dist / 2
            val nextAmp = computeAmplitude(nextX, waveSign)
            path.cubicTo(midX, currentAmp, midX, nextAmp, nextX, nextAmp)
            currentAmp = nextAmp
            currentX = nextX
        }
        // translate to the start position of the progress bar for all draw commands
        val clipTop = lineAmplitude + strokeWidth
        drawScope.translate(0f, drawScope.center.y) {
            // Draw path up to progress position
            clipRect(0f, -1f * clipTop, totalProgressPx, clipTop) {
                drawPath(path, waveColor, waveColor.alpha, style = pathStyle)
            }
            if (transitionEnabled) {
                // If there's a smooth transition, we draw the rest of the
                // path in a different color (using different clip params)
                clipRect(totalProgressPx, -1f * clipTop, totalWidth, clipTop) {
                    drawPath(path, lineColor, lineColor.alpha, style = pathStyle)
                }
            } else {
                // No transition, just draw a flat line to the end of the region.
                // The discontinuity is hidden by the progress bar thumb shape.
                drawLine(
                    lineColor,
                    Offset(totalProgressPx, 0f),
                    Offset(totalWidth, 0f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                    alpha = lineColor.alpha,
                )
            }
            // Draw round line cap at the beginning of the wave
            val startAmp = cos(abs(waveStart) / waveLength * TWO_PI)
            drawPoints(
                listOf(Offset(0f, startAmp * lineAmplitude * heightFraction)),
                PointMode.Points,
                waveColor,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
                alpha = waveColor.alpha,
            )
        }
    }
}

private fun lerpInv(a: Float, b: Float, value: Float): Float {
    return ((value - a) / (b - a)).takeIf { it.isFinite() } ?: 0f
}
