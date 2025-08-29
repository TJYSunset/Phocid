package org.sunsetware.phocid.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.sunsetware.phocid.AUDIO_SESSION_ID_KEY
import org.sunsetware.phocid.PlaybackService
import org.sunsetware.phocid.SET_TIMER_COMMAND
import org.sunsetware.phocid.TIMER_FINISH_LAST_TRACK_KEY
import org.sunsetware.phocid.TIMER_TARGET_KEY
import org.sunsetware.phocid.utils.coerceInOrMin
import org.sunsetware.phocid.utils.wrap

@Stable
class PlayerManager(
    val state: StateFlow<PlayerState>,
    private val _transientState: MutableStateFlow<PlayerTransientState>,
) : AutoCloseable {
    private lateinit var mediaController: MediaController
    val transientState = _transientState.asStateFlow()

    val currentPosition: Long
        get() = mediaController.currentPosition

    private var playbackPreferenceJob = null as Job?

    override fun close() {
        playbackPreferenceJob?.cancel()
        mediaController.release()
    }

    suspend fun initialize(context: Context) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        val completed = AtomicBoolean(false)
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                mediaController.prepare()
                completed.set(true)
            },
            ContextCompat.getMainExecutor(context),
        )

        while (!completed.get()) {
            delay(1)
        }
    }

    private fun updateTransientState() {
        _transientState.update { mediaController.captureTransientState() }
    }

    fun seekToPrevious() {
        val currentIndex = mediaController.currentMediaItemIndex
        val previousIndex =
            (currentIndex - 1).wrap(
                mediaController.mediaItemCount,
                mediaController.repeatMode != Player.REPEAT_MODE_OFF,
            ) ?: currentIndex
        mediaController.seekTo(previousIndex, 0)
        // Force a state emission for UI recomposition.
        updateTransientState()
        mediaController.play()
    }

    fun seekToPreviousSmart() {
        val currentIndex = mediaController.currentMediaItemIndex
        val previousIndex =
            (currentIndex - 1)
                .wrap(
                    mediaController.mediaItemCount,
                    mediaController.repeatMode != Player.REPEAT_MODE_OFF,
                )
                .takeIf {
                    mediaController.currentPosition <= mediaController.maxSeekToPreviousPosition
                } ?: currentIndex
        mediaController.seekTo(previousIndex, 0)
        // Force a state emission for UI recomposition.
        updateTransientState()
        mediaController.play()
    }

    fun seekToNext() {
        val currentIndex = mediaController.currentMediaItemIndex
        val nextIndex =
            (currentIndex + 1).wrap(
                mediaController.mediaItemCount,
                mediaController.repeatMode != Player.REPEAT_MODE_OFF,
            ) ?: currentIndex
        mediaController.seekTo(nextIndex, 0)
        // Force a state emission for UI recomposition.
        updateTransientState()
        mediaController.play()
    }

    fun seekTo(index: Int) {
        mediaController.seekTo(index, 0)
        mediaController.play()
    }

    fun seekToFraction(fraction: Float) {
        val duration = mediaController.duration
        mediaController.seekTo((duration * fraction).toLong().coerceInOrMin(0, duration))
    }

    fun togglePlay() {
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            play()
        }
    }

    fun play() {
        if (
            mediaController.currentPosition >= mediaController.duration - 1 &&
                !mediaController.hasNextMediaItem() &&
                !mediaController.isPlaying
        ) {
            // Media3 might instantly pause instead of starting from the beginning if these
            // conditions are met
            mediaController.seekTo(0)
        }
        mediaController.play()
    }

    fun setTracks(tracks: List<Track>, index: Int?) {
        if (index != null)
            mediaController.setMediaItems(tracks.map { it.getMediaItem(null) }, index, 0)
        else mediaController.setMediaItems(tracks.map { it.getMediaItem(null) })
        mediaController.play()
    }

    fun addTracks(tracks: List<Track>) {
        mediaController.addMediaItems(tracks.map { it.getMediaItem(null) })
    }

    fun playNext(tracks: List<Track>) {
        state.value.let { state ->
            if (!state.shuffle) {
                mediaController.addMediaItems(
                    if (state.actualPlayQueue.isNotEmpty()) {
                        state.currentIndex + 1
                    } else {
                        0
                    },
                    tracks.map { it.getMediaItem(null) },
                )
            } else {
                if (state.actualPlayQueue.isNotEmpty()) {
                    val mediaItems =
                        (0..<mediaController.mediaItemCount).map {
                            mediaController.getMediaItemAt(it)
                        }
                    val currentIndex = mediaController.currentMediaItemIndex
                    val currentUnshuffledIndex = mediaItems[currentIndex].getUnshuffledIndex()!!
                    val offsetOriginal =
                        mediaItems.map {
                            it.setUnshuffledIndex(
                                it.getUnshuffledIndex()!!.let {
                                    if (it > currentUnshuffledIndex) it + tracks.size else it
                                }
                            )
                        }
                    val new =
                        tracks.mapIndexed { i, track ->
                            track.getMediaItem(currentUnshuffledIndex + 1 + i)
                        }
                    mediaController.replaceMediaItems(
                        currentIndex + 1,
                        Int.MAX_VALUE,
                        new + offsetOriginal.drop(currentIndex + 1),
                    )
                    mediaController.replaceMediaItem(currentIndex, offsetOriginal[currentIndex])
                    mediaController.replaceMediaItems(
                        0,
                        currentIndex,
                        offsetOriginal.take(currentIndex),
                    )
                } else {
                    mediaController.addMediaItems(
                        tracks.mapIndexed { i, track -> track.getMediaItem(i) }
                    )
                }
            }
        }
    }

    fun moveTrack(from: Int, to: Int) {
        mediaController.moveMediaItem(from, to)
    }

    fun removeTrack(index: Int) {
        // [capturePlayerState] should take care of discontinuous [UNSHUFFLED_INDEX_KEY].
        mediaController.removeMediaItem(index)
    }

    fun clearTracks() {
        mediaController.clearMediaItems()
    }

    fun toggleShuffle() {
        mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
    }

    fun enableShuffle() {
        mediaController.shuffleModeEnabled = true
    }

    fun toggleRepeat() {
        mediaController.repeatMode =
            when (mediaController.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
    }

    fun getTimerState(): Pair<Long, Boolean>? {
        return mediaController.sessionExtras
            .getLong(TIMER_TARGET_KEY, -1)
            .takeIf { it >= 0 }
            ?.let {
                Pair(
                    it,
                    mediaController.sessionExtras.getBoolean(TIMER_FINISH_LAST_TRACK_KEY, true),
                )
            }
    }

    fun setTimer(settings: PlayerTimerSettings) {
        mediaController.sendCustomCommand(
            SessionCommand(SET_TIMER_COMMAND, Bundle.EMPTY),
            bundleOf(
                Pair(
                    TIMER_TARGET_KEY,
                    SystemClock.elapsedRealtime() + settings.duration.inWholeMilliseconds,
                ),
                Pair(TIMER_FINISH_LAST_TRACK_KEY, settings.finishLastTrack),
            ),
        )
    }

    fun cancelTimer() {
        mediaController.sendCustomCommand(
            SessionCommand(SET_TIMER_COMMAND, Bundle.EMPTY),
            bundleOf(Pair(TIMER_TARGET_KEY, -1)),
        )
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        mediaController.playbackParameters = PlaybackParameters(speed, pitch)
    }

    fun openSystemEqualizer(context: Context): Boolean {
        val sessionId = mediaController.sessionExtras.getInt(AUDIO_SESSION_ID_KEY)
        return try {
            context.startActivity(
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
