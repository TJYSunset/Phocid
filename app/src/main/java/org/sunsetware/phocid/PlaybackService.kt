package org.sunsetware.phocid

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.sunsetware.phocid.data.DefaultShuffleMode
import org.sunsetware.phocid.data.RadioGenerator
import org.sunsetware.phocid.data.TrackHistoryEntry
import org.sunsetware.phocid.data.appendEntry
import org.sunsetware.phocid.data.capturePlayerState
import org.sunsetware.phocid.data.captureTransientState
import org.sunsetware.phocid.data.getChildMediaItems
import org.sunsetware.phocid.data.getMediaItem
import org.sunsetware.phocid.data.isFavorite
import org.sunsetware.phocid.data.restorePlayerState
import org.sunsetware.phocid.data.search
import org.sunsetware.phocid.data.transformMediaSessionCallbackItems
import org.sunsetware.phocid.data.transformOnAddTracks
import org.sunsetware.phocid.data.transformOnSetTracks
import org.sunsetware.phocid.globals.GlobalData
import org.sunsetware.phocid.service.CustomizedBitmapLoader
import org.sunsetware.phocid.service.CustomizedPlayer
import org.sunsetware.phocid.utils.Random

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val mainScope = MainScope()
    private val timerMutex = Mutex()
    @Volatile private var timerTarget = -1L
    @Volatile private var timerJob = null as Job?
    @Volatile private var timerFinishLastTrack = true
    @Volatile private var playOnOutputDeviceConnection = false
    @Volatile private var audioOffloading = true
    @Volatile private var lastIndex = null as Int?
    @Volatile private var reshuffleOnRepeat = false
    @Volatile private var defaultShuffleModeTrack = DefaultShuffleMode.KEEP_CURRENT
    @Volatile private var defaultShuffleModeList = DefaultShuffleMode.KEEP_CURRENT
    @Volatile private var lastTransitionTrackId = null as Long?
    @Volatile private var lastTransitionPositionMs = null as Long?

    override fun onCreate() {
        super.onCreate()
        while (!GlobalData.initialized.get()) {
            Thread.sleep(1)
        }

        val player = CustomizedPlayer(this)

        // Integrate with system equalizer.
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.inner.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
        player.addListener(createListener(player))

        // Register listener for auto playback on device connection.
        // Must register before restoring the state, or it'll unexpectedly start playback
        // on app startup.
        getSystemService(AudioManager::class.java)
            .registerAudioDeviceCallback(audioDeviceCallback, null)

        // Restore state.
        player.restorePlayerState(
            GlobalData.playerState.value,
            GlobalData.unfilteredTrackIndex.value,
        )
        mainScope.launch {
            GlobalData.preferences
                .onEach { preferences ->
                    playOnOutputDeviceConnection = preferences.playOnOutputDeviceConnection
                    player.setAudioAttributes(player.audioAttributes, preferences.pauseOnFocusLoss)
                    audioOffloading = preferences.audioOffloading
                    player.updateAudioOffloading(audioOffloading)
                    reshuffleOnRepeat = preferences.reshuffleOnRepeat
                    defaultShuffleModeTrack = preferences.defaultShuffleModeTrack
                    defaultShuffleModeList = preferences.defaultShuffleModeList
                }
                .collect()
        }

        mediaSession =
            MediaLibrarySession.Builder(
                    this,
                    player,
                    createMediaSessionCallback(player, playerCommands),
                )
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        packageManager.getLaunchIntentForPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setBitmapLoader(CustomizedBitmapLoader(this))
                .setSessionExtras(bundleOf(AUDIO_SESSION_ID_KEY to player.inner.audioSessionId))
                .setMediaButtonPreferences(commandButtons(player))
                .build()

        // Update command buttons on global data changes.
        mainScope.launch {
            GlobalData.preferences
                .combine(GlobalData.playlistManager.playlists) { preferences, playlists ->
                    preferences.notificationButtons to playlists
                }
                .distinctUntilChanged()
                .onEach { mediaSession?.setMediaButtonPreferences(commandButtons(player)) }
                .collect()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {}

    override fun onDestroy() {
        getSystemService(AudioManager::class.java)
            .unregisterAudioDeviceCallback(audioDeviceCallback)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        mainScope.cancel()
        super.onDestroy()
    }

    // region Initialization details

    private fun createListener(player: CustomizedPlayer): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && player.mediaItemCount > 0) {
                    val trackId = player.currentMediaItem?.mediaId?.toLongOrNull()
                    if (trackId != null) {
                        recordHistoryIfEligible(trackId, player.currentPosition)
                    }
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                GlobalData.playerState.update { player.capturePlayerState() }
                GlobalData.playerTransientState.update { player.captureTransientState() }
                mediaSession?.setMediaButtonPreferences(commandButtons(player))

                if (
                    events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                    )
                ) {
                    runBlocking {
                        timerMutex.withLock {
                            if (
                                timerTarget >= 0 &&
                                    SystemClock.elapsedRealtime() >= timerTarget &&
                                    timerFinishLastTrack
                            ) {
                                player.pause()
                                timerTarget = -1
                                mediaSession?.updateSessionExtras { putLong(TIMER_TARGET_KEY, -1) }
                                timerJob?.cancel()
                                timerJob = null
                            }
                        }
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // onEvent won't trigger for this...
                GlobalData.playerState.update { player.capturePlayerState() }
                mediaSession?.setMediaButtonPreferences(commandButtons(player))
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                player.updateAudioOffloading(audioOffloading)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    if (lastIndex != null && lastIndex != player.currentMediaItemIndex) {
                        val previousTrackId =
                            GlobalData.playerState.value.actualPlayQueue.getOrNull(lastIndex!!)
                        if (previousTrackId != null) {
                            val positionMs =
                                if (lastTransitionTrackId == previousTrackId)
                                    lastTransitionPositionMs
                                else null
                            recordHistoryIfEligible(previousTrackId, positionMs)
                        }
                    }
                }

                lastTransitionTrackId = null
                lastTransitionPositionMs = null

                if (
                    player.currentMediaItemIndex == 0 &&
                        lastIndex == player.mediaItemCount - 1 &&
                        (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
                        player.shuffleModeEnabled &&
                        reshuffleOnRepeat &&
                        player.mediaItemCount > 2
                ) {
                    player.seekTo(Random.nextInt(0, player.mediaItemCount - 1), 0)
                    player.disableShuffle()
                    player.enableShuffle()
                }
                lastIndex = player.currentMediaItemIndex

                val seedTrackId = GlobalData.radioSeedTrackId.value
                if (seedTrackId != null) {
                    val preferences = GlobalData.preferences.value
                    if (preferences.radioInfiniteMode) {
                        val remaining =
                            player.mediaItemCount - player.currentMediaItemIndex
                        if (remaining <= 3) {
                            val libraryIndex = GlobalData.libraryIndex.value
                            val seedTrack = libraryIndex.tracks[seedTrackId]
                            if (seedTrack != null) {
                                val historyEntries = GlobalData.historyEntries.value
                                val newTracks =
                                    RadioGenerator.generate(
                                        seedTrack,
                                        libraryIndex,
                                        historyEntries,
                                        preferences.radioMixRatio,
                                        preferences.radioBatchSize,
                                    )
                                player.addMediaItems(
                                    newTracks.map { it.getMediaItem(null) }
                                )
                            }
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (
                    reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                        reason == Player.DISCONTINUITY_REASON_SEEK
                ) {
                    val oldIndex = oldPosition.mediaItemIndex
                    val newIndex = newPosition.mediaItemIndex
                    if (
                        reason == Player.DISCONTINUITY_REASON_SEEK &&
                            oldIndex == newIndex &&
                            newPosition.positionMs == 0L &&
                            oldIndex != C.INDEX_UNSET &&
                            oldIndex in 0..<player.mediaItemCount &&
                            oldPosition.positionMs > 0
                    ) {
                        val trackId =
                            player.getMediaItemAt(oldIndex).mediaId.toLongOrNull()
                        if (trackId != null) {
                            recordHistoryIfEligible(trackId, oldPosition.positionMs)
                        }
                    }
                    if (
                        oldIndex != newIndex &&
                            oldIndex != C.INDEX_UNSET &&
                            oldIndex in 0..<player.mediaItemCount
                    ) {
                        val trackId =
                            player.getMediaItemAt(oldIndex).mediaId.toLongOrNull()
                        lastTransitionTrackId = trackId
                        lastTransitionPositionMs = oldPosition.positionMs
                    } else {
                        lastTransitionTrackId = null
                        lastTransitionPositionMs = null
                    }
                }
            }
        }
    }

    private fun recordHistoryIfEligible(trackId: Long, positionMs: Long?) {
        val preferences = GlobalData.preferences.value
        val durationMs =
            GlobalData.libraryIndex.value.tracks[trackId]?.duration?.inWholeMilliseconds
        val percentThreshold = preferences.historyRecordMinPositionPercentage.coerceIn(0f, 1f)
        val timeThresholdMs = preferences.historyRecordMinPositionSeconds.toLong() * 1000L
        val percentCheck =
            if (durationMs != null && durationMs > 0 && positionMs != null) {
                positionMs.toDouble() / durationMs.toDouble() > percentThreshold
            } else {
                percentThreshold <= 0f
            }
        val timeCheck =
            if (positionMs != null) {
                positionMs > timeThresholdMs
            } else {
                timeThresholdMs <= 0L
            }

        if (percentCheck || timeCheck) {
            val timestamp = System.currentTimeMillis()
            GlobalData.historyEntries.update { history ->
                history.appendEntry(TrackHistoryEntry(trackId, timestamp))
            }
        }
    }

    private val defaultLibraryParams =
        LibraryParams.Builder().setOffline(true).setRecent(false).setSuggested(false).build()

    private fun createMediaSessionCallback(
        player: CustomizedPlayer,
        commands: Map<String, (CustomizedPlayer, MediaSession, Bundle) -> Unit>,
    ): MediaLibrarySession.Callback {
        return object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return Futures.immediateFuture(
                    LibraryResult.ofItem(
                        MediaItem.Builder()
                            .setMediaId(ROOT_MEDIA_ID)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .build()
                            )
                            .build(),
                        defaultLibraryParams,
                    )
                )
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return Futures.immediateFuture(
                    getChildMediaItems(
                            GlobalData.preferences.value,
                            GlobalData.libraryIndex.value,
                            GlobalData.playlistManager.playlists.value,
                            parentId,
                        )
                        ?.let {
                            LibraryResult.ofItemList(
                                it.drop(pageSize * page).take(pageSize),
                                defaultLibraryParams,
                            )
                        } ?: LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)
                )
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<Void>> {
                val preferences = GlobalData.preferences.value
                val libraryIndex = GlobalData.libraryIndex.value
                val result =
                    libraryIndex.tracks.values.search(query, preferences.searchCollator).map {
                        it.getMediaItem(null)
                    }
                session.notifySearchResultChanged(browser, query, result.size, defaultLibraryParams)
                return Futures.immediateFuture(LibraryResult.ofVoid())
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val preferences = GlobalData.preferences.value
                val libraryIndex = GlobalData.libraryIndex.value
                val result =
                    libraryIndex.tracks.values.search(query, preferences.searchCollator).map {
                        it.getMediaItem(null)
                    }
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(
                        result.drop(page * pageSize).take(pageSize),
                        defaultLibraryParams,
                    )
                )
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
                startIndex: Int,
                startPositionMs: Long,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val preferences = GlobalData.preferences.value
                val libraryIndex = GlobalData.libraryIndex.value
                val playerState = GlobalData.playerState.value
                val playlists = GlobalData.playlistManager.playlists.value
                val items =
                    transformMediaSessionCallbackItems(
                        preferences,
                        libraryIndex,
                        playlists,
                        mediaItems,
                    )
                val shuffle =
                    if (startIndex != C.INDEX_UNSET) {
                        when (defaultShuffleModeTrack) {
                            DefaultShuffleMode.KEEP_CURRENT -> playerState.shuffle
                            DefaultShuffleMode.OFF -> false
                            DefaultShuffleMode.ON -> true
                        }
                    } else {
                        when (defaultShuffleModeList) {
                            DefaultShuffleMode.KEEP_CURRENT -> playerState.shuffle
                            DefaultShuffleMode.OFF -> false
                            DefaultShuffleMode.ON -> true
                        }
                    }
                player.setShuffleModeEnabledWithoutReshuffle(shuffle)
                val (newItems, seekIndex) =
                    transformOnSetTracks(shuffle, items, startIndex.takeIf { it != C.INDEX_UNSET })
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(newItems, seekIndex, startPositionMs)
                )
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                /** These only contain valid [MediaItem.mediaId]s; all other fields are null */
                mediaItems: List<MediaItem>,
            ): ListenableFuture<List<MediaItem>> {
                val preferences = GlobalData.preferences.value
                val libraryIndex = GlobalData.libraryIndex.value
                val playerState = GlobalData.playerState.value
                val playlists = GlobalData.playlistManager.playlists.value
                val items =
                    transformMediaSessionCallbackItems(
                        preferences,
                        libraryIndex,
                        playlists,
                        mediaItems,
                    )
                return Futures.immediateFuture(transformOnAddTracks(playerState, items))
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                val command = commands[customCommand.customAction]
                if (command != null) {
                    command(player, session, args)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            }

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): ConnectionResult {
                return ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                            .addSessionCommands(
                                commands.map { (command, _) ->
                                    SessionCommand(command, Bundle.EMPTY)
                                }
                            )
                            .build()
                    )
                    .setMediaButtonPreferences(commandButtons(player))
                    .build()
            }
        }
    }

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo?>?) {
                if (playOnOutputDeviceConnection) {
                    mediaSession?.player?.play()
                }
            }
        }

    // endregion

    // region Commands

    private val playerCommands =
        mapOf(
            SET_TIMER_COMMAND to ::onSetTimer,
            EXTERNAL_REPEAT_COMMAND to ::onExternalRepeat,
            EXTERNAL_SHUFFLE_COMMAND to ::onExternalShuffle,
            EXTERNAL_FAVORITE_COMMAND to ::onExternalFavorite,
        )

    private fun commandButtons(player: Player): List<CommandButton> {
        return GlobalData.preferences.value.notificationButtons.map {
            it.build(
                player,
                GlobalData.libraryIndex.value.tracks[
                        player.currentMediaItem?.mediaId?.toLongOrNull()]
                    ?.let { GlobalData.playlistManager.playlists.value.isFavorite(it) } == true,
            )
        }
    }

    private fun newTimerJob(player: Player): Job {
        return mainScope.launch {
            while (isActive) {
                timerMutex.withLock {
                    if (
                        timerTarget >= 0 &&
                            SystemClock.elapsedRealtime() >= timerTarget &&
                            (!timerFinishLastTrack || !player.isPlaying)
                    ) {
                        player.pause()
                        timerTarget = -1
                        mediaSession?.updateSessionExtras { putLong(TIMER_TARGET_KEY, -1) }
                        timerJob?.cancel()
                        timerJob = null
                    } else if (timerTarget < 0) {
                        timerJob?.cancel()
                        timerJob = null
                    }
                }

                delay(1.seconds)
            }
        }
    }

    private fun onSetTimer(player: CustomizedPlayer, session: MediaSession, args: Bundle) {
        runBlocking {
            timerMutex.withLock {
                val target = args.getLong(TIMER_TARGET_KEY, -1)
                val finishLastTrack = args.getBoolean(TIMER_FINISH_LAST_TRACK_KEY, true)
                timerTarget = target
                timerFinishLastTrack = finishLastTrack
                session.updateSessionExtras {
                    putLong(TIMER_TARGET_KEY, target)
                    putBoolean(TIMER_FINISH_LAST_TRACK_KEY, finishLastTrack)
                }
                timerJob?.cancel()
                timerJob = newTimerJob(player)
            }
        }
    }

    private fun onExternalRepeat(
        player: CustomizedPlayer,
        @Suppress("unused") session: MediaSession,
        @Suppress("unused") args: Bundle,
    ) {
        player.repeatMode =
            when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
    }

    private fun onExternalShuffle(
        player: CustomizedPlayer,
        @Suppress("unused") session: MediaSession,
        @Suppress("unused") args: Bundle,
    ) {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    private fun onExternalFavorite(
        player: CustomizedPlayer,
        @Suppress("unused") session: MediaSession,
        @Suppress("unused") args: Bundle,
    ) {
        GlobalData.libraryIndex.value.tracks[player.currentMediaItem?.mediaId?.toLongOrNull()]?.let(
            GlobalData.playlistManager::toggleFavorite
        )
    }

    // endregion

    // region Utils

    private inline fun MediaSession.updateSessionExtras(crossinline action: Bundle.() -> Unit) {
        val bundle = sessionExtras.clone() as Bundle
        action(bundle)
        sessionExtras = bundle
    }

    private fun Player.updateAudioOffloading(audioOffloading: Boolean) {
        trackSelectionParameters =
            trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    if (audioOffloading) {
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                            .setIsSpeedChangeSupportRequired(
                                playbackParameters.speed != 1f || playbackParameters.pitch != 1f
                            )
                            .build()
                    } else {
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(
                                AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                            )
                            .build()
                    }
                )
                .build()
    }

    // endregion
}
