@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.DEFAULT_SWIPE_THRESHOLD
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.Lyrics
import org.sunsetware.phocid.data.PlayerManager
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.getArtworkColor
import org.sunsetware.phocid.data.isFavorite
import org.sunsetware.phocid.data.loadLyrics
import org.sunsetware.phocid.data.parseLrc
import org.sunsetware.phocid.ui.components.DragLock
import org.sunsetware.phocid.ui.theme.LocalThemeAccent
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.customColorScheme
import org.sunsetware.phocid.ui.theme.emphasizedEnter
import org.sunsetware.phocid.ui.theme.emphasizedStandard
import org.sunsetware.phocid.ui.theme.pureBackgroundColor
import org.sunsetware.phocid.ui.views.playerMenuItems
import org.sunsetware.phocid.ui.views.queueMenuItems
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.map
import org.sunsetware.phocid.utils.wrap

@Composable
fun PlayerScreen(dragLock: DragLock, viewModel: MainViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerManager = viewModel.playerManager
    val uiManager = viewModel.uiManager
    val playerScreenDragState = uiManager.playerScreenDragState
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val libraryIndex by viewModel.libraryIndex.collectAsStateWithLifecycle()

    val playerState by playerManager.state.collectAsStateWithLifecycle()
    val playerTransientStateVersion by
        playerManager.transientState
            .map(coroutineScope) { it.version }
            .collectAsStateWithLifecycle()
    val playQueue by
        playerManager.state
            .combine(coroutineScope, viewModel.libraryIndex) { state, library ->
                val trackCounts = mutableMapOf<Long, Int>()
                state.actualPlayQueue
                    .mapIndexed { index, id -> library.tracks[id] ?: InvalidTrack }
                    .map { track ->
                        val occurrence = trackCounts.getOrPut(track.id) { 0 }
                        trackCounts[track.id] = trackCounts[track.id]!! + 1
                        (Pair(track.id, occurrence) as Any) to track
                    }
            }
            .collectAsStateWithLifecycle()
    val currentTrack by
        remember {
                playerManager.state
                    .combine(viewModel.libraryIndex) { state, library ->
                        if (state.actualPlayQueue.isEmpty()) null
                        else library.tracks[state.actualPlayQueue[state.currentIndex]]
                    }
                    .filterNotNull()
            }
            .collectAsStateWithLifecycle(
                initialValue =
                    if (playerState.actualPlayQueue.isEmpty()) InvalidTrack
                    else
                        libraryIndex.tracks[playerState.actualPlayQueue[playerState.currentIndex]]
                            ?: InvalidTrack
            )
    val currentTrackIndex = playerState.currentIndex
    val playlists by viewModel.playlistManager.playlists.collectAsStateWithLifecycle()
    val currentTrackIsFavorite =
        remember(currentTrack, playlists) { playlists.isFavorite(currentTrack) }
    val currentTrackLyrics =
        remember(currentTrack) {
            val cachedLyrics = viewModel.lyricsCache.get()
            if (cachedLyrics != null && cachedLyrics.first == currentTrack.id) {
                PlayerScreenLyrics.Synced(cachedLyrics.second)
            } else {
                val externalLyrics = loadLyrics(currentTrack, preferences.charsetName)
                if (externalLyrics != null)
                    viewModel.lyricsCache.set(Pair(currentTrack.id, externalLyrics))
                externalLyrics?.let { PlayerScreenLyrics.Synced(it) }
                    ?: if (preferences.treatEmbeddedLyricsAsLrc) {
                        currentTrack.unsyncedLyrics
                            ?.let { parseLrc(it) }
                            ?.takeIf { it.lines.isNotEmpty() }
                            ?.let { PlayerScreenLyrics.Synced(it) }
                            ?: currentTrack.unsyncedLyrics?.let { PlayerScreenLyrics.Unsynced(it) }
                    } else {
                        currentTrack.unsyncedLyrics?.let { PlayerScreenLyrics.Unsynced(it) }
                    }
            }
        }
    val isPlaying by
        playerManager.transientState
            .map(coroutineScope) { it.isPlaying }
            .collectAsStateWithLifecycle()
    val repeat by
        playerManager.state.map(coroutineScope) { it.repeat }.collectAsStateWithLifecycle()
    val shuffle by
        playerManager.state.map(coroutineScope) { it.shuffle }.collectAsStateWithLifecycle()

    val defaultColor = LocalThemeAccent.current
    val artworkColor = remember {
        Animatable(
            if (preferences.coloredPlayer)
                currentTrack.getArtworkColor(preferences.artworkColorPreference)
            else defaultColor
        )
    }

    val playQueueDragLock = uiManager.playerScreenQueueDragLock
    val playQueueLazyListState = rememberLazyListState()
    // Do not animate the first scroll to reduce lags
    val firstAutoPlayQueueScroll = remember { AtomicBoolean(true) }
    suspend fun scrollPlayQueueToNextTrack() {
        val state = playerManager.state.value
        val currentIndex = state.currentIndex
        val nextIndex =
            (currentIndex + 1).wrap(playQueue.size, state.repeat != Player.REPEAT_MODE_OFF)
                ?: currentIndex
        if (firstAutoPlayQueueScroll.getAndSet(false)) {
            playQueueLazyListState.requestScrollToItem(nextIndex)
        } else {
            playQueueLazyListState.animateScrollToItem(nextIndex)
        }
    }
    val playQueueDragState = uiManager.playerScreenQueueDragState
    val playQueueDragTarget by playQueueDragState.targetValue.collectAsStateWithLifecycle()
    val playQueueCollapseEvent by
        uiManager.playerScreenQueueCollapseEvent.collectAsStateWithLifecycle()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (playQueueDragState.position < 1 && available.y < 0) {
                    playQueueDragState.onDrag(playQueueDragLock, available.y)
                    return available
                } else {
                    return Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y > 0f && available.y > 0f) return Offset.Zero
                playQueueDragState.onDrag(playQueueDragLock, available.y)

                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (available.y < 0f && playQueueDragState.position < 1) {
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return available
            }
        }
    }
    val controlsDragModifier =
        Modifier.pointerInput(Unit) {
                // Block non-vertical gestures
                detectHorizontalDragGestures { _, _ -> }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        coroutineScope.launch { playQueueLazyListState.stopScroll() }
                        playQueueDragState.onDragStart(playQueueDragLock)
                    },
                    onDragCancel = {
                        coroutineScope.launch { playQueueLazyListState.stopScroll() }
                        playQueueDragState.onDragEnd(playQueueDragLock, density)
                    },
                    onDragEnd = {
                        coroutineScope.launch { playQueueLazyListState.stopScroll() }
                        playQueueDragState.onDragEnd(playQueueDragLock, density)
                    },
                ) { _, dragAmount ->
                    playQueueDragState.onDrag(playQueueDragLock, dragAmount)
                }
            }

    val useLyricsView by uiManager.playerScreenUseLyricsView.collectAsStateWithLifecycle()
    var hideOverlay by remember { mutableStateOf(false) }
    val lyricsViewVisibility by
        animateFloatAsState(if (useLyricsView) 1f else 0f, emphasizedEnter())
    var lyricsViewAutoScroll by rememberSaveable { mutableStateOf(true) }
    val overlayVisibility by
        animateFloatAsState(if (!hideOverlay || useLyricsView) 1f else 0f, emphasizedStandard())

    val useCountdown by uiManager.playerScreenUseCountdown.collectAsStateWithLifecycle()

    LaunchedEffect(currentTrack) { lyricsViewAutoScroll = true }

    LaunchedEffect(playQueueCollapseEvent) { scrollPlayQueueToNextTrack() }

    // Auto close on playQueue clear
    LaunchedEffect(playQueue) {
        if (playQueue.isEmpty()) {
            playerScreenDragState.animateTo(0f)
        }
    }

    // Change colors
    // TODO: Fix this synchronization
    val disposing = remember { AtomicBoolean(false) }
    LaunchedEffect(currentTrack, preferences.coloredPlayer, preferences.colorfulPlayerBackground) {
        if (!disposing.get()) {
            val color =
                if (preferences.coloredPlayer)
                    currentTrack.getArtworkColor(preferences.artworkColorPreference)
                else defaultColor
            coroutineScope.launch { artworkColor.animateTo(color) }
            if (preferences.colorfulPlayerBackground) {
                uiManager.overrideStatusBarLightColor.update { color.luminance() >= 0.5 }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            disposing.set(true)
            uiManager.overrideStatusBarLightColor.update { null }
        }
    }

    // Start/end listener for NestedScrollConnection
    LaunchedEffect(playQueueLazyListState.isScrollInProgress) {
        if (playQueueLazyListState.isScrollInProgress) {
            playQueueDragState.onDragStart(playQueueDragLock)
        } else {
            playQueueDragState.onDragEnd(playQueueDragLock, density)
        }
    }

    // Scroll playQueue to next track on track change
    LaunchedEffect(playQueue, currentTrack) {
        if (playQueueDragState.position <= 0) scrollPlayQueueToNextTrack()
    }

    val playerLayout = preferences.playerScreenLayout.layout
    val components = preferences.playerScreenLayout.components

    MaterialTheme(
        colorScheme =
            if (preferences.coloredPlayer)
                customColorScheme(
                        color = currentTrack.getArtworkColor(preferences.artworkColorPreference),
                        darkTheme = preferences.darkTheme.boolean ?: isSystemInDarkTheme(),
                    )
                    .let { if (preferences.pureBackgroundColor) it.pureBackgroundColor() else it }
            else MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        val containerColor =
            if (preferences.colorfulPlayerBackground) artworkColor.value
            else MaterialTheme.colorScheme.surfaceContainerHighest
        val contentColor =
            if (preferences.colorfulPlayerBackground) artworkColor.value.contentColor()
            else MaterialTheme.colorScheme.primary
        val uiState =
            remember(
                containerColor,
                contentColor,
                playerLayout,
                components,
                useLyricsView,
                lyricsViewVisibility,
                overlayVisibility,
                useCountdown,
            ) {
                PlayerScreenUiState(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    playerLayout = playerLayout,
                    components = components,
                    useLyricsView = useLyricsView,
                    lyricsViewVisibility = lyricsViewVisibility,
                    overlayVisibility = overlayVisibility,
                    useCountdown = useCountdown,
                )
            }

        Scaffold(
            topBar = {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(uiState.containerColor)
                )
            },
            bottomBar = {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
            },
            contentWindowInsets = WindowInsets(0.dp),
        ) { scaffoldPadding ->
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding)
                        .background(uiState.containerColor)
            ) {
                Layout(
                    content = {
                        Box {
                            uiState.components.topBarStandalone.Compose(
                                containerColor = uiState.containerColor,
                                contentColor = uiState.contentColor,
                                lyricsViewVisibility = uiState.useLyricsView,
                                lyricsAutoScrollButtonVisibility =
                                    uiState.useLyricsView &&
                                        !lyricsViewAutoScroll &&
                                        currentTrackLyrics is PlayerScreenLyrics.Synced,
                                lyricsButtonEnabled =
                                    uiState.useLyricsView || currentTrackLyrics != null,
                                overlayVisibility = uiState.overlayVisibility,
                                onBack = { uiManager.back() },
                                onEnableLyricsViewAutoScroll = { lyricsViewAutoScroll = true },
                                onToggleLyricsView = {
                                    uiManager.playerScreenUseLyricsView.update { !it }
                                    hideOverlay = false
                                },
                            )
                        }
                        Box {
                            uiState.components.topBarOverlay.Compose(
                                containerColor = uiState.containerColor,
                                contentColor = uiState.contentColor,
                                lyricsViewVisibility = uiState.useLyricsView,
                                lyricsAutoScrollButtonVisibility =
                                    uiState.useLyricsView &&
                                        !lyricsViewAutoScroll &&
                                        currentTrackLyrics is PlayerScreenLyrics.Synced,
                                lyricsButtonEnabled =
                                    uiState.useLyricsView || currentTrackLyrics != null,
                                overlayVisibility = uiState.overlayVisibility,
                                onBack = { uiManager.back() },
                                onEnableLyricsViewAutoScroll = { lyricsViewAutoScroll = true },
                                onToggleLyricsView = {
                                    uiManager.playerScreenUseLyricsView.update { !it }
                                    hideOverlay = false
                                },
                            )
                        }
                        Box {
                            uiState.components.artwork.Compose(
                                playerTransientStateVersion = playerTransientStateVersion,
                                carouselArtworkCache = viewModel.carouselArtworkCache,
                                swipeThreshold =
                                    DEFAULT_SWIPE_THRESHOLD * preferences.swipeThresholdMultiplier,
                                highResArtworkPreference = preferences.highResArtworkPreference,
                                artworkColorPreference = preferences.artworkColorPreference,
                                playerState = playerState,
                                playerScreenDragState = playerScreenDragState,
                                dragLock = dragLock,
                                onGetTrackAtIndex = { state, index ->
                                    state.actualPlayQueue.getOrNull(index)?.let {
                                        libraryIndex.tracks[it]
                                    } ?: InvalidTrack
                                },
                                onPrevious = { playerManager.seekToPrevious() },
                                onNext = { playerManager.seekToNext() },
                                onToggleOverlay = { hideOverlay = !hideOverlay },
                            )
                        }
                        Box {
                            uiState.components.lyricsView.Compose(
                                lyrics = currentTrackLyrics,
                                autoScroll = { lyricsViewAutoScroll },
                                currentPosition = { playerManager.currentPosition },
                                preferences = preferences,
                                onDisableAutoScroll = { lyricsViewAutoScroll = false },
                            )
                        }
                        Box {
                            uiState.components.lyricsOverlay.Compose(
                                lyrics = (currentTrackLyrics as? PlayerScreenLyrics.Synced)?.value,
                                currentPosition = { playerManager.currentPosition },
                                preferences = preferences,
                                containerColor = uiState.containerColor,
                                contentColor = uiState.contentColor,
                                overlayVisibility = uiState.overlayVisibility,
                            )
                        }
                        Box {
                            uiState.components.controls.Compose(
                                currentTrack = currentTrack,
                                currentTrackIsFavorite = currentTrackIsFavorite,
                                isPlaying = isPlaying,
                                repeat = repeat,
                                shuffle = shuffle,
                                currentPosition = { playerManager.currentPosition },
                                overflowMenuItems =
                                    playerMenuItems(
                                        playerManager,
                                        uiManager,
                                        libraryIndex,
                                        currentTrack,
                                        currentTrackIndex,
                                    ),
                                dragModifier = controlsDragModifier,
                                containerColor = uiState.containerColor,
                                contentColor = uiState.contentColor,
                                colorfulBackground = preferences.colorfulPlayerBackground,
                                useCountdown = uiState.useCountdown,
                                onSeekToFraction = { playerManager.seekToFraction(it) },
                                onToggleRepeat = { playerManager.toggleRepeat() },
                                onSeekToPreviousSmart = { playerManager.seekToPreviousSmart() },
                                onTogglePlay = { playerManager.togglePlay() },
                                onSeekToNext = { playerManager.seekToNext() },
                                onToggleShuffle = { playerManager.toggleShuffle() },
                                onTogglePlayQueue = {
                                    playQueueDragState.animateTo(
                                        if (playQueueDragState.position <= 0) 1f else 0f
                                    )
                                },
                                onToggleCurrentTrackIsFavorite = {
                                    viewModel.playlistManager.toggleFavorite(currentTrack)
                                },
                                onToggleUseCountdown = {
                                    uiManager.playerScreenUseCountdown.update { !it }
                                },
                            )
                        }
                        Box {
                            uiState.components.queue.Compose(
                                playQueue = playQueue,
                                currentTrackIndex = currentTrackIndex,
                                lazyListState = playQueueLazyListState,
                                trackOverflowMenuItems = { track, index ->
                                    queueMenuItems(playerManager, uiManager, track, index)
                                },
                                dragModifier = controlsDragModifier,
                                nestedScrollConnection = nestedScrollConnection,
                                containerColor = uiState.containerColor,
                                contentColor = uiState.contentColor,
                                colorfulBackground = preferences.colorfulPlayerBackground,
                                dragIndicatorVisibility =
                                    playQueueDragState.position == 1f || playQueueDragTarget == 1f,
                                swipeToRemoveFromQueue = preferences.swipeToRemoveFromQueue,
                                swipeThreshold =
                                    DEFAULT_SWIPE_THRESHOLD * preferences.swipeThresholdMultiplier,
                                alwaysShowHintOnScroll = preferences.alwaysShowHintOnScroll,
                                onTogglePlayQueue = {
                                    playQueueDragState.animateTo(
                                        if (playQueueDragState.position <= 0) 1f else 0f
                                    )
                                },
                                onMoveTrack = { from, to -> playerManager.moveTrack(from, to) },
                                onRemoveTrack = { playerManager.removeTrack(it) },
                                onSeekTo = { playerManager.seekTo(it) },
                            )
                        }
                        Box {
                            // Scrim under queue
                            Box(
                                modifier =
                                    Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.scrim)
                            )
                        }
                        Box {
                            // Scrim under lyrics
                            Box(
                                modifier =
                                    Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { measurables, constraints ->
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        with(uiState.playerLayout) {
                            place(
                                topBarStandalone = measurables[0],
                                topBarOverlay = measurables[1],
                                artwork = measurables[2],
                                lyricsView = measurables[3],
                                lyricsOverlay = measurables[4],
                                controls = measurables[5],
                                queue = measurables[6],
                                scrimQueue = measurables[7],
                                scrimLyrics = measurables[8],
                                width = constraints.maxWidth,
                                height = constraints.maxHeight,
                                density = density,
                                queueDragState = playQueueDragState,
                                lyricsViewVisibility = uiState.lyricsViewVisibility,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Immutable
sealed class PlayerScreenLyrics {
    @Immutable class Synced(val value: Lyrics) : PlayerScreenLyrics()

    @Immutable class Unsynced(val value: String) : PlayerScreenLyrics()
}

@Immutable
data class PlayerScreenComponents(
    val topBarStandalone: PlayerScreenTopBar,
    val topBarOverlay: PlayerScreenTopBar,
    val artwork: PlayerScreenArtwork,
    val lyricsView: PlayerScreenLyricsView,
    val lyricsOverlay: PlayerScreenLyricsOverlay,
    val controls: PlayerScreenControls,
    val queue: PlayerScreenQueue,
)

@Immutable
data class PlayerScreenUiState(
    val containerColor: Color,
    val contentColor: Color,
    val playerLayout: PlayerScreenLayout,
    val components: PlayerScreenComponents,
    val useLyricsView: Boolean,
    val lyricsViewVisibility: Float,
    val overlayVisibility: Float,
    val useCountdown: Boolean,
)

@Serializable
enum class PlayerScreenLayoutType(
    val stringId: Int,
    val layout: PlayerScreenLayout,
    val components: PlayerScreenComponents,
) {
    DEFAULT(
        R.string.preferences_player_screen_layout_default,
        PlayerScreenLayoutDefault,
        PlayerScreenComponents(
            PlayerScreenTopBarDefaultStandalone,
            PlayerScreenTopBarDefaultOverlay,
            PlayerScreenArtworkDefault,
            PlayerScreenLyricsViewDefault,
            PlayerScreenLyricsOverlayDefault,
            PlayerScreenControlsDefault,
            PlayerScreenQueueDefault,
        ),
    ),
    NO_QUEUE(
        R.string.preferences_player_screen_layout_no_queue,
        PlayerScreenLayoutNoQueue,
        PlayerScreenComponents(
            PlayerScreenTopBarDefaultStandalone,
            PlayerScreenTopBarDefaultOverlay,
            PlayerScreenArtworkDefault,
            PlayerScreenLyricsViewDefault,
            PlayerScreenLyricsOverlayDefault,
            PlayerScreenControlsNoQueue,
            PlayerScreenQueueColored,
        ),
    ),
}
