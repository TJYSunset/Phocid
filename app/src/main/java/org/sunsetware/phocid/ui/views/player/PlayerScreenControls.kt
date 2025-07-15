package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.sunsetware.phocid.R
import org.sunsetware.phocid.TNUM
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.globals.format
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.ProgressSlider
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.theme.contentColorVariant
import org.sunsetware.phocid.ui.theme.darken
import org.sunsetware.phocid.ui.views.MenuItem
import org.sunsetware.phocid.utils.roundToIntOrZero

@Immutable
sealed class PlayerScreenControls {
    @Composable
    abstract fun Compose(
        currentTrack: Track,
        currentTrackIsFavorite: Boolean,
        isPlaying: Boolean,
        repeat: Int,
        shuffle: Boolean,
        currentPosition: () -> Long,
        overflowMenuItems: List<MenuItem>,
        dragModifier: Modifier,
        containerColor: Color,
        contentColor: Color,
        colorfulBackground: Boolean,
        useCountdown: Boolean,
        onSeekToFraction: (Float) -> Unit,
        onToggleRepeat: () -> Unit,
        onSeekToPreviousSmart: () -> Unit,
        onTogglePlay: () -> Unit,
        onSeekToNext: () -> Unit,
        onToggleShuffle: () -> Unit,
        onTogglePlayQueue: () -> Unit,
        onToggleCurrentTrackIsFavorite: () -> Unit,
        onToggleUseCountdown: () -> Unit,
    )
}

val PlayerScreenControlsDefault =
    PlayerScreenControlsDefaultBase(
        fillMaxSize = false,
        verticalArrangement = Arrangement.Top,
        currentTrackInfoFirst = false,
        currentTrackInfo = {
            currentTrack: Track,
            currentTrackIsFavorite: Boolean,
            containerColor: Color,
            contentColor: Color,
            colorfulBackground: Boolean,
            overflowMenuItems: List<MenuItem>,
            onTogglePlayQueue: () -> Unit,
            onToggleCurrentTrackIsFavorite: () -> Unit ->
            Surface(
                color =
                    if (colorfulBackground) containerColor.darken()
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = contentColor,
                modifier = Modifier.clickable(onClick = onTogglePlayQueue),
            ) {
                LibraryListItemHorizontal(
                    title = currentTrack.displayTitle,
                    subtitle = currentTrack.displayArtistWithAlbum,
                    lead = {
                        IconButton(onClick = onToggleCurrentTrackIsFavorite) {
                            AnimatedContent(currentTrackIsFavorite) { animatedIsFavorite ->
                                if (animatedIsFavorite) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        Strings[R.string.player_now_playing_remove_favorites],
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.FavoriteBorder,
                                        Strings[R.string.player_now_playing_add_favorites],
                                    )
                                }
                            }
                        }
                    },
                    actions = { OverflowMenu(overflowMenuItems) },
                    marquee = true,
                )
            }
        },
    )

val PlayerScreenControlsNoQueue =
    PlayerScreenControlsDefaultBase(
        fillMaxSize = true,
        verticalArrangement = Arrangement.SpaceEvenly,
        currentTrackInfoFirst = true,
        currentTrackInfo = {
            currentTrack: Track,
            currentTrackIsFavorite: Boolean,
            containerColor: Color,
            contentColor: Color,
            colorfulBackground: Boolean,
            overflowMenuItems: List<MenuItem>,
            onTogglePlayQueue: () -> Unit,
            onToggleCurrentTrackIsFavorite: () -> Unit ->
            Surface(
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier.clickable(onClick = onTogglePlayQueue),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SingleLineText(
                            currentTrack.displayTitle,
                            style = Typography.titleLarge,
                            modifier = Modifier.weight(1f).padding(start = 16.dp).basicMarquee(),
                        )

                        IconButton(onClick = onToggleCurrentTrackIsFavorite) {
                            AnimatedContent(currentTrackIsFavorite) { animatedIsFavorite ->
                                if (animatedIsFavorite) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        Strings[R.string.player_now_playing_remove_favorites],
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.FavoriteBorder,
                                        Strings[R.string.player_now_playing_add_favorites],
                                    )
                                }
                            }
                        }
                        OverflowMenu(overflowMenuItems)
                    }
                    SingleLineText(
                        currentTrack.displayArtist,
                        style = Typography.labelLarge,
                        color = contentColorVariant(),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleLineText(
                        currentTrack.album ?: "",
                        style = Typography.labelLarge,
                        color = contentColorVariant(),
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        },
    )

@Immutable
class PlayerScreenControlsDefaultBase(
    private val fillMaxSize: Boolean,
    private val verticalArrangement: Arrangement.Vertical,
    private val currentTrackInfoFirst: Boolean,
    private val currentTrackInfo:
        @Composable
        (
            currentTrack: Track,
            currentTrackIsFavorite: Boolean,
            containerColor: Color,
            contentColor: Color,
            colorfulBackground: Boolean,
            overflowMenuItems: List<MenuItem>,
            onTogglePlayQueue: () -> Unit,
            onToggleCurrentTrackIsFavorite: () -> Unit,
        ) -> Unit,
) : PlayerScreenControls() {
    @Composable
    override fun Compose(
        currentTrack: Track,
        currentTrackIsFavorite: Boolean,
        isPlaying: Boolean,
        repeat: Int,
        shuffle: Boolean,
        currentPosition: () -> Long,
        overflowMenuItems: List<MenuItem>,
        dragModifier: Modifier,
        containerColor: Color,
        contentColor: Color,
        colorfulBackground: Boolean,
        useCountdown: Boolean,
        onSeekToFraction: (Float) -> Unit,
        onToggleRepeat: () -> Unit,
        onSeekToPreviousSmart: () -> Unit,
        onTogglePlay: () -> Unit,
        onSeekToNext: () -> Unit,
        onToggleShuffle: () -> Unit,
        onTogglePlayQueue: () -> Unit,
        onToggleCurrentTrackIsFavorite: () -> Unit,
        onToggleUseCountdown: () -> Unit,
    ) {
        val context = LocalContext.current
        var progress by remember { mutableFloatStateOf(0f) }
        val progressSeconds by
            remember(currentTrack) {
                derivedStateOf {
                    (progress * (currentTrack.duration.inWholeSeconds)).roundToIntOrZero()
                }
            }
        var isDraggingProgressSlider by remember { mutableStateOf(false) }

        // Update progress
        LaunchedEffect(currentTrack) {
            val frameTime = (1f / context.display.refreshRate).toDouble().milliseconds

            while (isActive) {
                val currentPosition = currentPosition()
                if (!isDraggingProgressSlider) {
                    progress =
                        (currentPosition.toFloat() / (currentTrack.duration.inWholeMilliseconds))
                            .takeIf { !it.isNaN() } ?: 0f
                }
                delay(frameTime)
            }
        }

        Surface(color = containerColor, contentColor = contentColor, modifier = dragModifier) {
            Column(
                modifier =
                    if (fillMaxSize) {
                        Modifier.fillMaxSize()
                    } else Modifier,
                verticalArrangement = verticalArrangement,
            ) {
                if (currentTrackInfoFirst) {
                    currentTrackInfo(
                        currentTrack,
                        currentTrackIsFavorite,
                        containerColor,
                        contentColor,
                        colorfulBackground,
                        overflowMenuItems,
                        onTogglePlayQueue,
                        onToggleCurrentTrackIsFavorite,
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SingleLineText(
                                progressSeconds.seconds.format(),
                                style = Typography.labelMedium.copy(fontFeatureSettings = TNUM),
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier.defaultMinSize(
                                        minWidth = 36.dp * LocalDensity.current.fontScale
                                    ),
                            )
                            ProgressSlider(
                                value = progress,
                                onValueChange = {
                                    isDraggingProgressSlider = true
                                    progress = it
                                },
                                onValueChangeFinished = {
                                    isDraggingProgressSlider = false
                                    onSeekToFraction(progress)
                                },
                                animate = isPlaying && !isDraggingProgressSlider,
                                modifier = Modifier.padding(horizontal = 16.dp).weight(1f),
                            )
                            SingleLineText(
                                currentTrack.duration
                                    .let {
                                        if (useCountdown) -(it - progressSeconds.seconds) else it
                                    }
                                    .format(),
                                style = Typography.labelMedium.copy(fontFeatureSettings = TNUM),
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier.defaultMinSize(
                                            minWidth = 36.dp * LocalDensity.current.fontScale
                                        )
                                        .clickable(
                                            interactionSource =
                                                remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onToggleUseCountdown,
                                        ),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onToggleRepeat) {
                                when (repeat) {
                                    Player.REPEAT_MODE_ALL ->
                                        Icon(
                                            painterResource(R.drawable.player_repeat),
                                            contentDescription =
                                                Strings[R.string.player_repeat_mode_all],
                                        )

                                    Player.REPEAT_MODE_ONE ->
                                        Icon(
                                            painterResource(R.drawable.player_repeat_one),
                                            contentDescription =
                                                Strings[R.string.player_repeat_mode_one],
                                        )

                                    else ->
                                        Icon(
                                            painterResource(R.drawable.player_repeat),
                                            contentDescription =
                                                Strings[R.string.player_repeat_mode_off],
                                            modifier = Modifier.alpha(INACTIVE_ALPHA),
                                        )
                                }
                            }
                            IconButton(onClick = onSeekToPreviousSmart) {
                                Icon(
                                    painterResource(R.drawable.player_previous),
                                    contentDescription = Strings[R.string.player_previous],
                                )
                            }
                            FloatingActionButton(
                                onClick = onTogglePlay,
                                containerColor = contentColor,
                                contentColor = containerColor,
                            ) {
                                AnimatedContent(targetState = isPlaying) { animatedIsPlaying ->
                                    if (animatedIsPlaying) {
                                        Icon(
                                            painterResource(R.drawable.player_pause),
                                            contentDescription = Strings[R.string.player_pause],
                                        )
                                    } else {
                                        Icon(
                                            painterResource(R.drawable.player_play),
                                            contentDescription = Strings[R.string.player_play],
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onSeekToNext) {
                                Icon(
                                    painterResource(R.drawable.player_next),
                                    contentDescription = Strings[R.string.player_next],
                                )
                            }

                            IconButton(onClick = onToggleShuffle) {
                                if (shuffle) {
                                    Icon(
                                        painterResource(R.drawable.player_shuffle),
                                        contentDescription = Strings[R.string.player_shuffle_on],
                                    )
                                } else {
                                    Icon(
                                        painterResource(R.drawable.player_shuffle),
                                        contentDescription = Strings[R.string.player_shuffle_off],
                                        modifier = Modifier.alpha(INACTIVE_ALPHA),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                if (!currentTrackInfoFirst) {
                    currentTrackInfo(
                        currentTrack,
                        currentTrackIsFavorite,
                        containerColor,
                        contentColor,
                        colorfulBackground,
                        overflowMenuItems,
                        onTogglePlayQueue,
                        onToggleCurrentTrackIsFavorite,
                    )
                }
            }
        }
    }
}
