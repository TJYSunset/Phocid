package org.sunsetware.phocid.ui.views.player

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.PlayerState
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.ui.components.Artwork
import org.sunsetware.phocid.ui.components.ArtworkImage
import org.sunsetware.phocid.ui.components.TrackCarousel
import org.sunsetware.phocid.utils.BinaryDragState
import org.sunsetware.phocid.utils.DragLock

@Immutable
sealed class PlayerScreenArtwork {
    @Composable
    abstract fun Compose(
        playerTransientStateVersion: Long,
        artworkColorPreference: ArtworkColorPreference,
        playerState: PlayerState,
        playerScreenDragState: BinaryDragState,
        dragLock: DragLock,
        onGetTrackAtIndex: (PlayerState, Int) -> Track,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
    )
}

@Immutable
object PlayerScreenArtworkDefault : PlayerScreenArtwork() {
    @Composable
    override fun Compose(
        playerTransientStateVersion: Long,
        artworkColorPreference: ArtworkColorPreference,
        playerState: PlayerState,
        playerScreenDragState: BinaryDragState,
        dragLock: DragLock,
        onGetTrackAtIndex: (PlayerState, Int) -> Track,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
    ) {
        val density = LocalDensity.current
        TrackCarousel(
            state = playerState,
            key = playerTransientStateVersion,
            countSelector = { it.actualPlayQueue.size },
            indexSelector = { it.currentIndex },
            repeatSelector = { it.repeat != Player.REPEAT_MODE_OFF },
            indexEqualitySelector = { state, index ->
                if (state.shuffle) state.unshuffledPlayQueueMapping!!.indexOf(index) else index
            },
            tapKey = Unit,
            onTap = {},
            onVerticalDrag = {
                detectVerticalDragGestures(
                    onDragStart = { playerScreenDragState.onDragStart(dragLock) },
                    onDragCancel = { playerScreenDragState.onDragEnd(dragLock, density) },
                    onDragEnd = { playerScreenDragState.onDragEnd(dragLock, density) },
                ) { _, dragAmount ->
                    playerScreenDragState.onDrag(dragLock, dragAmount)
                }
            },
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true),
        ) { state, index ->
            ArtworkImage(
                artwork = Artwork.Track(onGetTrackAtIndex(state, index)),
                artworkColorPreference = artworkColorPreference,
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxSize(),
                async = false,
            )
        }
    }
}