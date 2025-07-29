@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.application
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.data.Album
import org.sunsetware.phocid.data.AlbumArtist
import org.sunsetware.phocid.data.Artist
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.Folder
import org.sunsetware.phocid.data.Genre
import org.sunsetware.phocid.data.HighResArtworkPreference
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.PlaylistManager
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.RealizedPlaylist
import org.sunsetware.phocid.data.SortingOption
import org.sunsetware.phocid.data.TabStylePreference
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.hint
import org.sunsetware.phocid.data.hintBy
import org.sunsetware.phocid.data.search
import org.sunsetware.phocid.data.sorted
import org.sunsetware.phocid.data.sortedBy
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.globals.format
import org.sunsetware.phocid.ui.components.Artwork
import org.sunsetware.phocid.ui.components.ArtworkImage
import org.sunsetware.phocid.ui.components.DefaultPagerState
import org.sunsetware.phocid.ui.components.EmptyListIndicator
import org.sunsetware.phocid.ui.components.LibraryListItemCard
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.MultiSelectManager
import org.sunsetware.phocid.ui.components.MultiSelectState
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.Scrollbar
import org.sunsetware.phocid.ui.components.SelectableList
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.components.TabIndicator
import org.sunsetware.phocid.ui.components.multiSelectClickable
import org.sunsetware.phocid.ui.theme.hashColor
import org.sunsetware.phocid.ui.views.MenuItem
import org.sunsetware.phocid.ui.views.collectionMenuItems
import org.sunsetware.phocid.ui.views.playlistCollectionMenuItems
import org.sunsetware.phocid.ui.views.playlistCollectionMultiSelectMenuItems
import org.sunsetware.phocid.ui.views.trackMenuItemsLibrary
import org.sunsetware.phocid.utils.combine

@Immutable
data class LibraryScreenHomeViewItem(
    val key: Any,
    val title: String,
    val subtitle: String,
    val scrollHint: String,
    val artwork: Artwork,
    val tracks: () -> List<Track>,
    val menuItems: (MainViewModel) -> List<MenuItem>,
    val multiSelectMenuItems:
        (
            others: List<LibraryScreenHomeViewItem>,
            viewModel: MainViewModel,
            continuation: () -> Unit,
        ) -> List<MenuItem.Button>,
    val onClick: (viewModel: MainViewModel, onOpenMenu: () -> Unit) -> Unit,
) : LibraryScreenItem<LibraryScreenHomeViewItem> {
    override fun getMultiSelectMenuItems(
        others: List<LibraryScreenHomeViewItem>,
        viewModel: MainViewModel,
        continuation: () -> Unit,
    ): List<MenuItem.Button> {
        return multiSelectMenuItems(others, viewModel, continuation)
    }
}

class LibraryScreenHomeViewState(
    coroutineScope: CoroutineScope,
    preferences: StateFlow<Preferences>,
    libraryIndex: StateFlow<LibraryIndex>,
    playlistManager: PlaylistManager,
    searchQuery: StateFlow<String>,
) : AutoCloseable {
    val pagerState = DefaultPagerState { preferences.value.tabs.size }
    val tabStates =
        LibraryScreenTabType.entries.associateWith { tabType ->
            val items =
                if (tabType != LibraryScreenTabType.PLAYLISTS) {
                    preferences.combine(
                        coroutineScope,
                        libraryIndex,
                        searchQuery,
                        transform =
                            when (tabType) {
                                LibraryScreenTabType.TRACKS -> ::trackItems
                                LibraryScreenTabType.ALBUMS -> ::albumItems
                                LibraryScreenTabType.ARTISTS -> ::artistItems
                                LibraryScreenTabType.ALBUM_ARTISTS -> ::albumArtistItems
                                LibraryScreenTabType.GENRES -> ::genreItems
                                LibraryScreenTabType.FOLDERS -> ::folderItems
                                LibraryScreenTabType.PLAYLISTS -> throw Error() // Impossible
                            },
                    )
                } else {
                    preferences.combine(
                        coroutineScope,
                        playlistManager.playlists,
                        searchQuery,
                        transform = ::playlistItems,
                    )
                }

            LibraryScreenHomeViewTabState(MultiSelectState(coroutineScope, items))
        }
    val tabRowScrollState = ScrollState(0)
    private val _activeMultiSelectState =
        MutableStateFlow(null as MultiSelectState<LibraryScreenHomeViewItem>?)
    val activeMultiSelectState = _activeMultiSelectState.asStateFlow()
    private val activeMultiSelectStateJobs =
        tabStates.map { (tabType, tabState) ->
            coroutineScope.launch {
                tabState.multiSelectState.items
                    .onEach { items ->
                        if (items.selection.isNotEmpty()) {
                            tabStates
                                .filterKeys { it != tabType }
                                .values
                                .forEach { it.multiSelectState.clearSelection() }
                            _activeMultiSelectState.update { tabState.multiSelectState }
                        }
                    }
                    .collect()
            }
        }

    override fun close() {
        activeMultiSelectStateJobs.forEach { it.cancel() }
        tabStates.values.forEach { it.close() }
    }

    private fun trackItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.TRACKS]!!
        val tracks =
            libraryIndex.tracks.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
                .hint(preferences.sortCollator, tab.sortingKeys)
        return tracks.mapIndexed { index, (track, hint) ->
            LibraryScreenHomeViewItem(
                key = track.id,
                title = track.displayTitle,
                subtitle = track.displayArtistWithAlbum,
                scrollHint = hint,
                artwork = Artwork.Track(track),
                tracks = { listOf(track) },
                menuItems = {
                    trackMenuItemsLibrary(
                        track,
                        { tracks.map { it.first } to index },
                        it.playerManager,
                        it.uiManager,
                    )
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { listOf(track) + others.flatMap { it.tracks() } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) { viewModel, onOpenMenu ->
                viewModel.preferences.value.libraryTrackClickAction.invokeOrOpenMenu(
                    tracks.map { it.first },
                    index,
                    viewModel.playerManager,
                    viewModel.uiManager,
                    onOpenMenu,
                )
            }
        }
    }

    private fun albumItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.ALBUMS]!!
        val albums =
            libraryIndex.albums
                .asIterable()
                .search(searchQuery, preferences.searchCollator) { it.value }
                .sortedBy(preferences.sortCollator, tab.sortingKeys, tab.sortAscending) { it.value }
                .hintBy(preferences.sortCollator, tab.sortingKeys) { it.value }
        return albums.map { (pair, hint) ->
            val (key, album) = pair
            LibraryScreenHomeViewItem(
                key = key.toString(),
                title = album.name,
                subtitle = album.displayAlbumArtist,
                scrollHint = hint,
                artwork = Artwork.Track(album.tracks.firstOrNull() ?: InvalidTrack),
                tracks = { album.tracks },
                menuItems = {
                    collectionMenuItems({ album.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { album.tracks + others.flatMap { it.tracks() } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) { viewModel, _ ->
                viewModel.uiManager.openAlbumCollectionView(key)
            }
        }
    }

    private fun artistItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.ARTISTS]!!
        val artists =
            libraryIndex.artists.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
                .hint(preferences.sortCollator, tab.sortingKeys)
        return artists.map { (artist, hint) ->
            LibraryScreenHomeViewItem(
                key = artist.name,
                title = artist.name,
                subtitle = artist.displayStatistics,
                scrollHint = hint,
                artwork = Artwork.Track(artist.tracks.firstOrNull() ?: InvalidTrack),
                tracks = { artist.tracks },
                menuItems = {
                    collectionMenuItems({ artist.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { artist.tracks + others.flatMap { it.tracks() } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) { viewModel, _ ->
                viewModel.uiManager.openArtistCollectionView(artist.name)
            }
        }
    }

    private fun albumArtistItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.ALBUM_ARTISTS]!!
        val albumArtists =
            libraryIndex.albumArtists.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
                .hint(preferences.sortCollator, tab.sortingKeys)
        return albumArtists.map { (albumArtist, hint) ->
            LibraryScreenHomeViewItem(
                key = albumArtist.name,
                title = albumArtist.name,
                subtitle = albumArtist.displayStatistics,
                scrollHint = hint,
                artwork = Artwork.Track(albumArtist.tracks.firstOrNull() ?: InvalidTrack),
                tracks = { albumArtist.tracks },
                menuItems = {
                    collectionMenuItems({ albumArtist.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { albumArtist.tracks + others.flatMap { it.tracks() } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) { viewModel, _ ->
                viewModel.uiManager.openAlbumArtistCollectionView(albumArtist.name)
            }
        }
    }

    private fun genreItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.GENRES]!!
        val genres =
            libraryIndex.genres.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
                .hint(preferences.sortCollator, tab.sortingKeys)
        return genres.map { (genre, hint) ->
            LibraryScreenHomeViewItem(
                key = genre.name,
                title = genre.name,
                subtitle = genre.displayStatistics,
                scrollHint = hint,
                artwork = Artwork.Track(genre.tracks.firstOrNull() ?: InvalidTrack),
                tracks = { genre.tracks },
                menuItems = {
                    collectionMenuItems({ genre.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { genre.tracks + others.flatMap { it.tracks() } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) { viewModel, _ ->
                viewModel.uiManager.openGenreCollectionView(genre.name)
            }
        }
    }

    private fun folderItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.FOLDERS]!!
        val rootFolder =
            libraryIndex.folders[preferences.folderTabRoot]
                ?: libraryIndex.folders[libraryIndex.defaultRootFolder]!!
        val (folders, tracks) =
            if (searchQuery.isNotEmpty()) {
                libraryIndex.folders[libraryIndex.defaultRootFolder]!!.childItemsRecursive(
                    libraryIndex.folders
                )
            } else {
                (rootFolder.childFolders.map { libraryIndex.folders[it]!! } to
                    rootFolder.childTracks)
            }
        val filteredChildFolders =
            folders
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
                .hint(preferences.sortCollator, tab.sortingKeys)
        // Sorting is required here because onClick is "baked" with this order.
        val filteredSortedChildTracks =
            tracks
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
                .hint(preferences.sortCollator, tab.sortingKeys)
        val folderItems =
            filteredChildFolders.map { (folder, hint) ->
                folder to
                    LibraryScreenHomeViewItem(
                        key = folder.path,
                        title = folder.fileName,
                        subtitle = folder.displayStatistics,
                        scrollHint = hint,
                        artwork = Artwork.Icon(Icons.Outlined.Folder, folder.path.hashColor()),
                        tracks = { folder.childTracksRecursive(libraryIndex.folders) },
                        menuItems = {
                            collectionMenuItems(
                                { folder.childTracksRecursive(libraryIndex.folders) },
                                it.playerManager,
                                it.uiManager,
                            )
                        },
                        multiSelectMenuItems = { others, viewModel, continuation ->
                            collectionMenuItems(
                                {
                                    folder.childTracksRecursive(libraryIndex.folders) +
                                        others.flatMap { it.tracks() }
                                },
                                viewModel.playerManager,
                                viewModel.uiManager,
                                continuation,
                            )
                        },
                    ) { viewModel, _ ->
                        viewModel.uiManager.openFolderCollectionView(folder.path)
                    }
            }
        val trackItems =
            filteredSortedChildTracks.mapIndexed { index, (track, hint) ->
                track to
                    LibraryScreenHomeViewItem(
                        key = track.id,
                        title = track.fileName,
                        subtitle = track.duration.format(),
                        scrollHint = hint,
                        artwork = Artwork.Track(track),
                        tracks = { listOf(track) },
                        menuItems = {
                            trackMenuItemsLibrary(
                                track,
                                { filteredSortedChildTracks.map { it.first } to index },
                                it.playerManager,
                                it.uiManager,
                            )
                        },
                        multiSelectMenuItems = { others, viewModel, continuation ->
                            collectionMenuItems(
                                { listOf(track) + others.flatMap { it.tracks() } },
                                viewModel.playerManager,
                                viewModel.uiManager,
                                continuation,
                            )
                        },
                    ) { viewModel, onOpenMenu ->
                        viewModel.preferences.value.libraryTrackClickAction.invokeOrOpenMenu(
                            filteredSortedChildTracks.map { it.first },
                            index,
                            viewModel.playerManager,
                            viewModel.uiManager,
                            onOpenMenu,
                        )
                    }
            }
        return (folderItems + trackItems)
            .sortedBy(preferences.sortCollator, tab.sortingKeys, tab.sortAscending) { it.first }
            .map { it.second }
    }

    private fun playlistItems(
        preferences: Preferences,
        playlists: Map<UUID, RealizedPlaylist>,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.PLAYLISTS]!!
        val filteredSortedPlaylists =
            playlists
                .asIterable()
                .search(searchQuery, preferences.searchCollator) { it.value }
                .sortedBy(preferences.sortCollator, tab.sortingKeys, tab.sortAscending) { it.value }
                .hintBy(preferences.sortCollator, tab.sortingKeys) { it.value }
        return filteredSortedPlaylists.map { (pair, hint) ->
            val (key, playlist) = pair
            LibraryScreenHomeViewItem(
                key = key,
                title = playlist.displayName,
                subtitle = playlist.displayStatistics,
                scrollHint = hint,
                artwork =
                    playlist.specialType?.let { Artwork.Icon(it.icon, it.color) }
                        ?: Artwork.Track(playlist.entries.firstOrNull()?.track ?: InvalidTrack),
                tracks = { playlist.validTracks },
                menuItems = {
                    collectionMenuItems({ playlist.validTracks }, it.playerManager, it.uiManager) +
                        MenuItem.Divider +
                        playlistCollectionMenuItems(
                            key,
                            playlist.displayName,
                            it.application.applicationContext,
                            it.uiManager,
                        )
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { playlist.validTracks + others.flatMap { it.tracks() } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    ) +
                        playlistCollectionMultiSelectMenuItems(
                            { setOf(key) + others.map { it.key as UUID } },
                            viewModel.uiManager,
                            continuation,
                        )
                },
            ) { viewModel, _ ->
                viewModel.uiManager.openPlaylistCollectionView(key)
            }
        }
    }
}

@Stable
data class LibraryScreenHomeViewTabState(
    val multiSelectState: MultiSelectState<LibraryScreenHomeViewItem>,
    val lazyGridState: LazyGridState = LazyGridState(0, 0),
) : AutoCloseable {
    override fun close() {
        multiSelectState.close()
    }
}

@Composable
fun LibraryScreenHomeView(
    state: LibraryScreenHomeViewState,
    viewModel: MainViewModel = viewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val pagerState = state.pagerState

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            ViewTabRow(preferences, state)
            HorizontalPager(state = pagerState) { i ->
                if (preferences.tabs.size > i && state.tabStates.size > i) {
                    val tab = preferences.tabs[i]
                    val (multiSelectState, lazyGridState) = state.tabStates[tab.type]!!
                    val items by multiSelectState.items.collectAsStateWithLifecycle()
                    LibraryList(
                        gridState = lazyGridState,
                        gridSize = tab.gridSize,
                        items = items,
                        multiSelectManager = multiSelectState,
                        highResArtworkPreference = preferences.highResArtworkPreference,
                        artworkColorPreference = preferences.artworkColorPreference,
                        artworkShape = preferences.shapePreference.artworkShape,
                        cardShape = preferences.shapePreference.cardShape,
                        coloredCards = preferences.coloredCards,
                        alwaysShowHintOnScroll = preferences.alwaysShowHintOnScroll,
                    )
                } else {
                    // Not providing a composable will cause internal crash in pager
                    Box {}
                }
            }
        }
    }
}

@Composable
private fun ViewTabRow(preferences: Preferences, state: LibraryScreenHomeViewState) {
    val coroutineScope = rememberCoroutineScope()
    val currentTabIndex = state.pagerState.targetPage.coerceIn(0, preferences.tabs.size - 1)

    @Composable
    fun tabs() {
        preferences.tabs.forEachIndexed { i, tab ->
            Tab(
                selected = i == currentTabIndex,
                onClick = {
                    if (state.pagerState.targetPage == i) {
                        coroutineScope.launch {
                            state.tabStates[tab.type]?.lazyGridState?.animateScrollToItem(0)
                        }
                    } else {
                        coroutineScope.launch { state.pagerState.animateScrollToPage(i) }
                    }
                },
                text =
                    if (preferences.tabStyle == TabStylePreference.ICON_ONLY) {
                        null
                    } else {
                        {
                            CompositionLocalProvider(
                                LocalContentColor provides
                                    if (i == currentTabIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (preferences.tabStyle == TabStylePreference.TEXT_AND_ICON) {
                                        Icon(
                                            tab.type.icon,
                                            null,
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }

                                    SingleLineText(
                                        Strings[tab.type.stringId],
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                icon =
                    if (preferences.tabStyle != TabStylePreference.ICON_ONLY) {
                        null
                    } else {
                        {
                            Icon(
                                tab.type.icon,
                                contentDescription = Strings[tab.type.stringId],
                                tint =
                                    if (i == currentTabIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (preferences.scrollableTabs) {
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())
            PrimaryScrollableTabRow(
                scrollState = state.tabRowScrollState,
                selectedTabIndex = currentTabIndex,
                indicator = { TabIndicator(state.pagerState) },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs()
            }
        } else {
            PrimaryTabRow(
                selectedTabIndex = currentTabIndex,
                indicator = { TabIndicator(state.pagerState) },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs()
            }
        }
    }
}

@Composable
private fun LibraryList(
    gridState: LazyGridState,
    gridSize: Int,
    items: SelectableList<LibraryScreenHomeViewItem>,
    multiSelectManager: MultiSelectManager,
    highResArtworkPreference: HighResArtworkPreference,
    artworkColorPreference: ArtworkColorPreference,
    artworkShape: Shape,
    cardShape: Shape,
    coloredCards: Boolean,
    alwaysShowHintOnScroll: Boolean,
) {
    val viewModel = viewModel<MainViewModel>()
    val haptics = LocalHapticFeedback.current

    if (items.isEmpty()) {
        EmptyListIndicator()
    } else if (gridSize == 0) {
        Scrollbar(gridState, { items.getOrNull(it)?.value?.scrollHint }, alwaysShowHintOnScroll) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, { _, (info, _) -> info.key }) { index, (info, selected) ->
                    with(info) {
                        var menuState = remember { mutableStateOf(false) }
                        LibraryListItemHorizontal(
                            title = title,
                            subtitle = subtitle,
                            lead = {
                                ArtworkImage(
                                    artwork = artwork,
                                    artworkColorPreference = artworkColorPreference,
                                    shape = artworkShape,
                                    highRes = highResArtworkPreference.small,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            actions = { OverflowMenu(menuItems(viewModel), state = menuState) },
                            modifier =
                                Modifier.multiSelectClickable(
                                        items,
                                        index,
                                        multiSelectManager,
                                        haptics,
                                    ) {
                                        info.onClick(viewModel) { menuState.value = true }
                                    }
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null),
                            selected = selected,
                        )
                    }
                }
            }
        }
    } else {
        Scrollbar(gridState, { items.getOrNull(it)?.value?.scrollHint }, alwaysShowHintOnScroll) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridSize),
                contentPadding = PaddingValues(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, { _, (info, _) -> info.key }) { index, (info, selected) ->
                    with(info) {
                        var menuState = remember { mutableStateOf(false) }
                        OverflowMenu(menuItems(viewModel), state = menuState)
                        LibraryListItemCard(
                            title = title,
                            subtitle = subtitle,
                            color =
                                if (coloredCards) artwork.getColor(artworkColorPreference)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = cardShape,
                            image = {
                                ArtworkImage(
                                    artwork = artwork,
                                    artworkColorPreference = artworkColorPreference,
                                    shape = RoundedCornerShape(0.dp),
                                    highRes = highResArtworkPreference.library,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            modifier =
                                Modifier.padding(2.dp)
                                    .multiSelectClickable(
                                        items,
                                        index,
                                        multiSelectManager,
                                        haptics,
                                    ) {
                                        info.onClick(viewModel) { menuState.value = true }
                                    }
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null),
                            selected = selected,
                        )
                    }
                }
            }
        }
    }

    // https://issuetracker.google.com/issues/209652366#comment35
    SideEffect {
        gridState.requestScrollToItem(
            index = gridState.firstVisibleItemIndex,
            scrollOffset = gridState.firstVisibleItemScrollOffset,
        )
    }
}

@Immutable
@Serializable
data class LibraryScreenTabInfo(
    val type: LibraryScreenTabType,
    val gridSize: Int = 0,
    val sortingOptionId: String = type.sortingOptions.keys.first(),
    val sortAscending: Boolean = true,
) {
    val sortingKeys
        get() = (type.sortingOptions[sortingOptionId] ?: type.sortingOptions.values.first()).keys
}

@Immutable
@Serializable
enum class LibraryScreenTabType(
    val stringId: Int,
    val mediaId: String,
    val mediaType: Int,
    val collectionType: LibraryScreenCollectionType?,
    val sortingOptions: Map<String, SortingOption>,
    val icon: ImageVector,
) {
    TRACKS(
        R.string.tab_tracks,
        "tracks",
        MediaMetadata.MEDIA_TYPE_MIXED,
        null,
        Track.SortingOptions,
        Icons.Outlined.MusicNote,
    ),
    ALBUMS(
        R.string.tab_albums,
        "albums",
        MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
        LibraryScreenCollectionType.ALBUM,
        Album.CollectionSortingOptions,
        Icons.Outlined.Album,
    ),
    ARTISTS(
        R.string.tab_artists,
        "artists",
        MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
        LibraryScreenCollectionType.ARTIST,
        Artist.CollectionSortingOptions,
        Icons.Outlined.PersonOutline,
    ),
    ALBUM_ARTISTS(
        R.string.tab_album_artists,
        "albumArtists",
        MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
        LibraryScreenCollectionType.ALBUM_ARTIST,
        AlbumArtist.CollectionSortingOptions,
        Icons.Outlined.AccountCircle,
    ),
    GENRES(
        R.string.tab_genres,
        "genres",
        MediaMetadata.MEDIA_TYPE_FOLDER_GENRES,
        LibraryScreenCollectionType.GENRE,
        Genre.CollectionSortingOptions,
        Icons.Outlined.Category,
    ),
    PLAYLISTS(
        R.string.tab_playlists,
        "playlists",
        MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
        LibraryScreenCollectionType.PLAYLIST,
        RealizedPlaylist.CollectionSortingOptions,
        Icons.AutoMirrored.Outlined.QueueMusic,
    ),
    FOLDERS(
        R.string.tab_folders,
        "folders",
        MediaMetadata.MEDIA_TYPE_MIXED,
        LibraryScreenCollectionType.FOLDER,
        Folder.SortingOptions,
        Icons.Outlined.Folder,
    ),
}
