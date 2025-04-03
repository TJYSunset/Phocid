package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.format
import org.sunsetware.phocid.ui.components.LibraryListHeader
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.Scrollbar
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.darken
import org.sunsetware.phocid.ui.views.MenuItem
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.sumOfDuration
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Immutable
sealed class PlayerScreenQueue {
    @Composable
    abstract fun Compose(
        playQueue: List<Pair<Any, Track>>,
        currentTrackIndex: Int,
        lazyListState: LazyListState,
        trackOverflowMenuItems: (Track, Int) -> List<MenuItem>,
        dragModifier: Modifier,
        nestedScrollConnection: NestedScrollConnection,
        containerColor: Color,
        contentColor: Color,
        onTogglePlayQueue: () -> Unit,
        onMoveTrack: (Int, Int) -> Unit,
        onSeekTo: (Int) -> Unit,
    )
}

val PlayerScreenQueueDefault =
    PlayerScreenQueueDefaultBase(
        { colorScheme, containerColor, contentColor -> colorScheme.surfaceContainerLow },
        { colorScheme, containerColor, contentColor -> colorScheme.onSurface },
    )
val PlayerScreenQueueColored =
    PlayerScreenQueueDefaultBase(
        { colorScheme, containerColor, contentColor -> containerColor.darken() },
        { colorScheme, containerColor, contentColor -> containerColor.darken().contentColor() },
    )

@Immutable
class PlayerScreenQueueDefaultBase(
    private val getContainerColor: (ColorScheme, Color, Color) -> Color,
    private val getContentColor: (ColorScheme, Color, Color) -> Color,
) : PlayerScreenQueue() {
    @Composable
    override fun Compose(
        playQueue: List<Pair<Any, Track>>,
        currentTrackIndex: Int,
        lazyListState: LazyListState,
        trackOverflowMenuItems: (Track, Int) -> List<MenuItem>,
        dragModifier: Modifier,
        nestedScrollConnection: NestedScrollConnection,
        containerColor: Color,
        contentColor: Color,
        onTogglePlayQueue: () -> Unit,
        onMoveTrack: (Int, Int) -> Unit,
        onSeekTo: (Int) -> Unit,
    ) {
        val view = LocalView.current

        val upNextCount = playQueue.size - currentTrackIndex - 1
        val upNextDuration =
            remember(playQueue, currentTrackIndex) {
                playQueue.drop(currentTrackIndex + 1).sumOfDuration { it.second.duration }.format()
            }

        var reorderableQueue by remember { mutableStateOf(null as List<Pair<Any, Track>>?) }
        var reorderInfo by remember { mutableStateOf(null as Pair<Int, Int>?) }
        val reorderableLazyListState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                ViewCompat.performHapticFeedback(
                    view,
                    HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK,
                )
                reorderInfo =
                    if (reorderInfo == null)
                        playQueue.indexOfFirst { it.first == from.key } to to.index
                    else reorderInfo!!.first to to.index

                reorderableQueue =
                    reorderableQueue?.toMutableList()?.apply { add(to.index, removeAt(from.index)) }
            }

        LaunchedEffect(playQueue) { reorderableQueue = null }

        Surface(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Surface(
                    color =
                        getContainerColor(MaterialTheme.colorScheme, containerColor, contentColor),
                    contentColor =
                        getContentColor(MaterialTheme.colorScheme, containerColor, contentColor),
                ) {
                    LibraryListHeader(
                        Strings.separate(
                            Strings[R.string.player_up_next],
                            Strings[R.string.count_track].icuFormat(upNextCount),
                            upNextDuration,
                        ),
                        modifier = dragModifier,
                        onClick = onTogglePlayQueue,
                    )
                }

                Scrollbar(lazyListState, { (it - currentTrackIndex).toString() }) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                    ) {
                        itemsIndexed(reorderableQueue ?: playQueue, { _, (key, _) -> key }) {
                            index,
                            (key, track) ->
                            ReorderableItem(
                                reorderableLazyListState,
                                key,
                                animateItemModifier =
                                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            ) {
                                LibraryListItemHorizontal(
                                    title = track.displayTitle,
                                    subtitle = track.displayArtistWithAlbum,
                                    lead = {
                                        AnimatedContent(
                                            targetState = (index - currentTrackIndex).toString(),
                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                            modifier =
                                                Modifier.draggableHandle(
                                                    onDragStarted = {
                                                        ViewCompat.performHapticFeedback(
                                                            view,
                                                            HapticFeedbackConstantsCompat.DRAG_START,
                                                        )
                                                        reorderInfo = null
                                                        reorderableQueue = playQueue
                                                    },
                                                    onDragStopped = {
                                                        ViewCompat.performHapticFeedback(
                                                            view,
                                                            HapticFeedbackConstantsCompat
                                                                .GESTURE_END,
                                                        )
                                                        reorderInfo?.let { (from, to) ->
                                                            onMoveTrack(from, to)
                                                        }
                                                    },
                                                ),
                                        ) {
                                            Text(
                                                text = it,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    },
                                    actions = {
                                        OverflowMenu(trackOverflowMenuItems(track, index))
                                    },
                                    deemphasized = index <= currentTrackIndex,
                                    modifier =
                                        Modifier.clickable { onSeekTo(index) }
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
