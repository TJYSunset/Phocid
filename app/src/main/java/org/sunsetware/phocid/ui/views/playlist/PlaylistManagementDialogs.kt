package org.sunsetware.phocid.ui.views.playlist

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.UNKNOWN
import org.sunsetware.phocid.data.Playlist
import org.sunsetware.phocid.data.RealizedPlaylist
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.sortedBy
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.UtilityCheckBoxListItem
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.utils.icuFormat

@Stable
class NewPlaylistDialog(private val tracks: List<Track> = emptyList()) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        var textFieldValue by rememberSaveable { mutableStateOf("") }
        DialogBase(
            title =
                if (tracks.isEmpty()) Strings[R.string.playlist_new]
                else Strings[R.string.playlist_new_with_tracks].icuFormat(tracks.size),
            onConfirm = {
                viewModel.playlistManager.addPlaylist(Playlist(textFieldValue).addTracks(tracks))
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
            confirmEnabled = textFieldValue.isNotEmpty(),
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = { Text(Strings[R.string.playlist_name]) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions {
                        if (textFieldValue.isNotEmpty()) {
                            viewModel.playlistManager.addPlaylist(Playlist(textFieldValue))
                            viewModel.uiManager.closeDialog()
                        }
                    },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Stable
class RenamePlaylistDialog(private val key: UUID) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        var textFieldValue by rememberSaveable {
            mutableStateOf(viewModel.playlistManager.playlists.value[key]?.displayName ?: "")
        }
        DialogBase(
            title = Strings[R.string.playlist_rename],
            onConfirm = {
                viewModel.playlistManager.updatePlaylist(key) { it.copy(name = textFieldValue) }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
            confirmEnabled = textFieldValue.isNotEmpty(),
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = { Text(Strings[R.string.playlist_rename_input_hint]) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions {
                        if (textFieldValue.isNotEmpty()) {
                            viewModel.playlistManager.updatePlaylist(key) {
                                it.copy(name = textFieldValue)
                            }
                            viewModel.uiManager.closeDialog()
                        }
                    },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Stable
class DeletePlaylistDialog(private val keys: Set<UUID>) : Dialog() {
    constructor(key: UUID) : this(setOf(key))

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val singlePlaylistName = rememberSaveable {
            viewModel.playlistManager.playlists.value[keys.first()]?.displayName ?: UNKNOWN
        }
        DialogBase(
            title =
                if (keys.size == 1) Strings[R.string.playlist_delete_single_dialog_title]
                else Strings[R.string.playlist_delete_multiple_dialog_title].icuFormat(keys.size),
            onConfirm = {
                keys.forEach { viewModel.playlistManager.removePlaylist(it) }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Text(
                if (keys.size == 1)
                    Strings[R.string.playlist_delete_single_dialog_body].icuFormat(
                        singlePlaylistName
                    )
                else Strings[R.string.playlist_delete_multiple_dialog_body].icuFormat(keys.size),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Stable
class AddToPlaylistDialog(private val tracks: List<Track> = emptyList()) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val playlists by viewModel.playlistManager.playlists.collectAsStateWithLifecycle()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val sortedPlaylists =
            remember(playlists, preferences) {
                playlists
                    .asIterable()
                    .sortedBy(
                        preferences.sortCollator,
                        RealizedPlaylist.CollectionSortingOptions.values.first().keys,
                        true,
                    ) {
                        it.value
                    }
                    .map { (key, playlist) ->
                        Triple(
                            key,
                            playlist.displayName,
                            if (tracks.size == 1)
                                playlist.entries.any { it.track?.id == tracks.first().id }
                            else false,
                        )
                    }
            }
        var selectedPlaylists by rememberSaveable { mutableStateOf(emptySet<String>()) }
        DialogBase(
            title = stringResource(R.string.playlist_add_to),
            onConfirm = {
                for (playlist in selectedPlaylists) {
                    viewModel.playlistManager.updatePlaylist(UUID.fromString(playlist)) { playlist
                        ->
                        playlist.addTracks(tracks)
                    }
                }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
            confirmEnabled = selectedPlaylists.isNotEmpty(),
        ) {
            LazyColumn {
                item {
                    UtilityCheckBoxListItem(
                        text = stringResource(R.string.playlist_new),
                        checked = false,
                        onCheckedChange = {
                            viewModel.uiManager.openDialog(NewPlaylistDialog(tracks))
                        },
                    )
                }
                items(sortedPlaylists, key = { (key, _, _) -> key.toString() }) {
                    (key, name, alreadyContainsTrack) ->
                    UtilityCheckBoxListItem(
                        text = name,
                        checked = selectedPlaylists.contains(key.toString()),
                        onCheckedChange = {
                            val key = key.toString()
                            if (!selectedPlaylists.contains(key)) selectedPlaylists + key
                            else selectedPlaylists - key
                        },
                        textModifier =
                            if (alreadyContainsTrack) Modifier.alpha(INACTIVE_ALPHA) else Modifier,
                    )
                }
            }
        }
    }
}

@Stable
class RemoveFromPlaylistDialog(private val playlistKey: UUID, private val trackKeys: Set<UUID>) :
    Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val playlistName = rememberSaveable {
            viewModel.playlistManager.playlists.value[playlistKey]?.displayName ?: UNKNOWN
        }
        DialogBase(
            title = Strings[R.string.playlist_remove_from_dialog_title].icuFormat(trackKeys.size),
            onConfirm = {
                viewModel.playlistManager.updatePlaylist(playlistKey) { playlist ->
                    playlist.copy(entries = playlist.entries.filter { !trackKeys.contains(it.key) })
                }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Text(
                Strings[R.string.playlist_remove_from_dialog_body].icuFormat(
                    trackKeys.size,
                    playlistName,
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
