package org.sunsetware.phocid

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
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

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val coroutineScope = MainScope()
    private val timerMutex = Mutex()
    @Volatile private var timerTarget = -1L
    @Volatile private var timerFinishLastTrack = true

    // Create your player and media session in the onCreate lifecycle event
    override fun onCreate() {
        super.onCreate()
        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
        val player = ExoPlayer.Builder(this).setAudioAttributes(audioAttributes, true).build()
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
                                SET_SHUFFLE_COMMAND -> {
                                    session.updateSessionExtras {
                                        putBoolean(SHUFFLE_KEY, args.getBoolean(SHUFFLE_KEY))
                                    }
                                    return Futures.immediateFuture(
                                        SessionResult(SessionResult.RESULT_SUCCESS)
                                    )
                                }

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
                                        .add(SessionCommand(SET_SHUFFLE_COMMAND, Bundle.EMPTY))
                                        .add(SessionCommand(SET_TIMER_COMMAND, Bundle.EMPTY))
                                        .build()
                                )
                                .build()
                        }
                    }
                )
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // The user dismissed the app from the recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {}

    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
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
}
