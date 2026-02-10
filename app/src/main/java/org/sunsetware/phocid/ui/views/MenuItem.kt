package org.sunsetware.phocid.ui.views

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.pm.ShortcutManagerCompat
import java.util.UUID
import org.sunsetware.phocid.R
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.PlayerManager
import org.sunsetware.phocid.data.SpecialPlaylistLookup
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.albumKey
import org.sunsetware.phocid.data.HistoryStartContext
import org.sunsetware.phocid.data.playlistShortcut
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.views.library.LibraryTrackClickAction
import org.sunsetware.phocid.ui.views.library.openAlbumCollectionView
import org.sunsetware.phocid.ui.views.library.openArtistCollectionView
import org.sunsetware.phocid.ui.views.playlist.AddToPlaylistDialog
import org.sunsetware.phocid.ui.views.playlist.DeletePlaylistDialog
import org.sunsetware.phocid.ui.views.playlist.PlaylistEditScreen
import org.sunsetware.phocid.ui.views.playlist.PlaylistIoScreen
import org.sunsetware.phocid.ui.views.playlist.RemoveFromPlaylistDialog
import org.sunsetware.phocid.ui.views.playlist.RenamePlaylistDialog
import org.sunsetware.phocid.utils.icuFormat

@Immutable
sealed class MenuItem {
    @Immutable
    data class Button(
        val text: String,
        val icon: ImageVector,
        val dangerous: Boolean = false,
        val onClick: () -> Unit,
    ) : MenuItem()

    @Immutable object Divider : MenuItem()
}

@Stable
fun trackMenuItems(
    track: Track,
    playerManager: PlayerManager,
    uiManager: UiManager,
): List<MenuItem> {
    val queue =
        listOf(
            MenuItem.Button(Strings[R.string.track_play_next], Icons.Filled.ChevronRight) {
                playerManager.playNext(listOf(track))
                uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
            },
            MenuItem.Button(Strings[R.string.track_add_to_queue], Icons.Filled.Add) {
                playerManager.addTracks(listOf(track))
                uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
            },
        )
    val playlist =
        MenuItem.Button(Strings[R.string.playlist_add_to], Icons.AutoMirrored.Filled.PlaylistAdd) {
            uiManager.openDialog(AddToPlaylistDialog(listOf(track)))
        }
    val share =
        MenuItem.Button(Strings[R.string.track_share], Icons.Filled.Share) {
            uiManager.intentLauncher.get()?.share(listOf(track))
        }
    val artists =
        track.artists.map { artist ->
            MenuItem.Button(
                Strings[R.string.track_view_artist].icuFormat(artist),
                Icons.Filled.Person,
            ) {
                uiManager.openArtistCollectionView(artist)
            }
        }
    val album =
        listOfNotNull(
            track.albumKey?.let { albumKey ->
                MenuItem.Button(
                    Strings[R.string.track_view_album].icuFormat(albumKey.name),
                    Icons.Filled.Album,
                ) {
                    uiManager.openAlbumCollectionView(albumKey)
                }
            }
        )
    val radio =
        MenuItem.Button(Strings[R.string.track_start_radio], Icons.Filled.Radio) {
            playerManager.startRadio(track)
            uiManager.toast(Strings[R.string.toast_radio_started])
        }
    val details =
        MenuItem.Button(Strings[R.string.track_details], Icons.Filled.Info) {
            uiManager.openDialog(TrackDetailsDialog(track))
        }

    return queue + playlist + radio + share + MenuItem.Divider + artists + album + details
}

@Stable
inline fun trackMenuItemsLibrary(
    track: Track,
    crossinline playContext: () -> Pair<List<Track>, Int>,
    playerManager: PlayerManager,
    uiManager: UiManager,
    historySource: HistoryStartContext? = null,
): List<MenuItem> {
    return listOf(LibraryTrackClickAction.PLAY_ALL, LibraryTrackClickAction.PLAY).map {
        MenuItem.Button(Strings[it.stringId], it.icon!!) {
            val (tracks, index) = playContext()
            it.invoke(tracks, index, playerManager, uiManager, historySource)
        }
    } + trackMenuItems(track, playerManager, uiManager)
}

@Stable
inline fun collectionMenuItemsWithoutPlay(
    crossinline tracks: () -> List<Track>,
    playerManager: PlayerManager,
    uiManager: UiManager,
    crossinline continuation: () -> Unit = {},
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.track_play_next], Icons.Filled.ChevronRight) {
            val tracks = tracks()
            playerManager.playNext(tracks)
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(tracks.size))
            continuation()
        },
        MenuItem.Button(Strings[R.string.track_add_to_queue], Icons.Filled.Add) {
            val tracks = tracks()
            playerManager.addTracks(tracks)
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(tracks.size))
            continuation()
        },
        MenuItem.Button(Strings[R.string.playlist_add_to], Icons.AutoMirrored.Filled.PlaylistAdd) {
            uiManager.openDialog(AddToPlaylistDialog(tracks()))
            continuation()
        },
        MenuItem.Button(Strings[R.string.track_share], Icons.Filled.Share) {
            uiManager.intentLauncher.get()?.share(tracks())
            continuation()
        },
    )
}

@Stable
inline fun collectionMenuItems(
    crossinline tracks: () -> List<Track>,
    playerManager: PlayerManager,
    uiManager: UiManager,
    historySource: HistoryStartContext? = null,
    crossinline continuation: () -> Unit = {},
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.track_play], Icons.Filled.PlayArrow) {
            playerManager.setTracks(tracks(), null, historySource)
            continuation()
        }
    ) + collectionMenuItemsWithoutPlay(tracks, playerManager, uiManager, continuation)
}

@Stable
fun playlistCollectionMenuItemsWithoutEdit(key: UUID, uiManager: UiManager): List<MenuItem.Button> {
    return listOfNotNull(
        if (!SpecialPlaylistLookup.containsKey(key)) {
            MenuItem.Button(
                Strings[R.string.playlist_rename],
                Icons.Filled.DriveFileRenameOutline,
            ) {
                uiManager.openDialog(RenamePlaylistDialog(key))
            }
        } else null,
        MenuItem.Button(Strings[R.string.playlist_export], Icons.Outlined.FileUpload) {
            uiManager.openTopLevelScreen(PlaylistIoScreen.export(setOf(key)))
        },
        MenuItem.Button(Strings[R.string.playlist_delete], Icons.Filled.Delete, dangerous = true) {
            uiManager.openDialog(DeletePlaylistDialog(key))
        },
    )
}

@Stable
fun playlistCollectionMenuItems(
    key: UUID,
    name: String,
    context: Context,
    uiManager: UiManager,
): List<MenuItem> {
    return listOf(
        MenuItem.Button(Strings[R.string.playlist_edit], Icons.Filled.Edit) {
            uiManager.openTopLevelScreen(PlaylistEditScreen(key))
        }
    ) +
        playlistCollectionMenuItemsWithoutEdit(key, uiManager) +
        listOf(
            MenuItem.Divider,
            MenuItem.Button(
                Strings[R.string.playlist_create_shortcut],
                Icons.AutoMirrored.Filled.AddToHomeScreen,
            ) {
                ShortcutManagerCompat.requestPinShortcut(
                    context,
                    playlistShortcut(context, "pinnedPlaylist", key, name),
                    null,
                )
            },
        )
}

@Stable
inline fun playlistCollectionMultiSelectMenuItems(
    crossinline keys: () -> Set<UUID>,
    uiManager: UiManager,
    crossinline continuation: () -> Unit,
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.playlist_export], Icons.Outlined.FileUpload) {
            uiManager.openTopLevelScreen(PlaylistIoScreen.export(keys()))
            continuation()
        },
        MenuItem.Button(Strings[R.string.playlist_delete], Icons.Filled.Delete, dangerous = true) {
            uiManager.openDialog(DeletePlaylistDialog(keys()))
            continuation()
        },
    )
}

@Stable
inline fun playlistTrackMenuItems(
    playlistKey: UUID,
    crossinline trackKeys: () -> Set<UUID>,
    uiManager: UiManager,
    crossinline continuation: () -> Unit = {},
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.playlist_remove_from], Icons.Filled.PlaylistRemove) {
            uiManager.openDialog(RemoveFromPlaylistDialog(playlistKey, trackKeys()))
            continuation()
        }
    )
}

@Stable
fun playlistTrackMenuItems(
    playlistKey: UUID,
    trackKey: UUID,
    uiManager: UiManager,
): List<MenuItem.Button> {
    return playlistTrackMenuItems(playlistKey, { setOf(trackKey) }, uiManager)
}
