package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sunsetware.phocid.R
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.LifecycleLaunchedEffect
import org.sunsetware.phocid.ui.theme.EXIT_DURATION
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.ui.theme.Typography

@Immutable
sealed class PlayerScreenLyricsView {
    @Composable
    abstract fun Compose(
        lyrics: PlayerScreenLyrics?,
        autoScroll: () -> Boolean,
        currentPosition: () -> Long,
        preferences: Preferences,
        onDisableAutoScroll: () -> Unit,
    )
}

@Immutable
object PlayerScreenLyricsViewDefault : PlayerScreenLyricsView() {
    @Composable
    override fun Compose(
        lyrics: PlayerScreenLyrics?,
        autoScroll: () -> Boolean,
        currentPosition: () -> Long,
        preferences: Preferences,
        onDisableAutoScroll: () -> Unit,
    ) {
        fun getLineIndex(): Int? {
            return (lyrics as? PlayerScreenLyrics.Synced)
                ?.value
                ?.getLineIndex((currentPosition() + EXIT_DURATION).milliseconds)
        }

        val textStyle = Typography.bodyLarge
        val padding = 16.dp
        val background = MaterialTheme.colorScheme.surfaceContainerLow
        val scrollState = rememberScrollState()
        val firstAutoPlayQueueScroll = remember { AtomicBoolean(true) }
        val linePositions = remember { AtomicReference(emptyList<Int>()) }
        var currentLineIndex by remember { mutableStateOf(null as Int?) }

        LifecycleLaunchedEffect(
            lyrics,
            minActiveState = Lifecycle.State.RESUMED,
        ) {
            currentLineIndex = null
            if (lyrics is PlayerScreenLyrics.Synced) {
                while (isActive) {
                    currentLineIndex = getLineIndex()
                    if (autoScroll()) {
                        if (!scrollState.isScrollInProgress) {
                            linePositions
                                .get()
                                .getOrNull(currentLineIndex ?: 0)
                                ?.let { it - scrollState.viewportSize / 2 }
                                ?.let {
                                    launch {
                                        if (firstAutoPlayQueueScroll.getAndSet(false)) {
                                            scrollState.scrollTo(it)
                                        } else {
                                            scrollState.animateScrollTo(it)
                                        }
                                    }
                                }
                        }
                    }
                    delay(83.milliseconds)
                }
            }
        }

        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides
                Density(density.density, density.fontScale * preferences.lyricsSizeMultiplier)
        ) {
            Box(
                modifier =
                    Modifier.drawWithCache {
                            val height = padding.toPx() * 2
                            val topBrush =
                                Brush.verticalGradient(
                                    listOf(background, Color.Transparent),
                                    startY = height / 2,
                                    endY = height,
                                )
                            val bottomBrush =
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, background),
                                    startY = size.height - height,
                                    endY = size.height - height / 2,
                                )
                            onDrawWithContent {
                                drawRect(background)
                                drawContent()
                                drawRect(
                                    topBrush,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, height),
                                )
                                drawRect(
                                    bottomBrush,
                                    topLeft = Offset(0f, size.height - height),
                                    size = Size(size.width, height),
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial)
                                onDisableAutoScroll()
                            }
                        }
                        .verticalScroll(scrollState)
                        .padding(horizontal = padding, vertical = padding * 2)
            ) {
                Layout(
                    content = {
                        when (lyrics) {
                            is PlayerScreenLyrics.Synced -> {
                                lyrics.value.lines.forEachIndexed { index, (_, text) ->
                                    val alpha by
                                        animateFloatAsState(
                                            if (index == currentLineIndex) 1f else INACTIVE_ALPHA
                                        )
                                    Text(text, style = textStyle, modifier = Modifier.alpha(alpha))
                                }
                            }
                            is PlayerScreenLyrics.Unsynced -> {
                                for (line in lyrics.value.lines()) {
                                    Text(line, style = textStyle)
                                }
                            }
                            null -> {
                                Text(Strings[R.string.player_no_lyrics], style = textStyle)
                            }
                        }
                    }
                ) { measurables, constraints ->
                    val paddingPx = (padding * 2).roundToPx()
                    val spacing = TextUnit(8f, TextUnitType.Sp).roundToPx()

                    val placeables = mutableListOf<Pair<Placeable, Int>>()
                    var cursor = 0
                    val positions = mutableListOf<Int>()
                    for (measurable in measurables) {
                        val placeable =
                            measurable.measure(Constraints(maxWidth = constraints.maxWidth))
                        placeables.add(placeable to cursor)
                        positions.add(cursor + placeable.height / 2 + paddingPx)
                        cursor += placeable.height + spacing
                    }
                    linePositions.set(positions)
                    layout(constraints.maxWidth, cursor) {
                        for ((placeable, top) in placeables) {
                            placeable.placeRelative(0, top)
                        }
                    }
                }
            }
        }
    }
}
