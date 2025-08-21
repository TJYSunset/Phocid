package org.sunsetware.phocid.service

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.sunsetware.phocid.data.getUnshuffledIndex
import org.sunsetware.phocid.data.setUnshuffledIndex
import org.sunsetware.phocid.utils.Random

@UnstableApi
class CustomizedPlayer(val inner: ExoPlayer) : ForwardingPlayer(inner) {
    private val listeners = mutableSetOf<Player.Listener>()
    private var shuffle = false

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
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
            for (listener in listeners) {
                listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
            }
        }
    }

    fun setShuffleModeEnabledWithoutReshuffle(shuffleModeEnabled: Boolean) {
        val raiseEvent = shuffleModeEnabled != shuffle
        shuffle = shuffleModeEnabled
        if (raiseEvent) {
            for (listener in listeners) {
                listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
            }
        }
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
}

@UnstableApi
fun CustomizedPlayer(context: Context): CustomizedPlayer {
    return ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
        .apply {
            trackSelectionParameters =
                trackSelectionParameters
                    .buildUpon()
                    .setAudioOffloadPreferences(
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                            .build()
                    )
                    .build()
        }
        .let { CustomizedPlayer(it) }
}
