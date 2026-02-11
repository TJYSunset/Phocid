@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.TopLevelScreen
import org.sunsetware.phocid.UNKNOWN
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.sortedBy
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.Artwork
import org.sunsetware.phocid.ui.components.ArtworkImage
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.Scrollbar
import org.sunsetware.phocid.ui.components.SortingOptionPicker
import org.sunsetware.phocid.ui.components.rememberReorderController
import org.sunsetware.phocid.ui.views.MenuItem
import org.sunsetware.phocid.ui.views.playlistCollectionMenuItemsWithoutEdit
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.swap
import org.sunsetware.phocid.utils.toLocalizedString
import sh.calvin.reorderable.ReorderableItem

@Stable
class PlaylistEditScreen(private val playlistKey: UUID) : TopLevelScreen() {
    private val lazyListState = LazyListState()

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val uiManager = viewModel.uiManager
        val playlistManager = viewModel.playlistManager
        val playlists by playlistManager.playlists.collectAsStateWithLifecycle()
        val playlist = playlists[playlistKey]
        val playlistEntries = playlist?.entries ?: emptyList()
        val playlistName by
            remember {
                    playlistManager.playlists.map { it[playlistKey]?.displayName }.filterNotNull()
                }
                .collectAsState(playlist?.displayName)
        val reorderController =
            rememberReorderController(
                lazyListState = lazyListState,
                items = playlistEntries,
                keySelector = { it.key },
                onCommitMove = { from, to ->
                    playlistManager.updatePlaylist(playlistKey) {
                        it.copy(entries = it.entries.toMutableList().apply { add(to, removeAt(from)) })
                    }
                },
            )

        LaunchedEffect(playlist) {
            if (playlist == null) {
                uiManager.closeTopLevelScreen(this@PlaylistEditScreen)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            Strings[R.string.playlist_edit_screen_title].icuFormat(playlistName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { uiManager.back() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Strings[R.string.commons_back],
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                uiManager.openDialog(PlaylistEditScreenSortDialog(playlistKey))
                            }
                        ) {
                            Icon(Icons.Filled.SortByAlpha, Strings[R.string.playlist_sort])
                        }
                        OverflowMenu(
                            listOf(
                                MenuItem.Button(
                                    Strings[R.string.playlist_remove_invalid_tracks],
                                    Icons.Filled.DeleteSweep,
                                ) {
                                    if (playlist != null) {
                                        val trackKeys =
                                            playlist.entries
                                                .filter { it.track == null }
                                                .map { it.key }
                                                .toSet()
                                        if (trackKeys.isNotEmpty()) {
                                            uiManager.openDialog(
                                                RemoveFromPlaylistDialog(playlistKey, trackKeys)
                                            )
                                        }
                                    }
                                }
                            ) + playlistCollectionMenuItemsWithoutEdit(playlistKey, uiManager)
                        )
                    },
                )
            }
        ) { scaffoldPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Scrollbar(
                    lazyListState,
                    { (it + 1).toLocalizedString() },
                    preferences.alwaysShowHintOnScroll,
                ) {
                    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            reorderController.reorderingItems ?: playlistEntries,
                            { _, entry -> entry.key },
                        ) { index, entry ->
                            ReorderableItem(
                                reorderController.reorderableLazyListState,
                                entry.key,
                                animateItemModifier =
                                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            ) {
                                Box {
                                    LibraryListItemHorizontal(
                                        title = entry.track?.displayTitle ?: UNKNOWN,
                                        subtitle =
                                            entry.track?.displayArtistWithAlbum
                                                ?: FilenameUtils.getName(entry.playlistEntry.path),
                                        lead = {
                                            ArtworkImage(
                                                artwork =
                                                    Artwork.Track(entry.track ?: InvalidTrack),
                                                artworkColorPreference =
                                                    preferences.artworkColorPreference,
                                                shape = preferences.shapePreference.artworkShape,
                                                highRes =
                                                    preferences.highResArtworkPreference.small,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        },
                                        actions = {
                                            IconButton(
                                                onClick = {
                                                    playlistManager.updatePlaylist(playlistKey) {
                                                        if (index > 0) {
                                                            it.copy(
                                                                entries =
                                                                    it.entries.swap(
                                                                        index,
                                                                        index - 1,
                                                                    )
                                                            )
                                                        } else it
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.ArrowUpward,
                                                    contentDescription =
                                                        Strings[R.string.list_move_up],
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    playlistManager.updatePlaylist(playlistKey) {
                                                        if (index < it.entries.size - 1) {
                                                            it.copy(
                                                                entries =
                                                                    it.entries.swap(
                                                                        index,
                                                                        index + 1,
                                                                    )
                                                            )
                                                        } else it
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.ArrowDownward,
                                                    contentDescription =
                                                        Strings[R.string.list_move_down],
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    uiManager.openDialog(
                                                        RemoveFromPlaylistDialog(
                                                            playlistKey,
                                                            setOf(entry.key),
                                                        )
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.Remove,
                                                    contentDescription =
                                                        Strings[R.string.commons_remove],
                                                )
                                            }
                                        },
                                        dragIndicator = true,
                                        modifier =
                                            Modifier.background(
                                                MaterialTheme.colorScheme.background
                                            ),
                                    )
                                    Box(
                                        modifier =
                                            Modifier.align(Alignment.CenterStart)
                                                .width(56.dp)
                                                .height(72.dp)
                                                .draggableHandle(
                                                    onDragStarted =
                                                        reorderController.onDragStarted,
                                                    onDragStopped =
                                                        reorderController.onDragStopped,
                                                )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // https://issuetracker.google.com/issues/209652366#comment35
        SideEffect {
            lazyListState.requestScrollToItem(
                index = lazyListState.firstVisibleItemIndex,
                scrollOffset = lazyListState.firstVisibleItemScrollOffset,
            )
        }
    }
}

@Stable
class PlaylistEditScreenSortDialog(private val playlistKey: UUID) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val playlistManager = viewModel.playlistManager
        val playlists by playlistManager.playlists.collectAsStateWithLifecycle()

        var activeSortingOptionId by remember { mutableStateOf(Track.SortingOptions.keys.first()) }
        var sortAscending by remember { mutableStateOf(true) }
        DialogBase(
            title =
                Strings[R.string.playlist_sort_screen_title].icuFormat(
                    playlists[playlistKey]?.displayName
                ),
            onConfirm = {
                playlistManager.updatePlaylist(playlistKey) {
                    val trackIndex =
                        viewModel.libraryIndex.value.tracks.values.associateBy { it.path }
                    it.copy(
                        entries =
                            it.entries.sortedBy(
                                viewModel.preferences.value.sortCollator,
                                Track.SortingOptions[activeSortingOptionId]!!.keys,
                                sortAscending,
                            ) {
                                trackIndex[it.path] ?: InvalidTrack
                            }
                    )
                }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            SortingOptionPicker(
                Track.SortingOptions,
                activeSortingOptionId = activeSortingOptionId,
                sortAscending = sortAscending,
                onSetSortingOption = { activeSortingOptionId = it },
                onSetSortAscending = { sortAscending = it },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
            )
        }
    }
}
