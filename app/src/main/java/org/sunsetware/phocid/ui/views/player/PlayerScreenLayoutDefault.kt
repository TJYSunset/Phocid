package org.sunsetware.phocid.ui.views.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.min
import org.sunsetware.phocid.ui.components.BinaryDragState
import org.sunsetware.phocid.utils.roundToIntOrZero

@Immutable
object PlayerScreenLayoutDefault : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsView: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrimQueue: Measurable,
        scrimLyrics: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
        lyricsViewVisibility: Float,
    ) {
        with(
            when (aspectRatio(width, height, 1.5f)) {
                AspectRatio.LANDSCAPE -> PlayerScreenLayoutDefaultLandscape
                AspectRatio.SQUARE ->
                    if (min(width, height) >= with(density) { TABLET_BREAK_POINT.roundToPx() })
                        PlayerScreenLayoutDefaultPortraitTablet
                    else PlayerScreenLayoutDefaultSquarePhone
                AspectRatio.PORTRAIT -> PlayerScreenLayoutDefaultPortraitPhone
            }
        ) {
            place(
                topBarStandalone,
                topBarOverlay,
                artwork,
                lyricsView,
                lyricsOverlay,
                controls,
                queue,
                scrimQueue,
                scrimLyrics,
                width,
                height,
                density,
                queueDragState,
                lyricsViewVisibility,
            )
        }
    }
}

private val TABLET_LYRICS_PADDING = 16.dp

val PlayerScreenLayoutDefaultPortraitPhone = PlayerScreenLayoutDefaultPortrait(false)
val PlayerScreenLayoutDefaultPortraitTablet = PlayerScreenLayoutDefaultPortrait(true)

@Immutable
class PlayerScreenLayoutDefaultPortrait(val tablet: Boolean) : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsView: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrimQueue: Measurable,
        scrimLyrics: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
        lyricsViewVisibility: Float,
    ) {
        val lyricsTransitionFirstHalf = (lyricsViewVisibility * 2).coerceIn(0f, 1f)
        val lyricsTransitionSecondHalf = (lyricsViewVisibility * 2 - 1).coerceIn(0f, 1f)

        val topBarStandalonePlaceable = topBarStandalone.measure(Constraints(maxWidth = width))
        val topBarOverlayPlaceable = topBarOverlay.measure(Constraints(maxWidth = width))
        val controlsPlaceable = controls.measure(Constraints(maxWidth = width))

        val topBarStandaloneHeight = if (tablet) topBarStandalonePlaceable.height else 0
        val controlsHeight = controlsPlaceable.height
        val artworkHeight =
            if (tablet)
                (height * 0.618034f - topBarStandaloneHeight)
                    .roundToIntOrZero()
                    .coerceAtLeast(0)
                    .coerceAtMost(width)
            else width
        val lyricsPadding =
            if (tablet) with(density) { TABLET_LYRICS_PADDING.roundToPx() }
            else topBarOverlayPlaceable.height
        val lyricsHeight =
            (height -
                    topBarStandaloneHeight -
                    controlsHeight -
                    with(density) { PLAYER_SCREEN_QUEUE_HEADER_HEIGHT.roundToPx() } -
                    lyricsPadding * 2)
                .coerceAtLeast(0)
        val queueArtworkCollapsedHeight =
            (height - topBarStandaloneHeight - artworkHeight - controlsHeight).coerceAtLeast(0)
        val queueLyricsCollapsedHeight =
            with(density) { PLAYER_SCREEN_QUEUE_HEADER_HEIGHT.roundToPx() }
        val lyricsOffset =
            lerp(
                (queueArtworkCollapsedHeight - queueLyricsCollapsedHeight) / 3,
                0,
                lyricsTransitionSecondHalf,
            )
        val queueCollapsedHeight =
            lerp(
                queueArtworkCollapsedHeight,
                queueLyricsCollapsedHeight,
                lyricsTransitionSecondHalf,
            )
        val contentHeight =
            (height - topBarStandaloneHeight - controlsHeight - queueCollapsedHeight).coerceAtLeast(
                0
            )
        val queueOffset =
            (queueDragState.position *
                    (height - topBarStandaloneHeight - controlsHeight - queueCollapsedHeight))
                .roundToIntOrZero()

        if (lyricsTransitionFirstHalf < 1) {
            artwork
                .measure(Constraints(maxWidth = width, maxHeight = artworkHeight))
                .placeRelative(0, topBarStandaloneHeight)
            lyricsOverlay
                .measure(Constraints(maxWidth = width, maxHeight = contentHeight))
                .placeRelative(0, topBarStandaloneHeight)
        }
        if (lyricsTransitionFirstHalf > 0) {
            scrimLyrics
                .measure(Constraints(maxWidth = width, maxHeight = contentHeight))
                .placeRelativeWithLayer(0, topBarStandaloneHeight) {
                    alpha = lyricsTransitionFirstHalf
                }
        }
        if (lyricsTransitionSecondHalf > 0) {
            lyricsView
                .measure(Constraints(maxWidth = width, maxHeight = lyricsHeight))
                .placeRelativeWithLayer(0, topBarStandaloneHeight + lyricsPadding - lyricsOffset) {
                    alpha = lyricsTransitionSecondHalf
                }
        }
        (if (tablet) topBarStandalonePlaceable else topBarOverlayPlaceable).placeRelative(0, 0)

        if (queueOffset > 0) {
            scrimQueue
                .measure(Constraints(maxWidth = width, maxHeight = height - topBarStandaloneHeight))
                .placeRelativeWithLayer(0, topBarStandaloneHeight) {
                    alpha = queueDragState.position
                }
        }

        controlsPlaceable.placeRelative(0, topBarStandaloneHeight + contentHeight - queueOffset)
        queue
            .measure(Constraints(maxWidth = width, maxHeight = queueCollapsedHeight + queueOffset))
            .placeRelative(0, (height - queueCollapsedHeight - queueOffset).coerceAtLeast(0))

        queueDragState.length =
            (height - topBarStandaloneHeight - controlsHeight - queueCollapsedHeight)
                .coerceAtLeast(0)
                .toFloat()
    }
}

@Immutable
object PlayerScreenLayoutDefaultLandscape : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsView: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrimQueue: Measurable,
        scrimLyrics: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
        lyricsViewVisibility: Float,
    ) {
        val lyricsTransitionFirstHalf = (lyricsViewVisibility * 2).coerceIn(0f, 1f)
        val lyricsTransitionSecondHalf = (lyricsViewVisibility * 2 - 1).coerceIn(0f, 1f)

        val artworkWidth = height

        val topBarOverlayPlaceable =
            topBarOverlay.measure(Constraints(maxWidth = artworkWidth, maxHeight = height))
        val controlsPlaceable =
            controls.measure(Constraints(maxWidth = width - artworkWidth, maxHeight = height))
        val queueOffset = (queueDragState.position * controlsPlaceable.height).roundToIntOrZero()

        artwork
            .measure(Constraints(maxWidth = artworkWidth, maxHeight = height))
            .placeRelative(0, 0)
        lyricsOverlay
            .measure(Constraints(maxWidth = artworkWidth, maxHeight = height))
            .placeRelative(0, 0)
        if (lyricsTransitionFirstHalf > 0) {
            scrimLyrics
                .measure(Constraints(maxWidth = artworkWidth, maxHeight = height))
                .placeRelativeWithLayer(0, 0) { alpha = lyricsTransitionFirstHalf }
        }
        if (lyricsTransitionSecondHalf >= 0.5) {
            lyricsView
                .measure(
                    Constraints(
                        maxWidth = artworkWidth,
                        maxHeight = (height - topBarOverlayPlaceable.height).coerceAtLeast(0),
                    )
                )
                .placeRelativeWithLayer(0, topBarOverlayPlaceable.height) {
                    alpha = lyricsTransitionSecondHalf
                }
        }
        topBarOverlayPlaceable.placeRelative(0, 0)

        controlsPlaceable.placeRelative(artworkWidth, 0)
        scrimQueue
            .measure(Constraints(maxWidth = width - artworkWidth, maxHeight = height))
            .placeRelativeWithLayer(artworkWidth, 0) { alpha = queueDragState.position }
        queue
            .measure(
                Constraints(
                    maxWidth = width - artworkWidth,
                    maxHeight = (height - controlsPlaceable.height + queueOffset).coerceAtLeast(0),
                )
            )
            .placeRelative(artworkWidth, controlsPlaceable.height - queueOffset)

        queueDragState.length = controlsPlaceable.height.toFloat()
    }
}

@Immutable
object PlayerScreenLayoutDefaultSquarePhone : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsView: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrimQueue: Measurable,
        scrimLyrics: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
        lyricsViewVisibility: Float,
    ) {
        val lyricsTransitionFirstHalf = (lyricsViewVisibility * 2).coerceIn(0f, 1f)
        val lyricsTransitionSecondHalf = (lyricsViewVisibility * 2 - 1).coerceIn(0f, 1f)

        val topBarPlaceable =
            topBarStandalone.measure(Constraints(maxWidth = width, maxHeight = height))
        topBarPlaceable.placeRelative(0, 0)
        val controlsPlaceable =
            controls.measure(
                Constraints(
                    maxWidth = width,
                    maxHeight = (height - topBarPlaceable.height).coerceAtLeast(0),
                )
            )
        controlsPlaceable.placeRelative(0, topBarPlaceable.height)

        val queueOffset = (queueDragState.position * controlsPlaceable.height).roundToIntOrZero()

        scrimQueue
            .measure(
                Constraints(
                    maxWidth = width,
                    maxHeight = (height - topBarPlaceable.height).coerceAtLeast(0),
                )
            )
            .placeRelativeWithLayer(0, topBarPlaceable.height) { alpha = queueDragState.position }

        queue
            .measure(
                Constraints(
                    maxWidth = width,
                    maxHeight =
                        (height - topBarPlaceable.height - controlsPlaceable.height + queueOffset)
                            .coerceAtLeast(0),
                )
            )
            .placeRelative(0, topBarPlaceable.height - queueOffset + controlsPlaceable.height)

        if (lyricsTransitionFirstHalf > 0) {
            scrimLyrics
                .measure(
                    Constraints(
                        maxWidth = width,
                        maxHeight = (height - topBarPlaceable.height).coerceAtLeast(0),
                    )
                )
                .placeRelativeWithLayer(0, topBarPlaceable.height) {
                    alpha = lyricsTransitionFirstHalf
                }
        }
        if (lyricsTransitionSecondHalf >= 0.5) {
            lyricsView
                .measure(
                    Constraints(
                        maxWidth = width,
                        maxHeight = (height - topBarPlaceable.height).coerceAtLeast(0),
                    )
                )
                .placeRelativeWithLayer(0, topBarPlaceable.height) {
                    alpha = lyricsTransitionSecondHalf
                }
        }

        queueDragState.length = controlsPlaceable.height.toFloat()
    }
}
