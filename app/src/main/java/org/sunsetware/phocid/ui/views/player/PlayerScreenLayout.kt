package org.sunsetware.phocid.ui.views.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.ui.components.BinaryDragState

@Immutable
sealed class PlayerScreenLayout {
    /**
     * [queueDragState]'s [BinaryDragState.length] must be updated here.
     *
     * @param lyricsViewVisibility 0-0.5: [scrimLyrics]'s transition; 0.5-1: [lyricsView]'s
     *   transition
     */
    abstract fun Placeable.PlacementScope.place(
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
    )
}

enum class AspectRatio {
    LANDSCAPE,
    SQUARE,
    PORTRAIT,
}

@Stable
fun aspectRatio(width: Int, height: Int, threshold: Float): AspectRatio {
    return when {
        width.toFloat() / height >= threshold -> AspectRatio.LANDSCAPE
        height.toFloat() / width >= threshold -> AspectRatio.PORTRAIT
        else -> AspectRatio.SQUARE
    }
}

/**
 * https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views#smallest_width
 * midpoint between "large phone" and "7" tablet"
 */
val TABLET_BREAK_POINT = 540.dp
