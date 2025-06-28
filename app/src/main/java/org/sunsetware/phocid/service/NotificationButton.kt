@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")
@file:OptIn(UnstableApi::class)

package org.sunsetware.phocid.service

import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import org.sunsetware.phocid.EXTERNAL_FAVORITE_COMMAND
import org.sunsetware.phocid.EXTERNAL_REPEAT_COMMAND
import org.sunsetware.phocid.EXTERNAL_SHUFFLE_COMMAND
import org.sunsetware.phocid.R
import org.sunsetware.phocid.globals.Strings

enum class NotificationButton(
    val stringId: Int,
    val build: (Player, currentTrackIsFavorite: Boolean) -> CommandButton,
) {
    REPEAT(
        R.string.preferences_notification_button_repeat,
        { player, _ ->
            CommandButton.Builder(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                        Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                        else -> CommandButton.ICON_REPEAT_OFF
                    }
                )
                .setDisplayName(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ALL -> Strings[R.string.player_repeat_mode_all]
                        Player.REPEAT_MODE_ONE -> Strings[R.string.player_repeat_mode_one]
                        else -> Strings[R.string.player_repeat_mode_off]
                    }
                )
                .setSessionCommand(SessionCommand(EXTERNAL_REPEAT_COMMAND, bundleOf()))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        },
    ),
    SHUFFLE(
        R.string.preferences_notification_button_shuffle,
        { player, _ ->
            CommandButton.Builder(
                    if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON
                    else CommandButton.ICON_SHUFFLE_OFF
                )
                .setDisplayName(
                    if (player.shuffleModeEnabled) Strings[R.string.player_shuffle_on]
                    else Strings[R.string.player_shuffle_off]
                )
                .setSessionCommand(SessionCommand(EXTERNAL_SHUFFLE_COMMAND, bundleOf()))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        },
    ),
    FAVORITE(
        R.string.preferences_notification_button_favorite,
        { _, currentTrackIsFavorite ->
            CommandButton.Builder(
                    if (currentTrackIsFavorite) CommandButton.ICON_HEART_FILLED
                    else CommandButton.ICON_HEART_UNFILLED
                )
                .setDisplayName(
                    if (currentTrackIsFavorite)
                        Strings[R.string.player_now_playing_remove_favorites]
                    else Strings[R.string.player_now_playing_add_favorites]
                )
                .setSessionCommand(SessionCommand(EXTERNAL_FAVORITE_COMMAND, bundleOf()))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        },
    ),
}
