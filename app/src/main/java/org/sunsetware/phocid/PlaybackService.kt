package org.sunsetware.phocid

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.sunsetware.phocid.utils.Random
import org.sunsetware.phocid.utils.wrap

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val coroutineScope = MainScope()
    private val timerMutex = Mutex()
    @Volatile private var timerTarget = -1L
    @Volatile private var timerFinishLastTrack = true
    @Volatile private var playOnOutputDeviceConnection = false
    @Volatile private var audioOffloading = true
    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo?>?) {
                if (playOnOutputDeviceConnection) {
                    mediaSession?.player?.play()
                }
            }
        }

    // Create your player and media session in the onCreate lifecycle event
    override fun onCreate() {
        super.onCreate()
        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        val player =
            CustomizedPlayer(
                ExoPlayer.Builder(this)
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .build()
                    .apply {
                        trackSelectionParameters =
                            trackSelectionParameters
                                .buildUpon()
                                .setAudioOffloadPreferences(
                                    AudioOffloadPreferences.Builder()
                                        .setAudioOffloadMode(
                                            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                                        )
                                        .build()
                                )
                                .build()
                    }
            )
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.inner.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
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
                                    mediaSession?.updateSessionExtras {
                                        putLong(TIMER_TARGET_KEY, -1)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    player.updateAudioOffloading(audioOffloading)
                }
            }
        )
        coroutineScope.launch {
            while (isActive) {
                timerMutex.withLock {
                    if (timerTarget >= 0 && SystemClock.elapsedRealtime() >= timerTarget) {
                        if (!timerFinishLastTrack) {
                            player.pause()
                            timerTarget = -1
                            mediaSession?.updateSessionExtras { putLong(TIMER_TARGET_KEY, -1) }
                        } else if (!player.isPlaying) {
                            player.pause()
                            timerTarget = -1
                            mediaSession?.updateSessionExtras { putLong(TIMER_TARGET_KEY, -1) }
                        }
                    }
                }

                delay(1.seconds)
            }
        }
        mediaSession =
            MediaSession.Builder(this, player)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        packageManager.getLaunchIntentForPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onCustomCommand(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> {
                            when (customCommand.customAction) {
                                SET_TIMER_COMMAND -> {
                                    runBlocking {
                                        timerMutex.withLock {
                                            val target = args.getLong(TIMER_TARGET_KEY, -1)
                                            val finishLastTrack =
                                                args.getBoolean(TIMER_FINISH_LAST_TRACK_KEY, true)
                                            timerTarget = target
                                            timerFinishLastTrack = finishLastTrack
                                            session.updateSessionExtras {
                                                putLong(TIMER_TARGET_KEY, target)
                                                putBoolean(
                                                    TIMER_FINISH_LAST_TRACK_KEY,
                                                    finishLastTrack,
                                                )
                                            }
                                        }
                                    }
                                    return Futures.immediateFuture(
                                        SessionResult(SessionResult.RESULT_SUCCESS)
                                    )
                                }

                                SET_PLAYBACK_PREFERENCE_COMMAND -> {
                                    playOnOutputDeviceConnection =
                                        args.getBoolean(PLAY_ON_OUTPUT_DEVICE_CONNECTION_KEY, false)
                                    audioOffloading = args.getBoolean(AUDIO_OFFLOADING_KEY, true)
                                    player.updateAudioOffloading(audioOffloading)
                                    player.reshuffleOnRepeat =
                                        args.getBoolean(RESHUFFLE_ON_REPEAT_KEY, false)
                                    return Futures.immediateFuture(
                                        SessionResult(SessionResult.RESULT_SUCCESS)
                                    )
                                }

                                else ->
                                    return Futures.immediateFuture(
                                        SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                                    )
                            }
                        }

                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): ConnectionResult {
                            return ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(
                                    ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                        .add(SessionCommand(SET_TIMER_COMMAND, Bundle.EMPTY))
                                        .add(
                                            SessionCommand(
                                                SET_PLAYBACK_PREFERENCE_COMMAND,
                                                Bundle.EMPTY,
                                            )
                                        )
                                        .build()
                                )
                                .build()
                        }
                    }
                )
                .build()
        getSystemService(AudioManager::class.java)
            .registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // The user dismissed the app from the recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {}

    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        getSystemService(AudioManager::class.java)
            .unregisterAudioDeviceCallback(audioDeviceCallback)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        coroutineScope.cancel()
        super.onDestroy()
    }

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
}

/** https://github.com/androidx/media/issues/1708 */
@UnstableApi
private class CustomizedPlayer(val inner: ExoPlayer) : ForwardingPlayer(inner) {
    var reshuffleOnRepeat = false

    private val listeners = mutableMapOf<Player.Listener, ForwardingListener>()
    private var shuffle = false

    init {
        inner.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (
                        currentMediaItemIndex == 0 &&
                            reason == MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                            shuffle &&
                            reshuffleOnRepeat &&
                            mediaItemCount > 2
                    ) {
                        seekTo(Random.nextInt(0, mediaItemCount - 1), 0)
                        disableShuffle()
                        enableShuffle()
                    }
                }
            }
        )
    }

    override fun addListener(listener: Player.Listener) {
        if (listeners.containsKey(listener)) return
        val forwardingListener = ForwardingListener(this, listener)
        listeners[listener] = forwardingListener
        inner.addListener(forwardingListener)
    }

    override fun removeListener(listener: Player.Listener) {
        val forwardingListener = listeners.remove(listener)
        forwardingListener?.let { inner.removeListener(it) }
    }

    override fun getShuffleModeEnabled(): Boolean {
        return shuffle
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        var raiseEvent = true
        if (shuffleModeEnabled && !shuffle) {
            enableShuffle()
        } else if (!shuffleModeEnabled && shuffle) {
            disableShuffle()
        } else {
            raiseEvent = false
        }

        shuffle = shuffleModeEnabled

        if (raiseEvent) {
            for (listener in listeners.values) {
                listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
            }
        }
    }

    override fun seekToPreviousMediaItem() {
        if (mediaItemCount <= 0) return
        val currentIndex = currentMediaItemIndex
        val previousIndex =
            (currentIndex - 1).wrap(mediaItemCount, repeatMode != REPEAT_MODE_OFF) ?: currentIndex
        seekTo(previousIndex, 0)
    }

    override fun seekToPrevious() {
        if (mediaItemCount <= 0) return
        val currentIndex = currentMediaItemIndex
        val previousIndex =
            (currentIndex - 1).wrap(mediaItemCount, repeatMode != REPEAT_MODE_OFF).takeIf {
                currentPosition <= maxSeekToPreviousPosition
            } ?: currentIndex
        seekTo(previousIndex, 0)
    }

    @Deprecated("")
    override fun seekToPreviousWindow() {
        seekToPreviousMediaItem()
    }

    override fun seekToNextMediaItem() {
        if (mediaItemCount <= 0) return
        val currentIndex = currentMediaItemIndex
        if (
            currentIndex == mediaItemCount - 1 &&
                shuffle &&
                reshuffleOnRepeat &&
                repeatMode != REPEAT_MODE_OFF &&
                mediaItemCount > 2
        ) {
            seekTo(Random.nextInt(0, mediaItemCount - 1), 0)
            disableShuffle()
            enableShuffle()
        } else {
            val nextIndex =
                (currentIndex + 1).wrap(mediaItemCount, repeatMode != REPEAT_MODE_OFF)
                    ?: currentIndex
            seekTo(nextIndex, 0)
        }
    }

    override fun seekToNext() {
        seekToNextMediaItem()
    }

    @Deprecated("")
    override fun seekToNextWindow() {
        seekToNextMediaItem()
    }

    override fun getAvailableCommands(): Player.Commands {
        return inner.availableCommands
            .buildUpon()
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
    }

    override fun hasPreviousMediaItem(): Boolean {
        return mediaItemCount > 0
    }

    override fun hasNextMediaItem(): Boolean {
        return mediaItemCount > 0
    }

    fun enableShuffle() {
        if (currentTimeline.isEmpty) return

        val currentIndex = currentMediaItemIndex
        val itemCount = mediaItemCount
        val shuffledPlayQueue =
            (0..<itemCount)
                .map { getMediaItemAt(it) }
                .mapIndexed { index, mediaItem -> index to mediaItem.setUnshuffledIndex(index) }
                .filter { it.first != currentIndex }
                .shuffled(Random)
                .map { it.second }
        replaceMediaItems(currentIndex + 1, itemCount, shuffledPlayQueue)
        removeMediaItems(0, currentIndex)
        replaceMediaItem(0, currentMediaItem!!.setUnshuffledIndex(currentIndex))
    }

    fun disableShuffle() {
        if (currentTimeline.isEmpty) return

        val currentIndex = currentMediaItemIndex
        val itemCount = mediaItemCount
        val unshuffledIndex = currentMediaItem!!.getUnshuffledIndex()
        if (unshuffledIndex == null) {
            Log.e("Phocid", "Current track has no unshuffled index when disabling shuffle")
            replaceMediaItems(
                0,
                itemCount,
                (0..<itemCount).map { getMediaItemAt(it).setUnshuffledIndex(null) },
            )
        } else {
            val unshuffledPlayQueue =
                (0..<itemCount)
                    .map { getMediaItemAt(it) }
                    .mapNotNull { mediaItem ->
                        mediaItem.getUnshuffledIndex()?.let { Pair(mediaItem, it) }
                    }
                    .sortedBy { it.second }
                    .map { it.first }
            replaceMediaItem(currentIndex, currentMediaItem!!.setUnshuffledIndex(null))
            replaceMediaItems(
                currentIndex + 1,
                itemCount,
                unshuffledPlayQueue.subList(unshuffledIndex + 1, unshuffledPlayQueue.size).map {
                    it.setUnshuffledIndex(null)
                },
            )
            replaceMediaItems(
                0,
                currentIndex,
                unshuffledPlayQueue.subList(0, unshuffledIndex).map { it.setUnshuffledIndex(null) },
            )
        }
    }

    private class ForwardingListener(val forwardingPlayer: Player, val inner: Player.Listener) :
        Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            inner.onEvents(forwardingPlayer, events)
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            inner.onAvailableCommandsChanged(
                availableCommands
                    .buildUpon()
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
            )
        }

        // region Forwards

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            inner.onTimelineChanged(timeline, reason)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            inner.onMediaItemTransition(mediaItem, reason)
        }

        override fun onTracksChanged(tracks: Tracks) {
            inner.onTracksChanged(tracks)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            inner.onMediaMetadataChanged(mediaMetadata)
        }

        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
            inner.onPlaylistMetadataChanged(mediaMetadata)
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            inner.onIsLoadingChanged(isLoading)
        }

        @Deprecated("")
        override fun onLoadingChanged(isLoading: Boolean) {
            @Suppress("DEPRECATION") inner.onLoadingChanged(isLoading)
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            inner.onTrackSelectionParametersChanged(parameters)
        }

        @Deprecated("")
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            @Suppress("DEPRECATION") inner.onPlayerStateChanged(playWhenReady, playbackState)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            inner.onPlaybackStateChanged(playbackState)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            inner.onPlayWhenReadyChanged(playWhenReady, reason)
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            inner.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            inner.onIsPlayingChanged(isPlaying)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            inner.onRepeatModeChanged(repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            inner.onShuffleModeEnabledChanged(shuffleModeEnabled)
        }

        override fun onPlayerError(error: PlaybackException) {
            inner.onPlayerError(error)
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            inner.onPlayerErrorChanged(error)
        }

        @Deprecated("")
        override fun onPositionDiscontinuity(reason: Int) {
            @Suppress("DEPRECATION") inner.onPositionDiscontinuity(reason)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            inner.onPositionDiscontinuity(oldPosition, newPosition, reason)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            inner.onPlaybackParametersChanged(playbackParameters)
        }

        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
            inner.onSeekBackIncrementChanged(seekBackIncrementMs)
        }

        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
            inner.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        }

        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {
            inner.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            inner.onAudioSessionIdChanged(audioSessionId)
        }

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
            inner.onAudioAttributesChanged(audioAttributes)
        }

        override fun onVolumeChanged(volume: Float) {
            inner.onVolumeChanged(volume)
        }

        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
            inner.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            inner.onDeviceInfoChanged(deviceInfo)
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            inner.onDeviceVolumeChanged(volume, muted)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            inner.onVideoSizeChanged(videoSize)
        }

        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            inner.onSurfaceSizeChanged(width, height)
        }

        override fun onRenderedFirstFrame() {
            inner.onRenderedFirstFrame()
        }

        @Deprecated("")
        override fun onCues(cues: List<Cue>) {
            @Suppress("DEPRECATION") inner.onCues(cues)
        }

        override fun onCues(cueGroup: CueGroup) {
            inner.onCues(cueGroup)
        }

        override fun onMetadata(metadata: Metadata) {
            inner.onMetadata(metadata)
        }

        // endregion
    }
}

fun MediaItem.getUnshuffledIndex(): Int? {
    return mediaMetadata.extras?.getInt(UNSHUFFLED_INDEX_KEY, -1)?.takeIf { it >= 0 }
}

fun MediaItem.setUnshuffledIndex(unshuffledIndex: Int?): MediaItem {
    return buildUpon()
        .setMediaMetadata(
            mediaMetadata
                .buildUpon()
                .setExtras(
                    if (unshuffledIndex == null) bundleOf()
                    else bundleOf(Pair(UNSHUFFLED_INDEX_KEY, unshuffledIndex))
                )
                .build()
        )
        .build()
}
