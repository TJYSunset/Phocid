package org.sunsetware.phocid.ui.views.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.R
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.HistoryStartContext
import org.sunsetware.phocid.data.PlayerManager
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.utils.icuFormat

@Serializable
enum class LibraryTrackClickAction(
    val stringId: Int,
    val icon: ImageVector?,
    val invoke:
        (
            tracks: List<Track>,
            index: Int,
            playerManager: PlayerManager,
            uiManager: UiManager,
            historySource: HistoryStartContext?,
        ) -> Unit,
) {
    OPEN_MENU(
        R.string.preferences_library_track_click_action_open_menu,
        null,
        { _, _, _, _, _ -> },
    ),
    PLAY_ALL(
        R.string.track_play_all,
        Icons.AutoMirrored.Filled.PlaylistPlay,
        { tracks, index, playerManager, _, historySource ->
            playerManager.setTracks(tracks, index, historySource)
        },
    ),
    PLAY(
        R.string.track_play,
        Icons.Filled.PlayArrow,
        { tracks, index, playerManager, _, historySource ->
            playerManager.setTracks(listOf(tracks[index]), 0, historySource)
        },
    ),
    PLAY_NEXT(
        R.string.track_play_next,
        Icons.Filled.ChevronRight,
        { tracks, index, playerManager, uiManager, _ ->
            playerManager.playNext(listOf(tracks[index]))
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
        },
    ),
    ADD_TO_QUEUE(
        R.string.track_add_to_queue,
        Icons.Filled.Add,
        { tracks, index, playerManager, uiManager, _ ->
            playerManager.addTracks(listOf(tracks[index]))
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
        },
    ),
}

inline fun LibraryTrackClickAction.invokeOrOpenMenu(
    tracks: List<Track>,
    index: Int,
    playerManager: PlayerManager,
    uiManager: UiManager,
    historySource: HistoryStartContext? = null,
    onOpenMenu: () -> Unit,
) {
    if (this == LibraryTrackClickAction.OPEN_MENU) onOpenMenu()
    else invoke(tracks, index, playerManager, uiManager, historySource)
}
