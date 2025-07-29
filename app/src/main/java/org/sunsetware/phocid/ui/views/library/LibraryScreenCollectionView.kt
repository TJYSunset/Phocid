package org.sunsetware.phocid.ui.views.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.application
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.Album
import org.sunsetware.phocid.data.AlbumArtist
import org.sunsetware.phocid.data.AlbumKey
import org.sunsetware.phocid.data.AlbumSlice
import org.sunsetware.phocid.data.Artist
import org.sunsetware.phocid.data.ArtistSlice
import org.sunsetware.phocid.data.Folder
import org.sunsetware.phocid.data.Genre
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.RealizedPlaylist
import org.sunsetware.phocid.data.RealizedPlaylistEntry
import org.sunsetware.phocid.data.Searchable
import org.sunsetware.phocid.data.Sortable
import org.sunsetware.phocid.data.SortingKey
import org.sunsetware.phocid.data.SortingOption
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.albumKey
import org.sunsetware.phocid.data.hintBy
import org.sunsetware.phocid.data.searchIndices
import org.sunsetware.phocid.data.sorted
import org.sunsetware.phocid.data.sortedBy
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.globals.format
import org.sunsetware.phocid.ui.components.Artwork
import org.sunsetware.phocid.ui.components.ArtworkImage
import org.sunsetware.phocid.ui.components.EmptyListIndicator
import org.sunsetware.phocid.ui.components.LibraryListHeader
import org.sunsetware.phocid.ui.components.LibraryListItemCompactCard
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.MultiSelectState
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.Scrollbar
import org.sunsetware.phocid.ui.components.multiSelectClickable
import org.sunsetware.phocid.ui.theme.hashColor
import org.sunsetware.phocid.ui.views.MenuItem
import org.sunsetware.phocid.ui.views.collectionMenuItems
import org.sunsetware.phocid.ui.views.playlistCollectionMenuItems
import org.sunsetware.phocid.ui.views.playlistTrackMenuItems
import org.sunsetware.phocid.ui.views.trackMenuItemsLibrary
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.map
import org.sunsetware.phocid.utils.sumOfDuration
import org.sunsetware.phocid.utils.trimAndNormalize

@Immutable
sealed class LibraryScreenCollectionViewItemInfo :
    LibraryScreenItem<LibraryScreenCollectionViewItemInfo> {
    abstract val sortable: Sortable
    abstract val searchable: Searchable
    abstract val title: String
    abstract val subtitle: String
    abstract val lead: LibraryScreenCollectionViewItemLead
    abstract val playTracks: List<Track>
    abstract val multiSelectTracks: List<Track>
    abstract val composeKey: Any

    @Stable
    abstract fun onClick(
        items: List<LibraryScreenCollectionViewItemInfo>,
        index: Int,
        viewModel: MainViewModel,
        onOpenMenu: () -> Unit,
    )

    @Stable
    abstract fun getMenuItems(
        items: List<LibraryScreenCollectionViewItemInfo>,
        index: Int,
        viewModel: MainViewModel,
    ): List<MenuItem>

    @Immutable
    data class LibraryTrack(
        val track: Track,
        override val title: String,
        override val subtitle: String,
        override val lead: LibraryScreenCollectionViewItemLead,
    ) : LibraryScreenCollectionViewItemInfo() {
        override val sortable
            get() = track

        override val searchable
            get() = track

        override val playTracks
            get() = listOf(track)

        override val multiSelectTracks
            get() = listOf(track)

        override val composeKey
            get() = track.id

        override fun onClick(
            items: List<LibraryScreenCollectionViewItemInfo>,
            index: Int,
            viewModel: MainViewModel,
            onOpenMenu: () -> Unit,
        ) {
            viewModel.preferences.value.libraryTrackClickAction.invokeOrOpenMenu(
                items.flatMap { it.playTracks },
                items.take(index).sumOf { it.playTracks.size },
                viewModel.playerManager,
                viewModel.uiManager,
                onOpenMenu,
            )
        }

        override fun getMenuItems(
            items: List<LibraryScreenCollectionViewItemInfo>,
            index: Int,
            viewModel: MainViewModel,
        ): List<MenuItem> {
            return trackMenuItemsLibrary(
                track,
                {
                    items.flatMap { it.playTracks } to
                        items.take(index).sumOf { it.playTracks.size }
                },
                viewModel.playerManager,
                viewModel.uiManager,
            )
        }

        override fun getMultiSelectMenuItems(
            others: List<LibraryScreenCollectionViewItemInfo>,
            viewModel: MainViewModel,
            continuation: () -> Unit,
        ): List<MenuItem.Button> {
            return collectionMenuItems(
                { multiSelectTracks + others.flatMap { it.multiSelectTracks } },
                viewModel.playerManager,
                viewModel.uiManager,
                continuation,
            )
        }
    }

    @Immutable
    data class LibraryFolder(
        val folder: Folder,
        val childTracksRecursive: () -> List<Track>,
        override val title: String,
        override val subtitle: String,
        override val lead: LibraryScreenCollectionViewItemLead,
    ) : LibraryScreenCollectionViewItemInfo() {
        override val sortable
            get() = folder

        override val searchable
            get() = folder

        override val playTracks
            get() = childTracksRecursive()

        override val multiSelectTracks
            get() = childTracksRecursive()

        override val composeKey
            get() = folder.path

        override fun onClick(
            items: List<LibraryScreenCollectionViewItemInfo>,
            index: Int,
            viewModel: MainViewModel,
            onOpenMenu: () -> Unit,
        ) {
            viewModel.uiManager.openFolderCollectionView(folder.path)
        }

        override fun getMenuItems(
            items: List<LibraryScreenCollectionViewItemInfo>,
            index: Int,
            viewModel: MainViewModel,
        ): List<MenuItem> {
            return collectionMenuItems(
                { childTracksRecursive() },
                viewModel.playerManager,
                viewModel.uiManager,
            )
        }

        override fun getMultiSelectMenuItems(
            others: List<LibraryScreenCollectionViewItemInfo>,
            viewModel: MainViewModel,
            continuation: () -> Unit,
        ): List<MenuItem.Button> {
            return collectionMenuItems(
                { multiSelectTracks + others.flatMap { it.multiSelectTracks } },
                viewModel.playerManager,
                viewModel.uiManager,
                continuation,
            )
        }
    }

    @Immutable
    data class PlaylistEntry(
        val playlistKey: UUID,
        val playlistEntry: RealizedPlaylistEntry,
        override val title: String,
        override val subtitle: String,
        override val lead: LibraryScreenCollectionViewItemLead,
    ) : LibraryScreenCollectionViewItemInfo() {
        override val sortable
            get() = playlistEntry.track!!

        override val searchable
            get() = playlistEntry.track!!

        override val playTracks
            get() = listOf(playlistEntry.track!!)

        override val multiSelectTracks
            get() = listOf(playlistEntry.track!!)

        override val composeKey
            get() = playlistEntry.key

        override fun onClick(
            items: List<LibraryScreenCollectionViewItemInfo>,
            index: Int,
            viewModel: MainViewModel,
            onOpenMenu: () -> Unit,
        ) {
            viewModel.preferences.value.libraryTrackClickAction.invokeOrOpenMenu(
                items.flatMap { it.playTracks },
                items.take(index).sumOf { it.playTracks.size },
                viewModel.playerManager,
                viewModel.uiManager,
                onOpenMenu,
            )
        }

        override fun getMenuItems(
            items: List<LibraryScreenCollectionViewItemInfo>,
            index: Int,
            viewModel: MainViewModel,
        ): List<MenuItem> {
            return playlistTrackMenuItems(playlistKey, playlistEntry.key, viewModel.uiManager) +
                trackMenuItemsLibrary(
                    playTracks.first(),
                    {
                        items.flatMap { it.playTracks } to
                            items.take(index).sumOf { it.playTracks.size }
                    },
                    viewModel.playerManager,
                    viewModel.uiManager,
                )
        }

        override fun getMultiSelectMenuItems(
            others: List<LibraryScreenCollectionViewItemInfo>,
            viewModel: MainViewModel,
            continuation: () -> Unit,
        ): List<MenuItem.Button> {
            return collectionMenuItems(
                { multiSelectTracks + others.flatMap { it.multiSelectTracks } },
                viewModel.playerManager,
                viewModel.uiManager,
                continuation,
            ) +
                playlistTrackMenuItems(
                    playlistKey,
                    {
                        setOf(playlistEntry.key) +
                            others.map { (it as PlaylistEntry).playlistEntry.key }
                    },
                    viewModel.uiManager,
                    continuation,
                )
        }
    }
}

@Immutable
sealed class LibraryScreenCollectionViewItemLead {
    @Immutable data class Text(val text: String) : LibraryScreenCollectionViewItemLead()

    @Immutable
    data class Artwork(val artwork: org.sunsetware.phocid.ui.components.Artwork) :
        LibraryScreenCollectionViewItemLead()
}

@Immutable
data class LibraryScreenCollectionViewItem(
    val info: LibraryScreenCollectionViewItemInfo,
    val scrollHint: String,
) : LibraryScreenItem<LibraryScreenCollectionViewItem> {
    override fun getMultiSelectMenuItems(
        others: List<LibraryScreenCollectionViewItem>,
        viewModel: MainViewModel,
        continuation: () -> Unit,
    ): List<MenuItem.Button> {
        return info.getMultiSelectMenuItems(others.map { it.info }, viewModel, continuation)
    }
}

@Stable
class LibraryScreenCollectionViewState(
    private val stateScope: CoroutineScope,
    preferences: StateFlow<Preferences>,
    val info: StateFlow<CollectionViewInfo?>,
    val cardsLazyListState: LazyListState = LazyListState(),
    val tracksLazyListState: LazyListState = LazyListState(),
) : AutoCloseable {
    val multiSelectState =
        MultiSelectState(
            stateScope,
            info.combine(stateScope, preferences) { info, preferences ->
                if (info == null) return@combine emptyList()

                val type = info.type
                val (id, ascending) = preferences.collectionViewSorting[type]!!
                info.items
                    .sortedBy(
                        preferences.sortCollator,
                        (type.sortingOptions[id] ?: type.sortingOptions.values.first()).keys,
                        ascending,
                    ) {
                        it.sortable
                    }
                    .hintBy(
                        preferences.sortCollator,
                        (type.sortingOptions[id] ?: type.sortingOptions.values.first()).keys,
                    ) {
                        it.sortable
                    }
                    .map { LibraryScreenCollectionViewItem(it.first, it.second) }
            },
        )

    override fun close() {
        stateScope.cancel()
    }

    fun listItemBeforeTracksCount(): Int {
        return (if (info.value?.artwork != null) 1 else 0) +
            (if (info.value?.cards?.items?.isNotEmpty() == true) 1 else 0) +
            1
    }

    val searchQuery = MutableStateFlow(null as String?)
    private val _searchIndex = MutableStateFlow(null as Int?)
    val searchIndex = _searchIndex.asStateFlow()
    val searchResults =
        searchQuery
            .map(stateScope) { it?.trimAndNormalize() }
            .combine(stateScope, multiSelectState.items, preferences) { query, items, preferences ->
                val results =
                    items.searchIndices(query ?: "", preferences.searchCollator) {
                        it.value.info.searchable
                    }
                _searchIndex.update { results.firstOrNull() }
                results.firstOrNull()?.let {
                    tracksLazyListState.requestScrollToItem(listItemBeforeTracksCount() + it)
                }
                results
            }

    fun searchPrevious() {
        _searchIndex.update { index ->
            val results = searchResults.value.toList()
            if (results.isEmpty()) null
            else
                results
                    .binarySearch(index)
                    .let { (if (it < 0) -(it + 1) else it) - 1 }
                    .takeIf { it >= 0 }
                    ?.let { results[it] } ?: results.lastOrNull()
        }
        _searchIndex.value?.let {
            tracksLazyListState.requestScrollToItem(listItemBeforeTracksCount() + it)
        }
    }

    fun searchNext() {
        _searchIndex.update { index ->
            val results = searchResults.value.toList()
            if (results.isEmpty()) null
            else
                results
                    .binarySearch(index)
                    .let { (if (it < 0) -(it + 1) else it) + 1 }
                    .takeIf { it < results.size }
                    ?.let { results[it] } ?: results.firstOrNull()
        }
        _searchIndex.value?.let {
            tracksLazyListState.requestScrollToItem(listItemBeforeTracksCount() + it)
        }
    }
}

@Immutable
abstract class CollectionViewInfo {
    abstract val type: LibraryScreenCollectionType
    abstract val title: String
    abstract val artwork: Artwork?
    abstract val cards: CollectionViewCards?
    abstract val additionalStatistics: List<String>
    abstract val items: List<LibraryScreenCollectionViewItemInfo>

    @Stable abstract fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem>
}

val InvalidCollectionViewInfo =
    object : CollectionViewInfo() {
        override val type
            get() = LibraryScreenCollectionType.INVALID

        override val title
            get() = ""

        override val artwork
            get() = null

        override val cards
            get() = null

        override val additionalStatistics
            get() = emptyList<String>()

        override val items
            get() = emptyList<LibraryScreenCollectionViewItemInfo>()

        override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
            return emptyList()
        }
    }

@Immutable
data class CollectionViewCards(
    val items: List<CollectionViewCardInfo>,
    val sortingKeys: List<SortingKey>,
    val sortAscending: Boolean,
)

fun UiManager.openAlbumCollectionView(key: AlbumKey) {
    openCollectionView { libraryIndex ->
        libraryIndex.albums[key]?.let { AlbumCollectionViewInfo(it) }
    }
}

fun UiManager.openArtistCollectionView(key: String) {
    openCollectionView { libraryIndex ->
        libraryIndex.artists[key]?.let { ArtistCollectionViewInfo(it) }
    }
}

fun UiManager.openAlbumArtistCollectionView(key: String) {
    openCollectionView { libraryIndex ->
        libraryIndex.albumArtists[key]?.let { AlbumArtistCollectionViewInfo(it) }
    }
}

fun UiManager.openGenreCollectionView(key: String) {
    openCollectionView { libraryIndex ->
        libraryIndex.genres[key]?.let { GenreCollectionViewInfo(it) }
    }
}

fun UiManager.openFolderCollectionView(path: String) {
    openCollectionView { libraryIndex ->
        libraryIndex.folders[path]?.let { FolderCollectionViewInfo(it, libraryIndex.folders) }
    }
}

@Immutable
data class AlbumCollectionViewInfo(val album: Album) : CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.ALBUM

    override val title
        get() = album.name

    override val artwork
        get() = album.tracks.firstOrNull()?.let { Artwork.Track(it) }

    override val cards
        get() = null

    override val additionalStatistics
        get() = emptyList<String>()

    override val items
        get() =
            album.tracks.map { track ->
                LibraryScreenCollectionViewItemInfo.LibraryTrack(
                    track = track,
                    title = track.displayTitle,
                    subtitle = Strings.separate(track.displayArtist, track.duration.format()),
                    lead = LibraryScreenCollectionViewItemLead.Text(track.displayNumber),
                )
            }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return emptyList()
    }
}

@Immutable
data class ArtistCollectionViewInfo(val artist: Artist) : CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.ARTIST

    override val title
        get() = artist.name

    override val artwork
        get() = null

    override val cards =
        CollectionViewCards(
            artist.albumSlices.map { slice ->
                CollectionViewCardInfo(
                    slice.album,
                    slice.album.name,
                    Strings.separate(slice.album.year?.toString(), slice.album.displayAlbumArtist),
                    Artwork.Track(slice.album.tracks.firstOrNull() ?: InvalidTrack),
                ) { library ->
                    library.artists[artist.name]
                        ?.albumSlices
                        ?.firstOrNull { it.album.albumKey == slice.album.albumKey }
                        ?.let { AlbumSliceCollectionViewInfo(it) }
                }
            },
            listOf(SortingKey.YEAR, SortingKey.ALBUM_ARTIST, SortingKey.ALBUM),
            true,
        )

    override val additionalStatistics
        get() = emptyList<String>()

    override val items
        get() =
            artist.tracks.map { track ->
                LibraryScreenCollectionViewItemInfo.LibraryTrack(
                    track = track,
                    title = track.displayTitle,
                    subtitle = Strings.separate(track.album, track.duration.format()),
                    lead = LibraryScreenCollectionViewItemLead.Artwork(Artwork.Track(track)),
                )
            }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return emptyList()
    }
}

@Immutable
data class AlbumArtistCollectionViewInfo(val albumArtist: AlbumArtist) : CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.ALBUM_ARTIST

    override val title
        get() = albumArtist.name

    override val artwork
        get() = null

    override val cards =
        CollectionViewCards(
            albumArtist.albums.map { album ->
                CollectionViewCardInfo(
                    album,
                    album.name,
                    Strings.separate(album.year?.toString(), album.displayAlbumArtist),
                    Artwork.Track(album.tracks.firstOrNull() ?: InvalidTrack),
                ) { library ->
                    library.albums[album.albumKey]?.let { AlbumCollectionViewInfo(it) }
                }
            },
            listOf(SortingKey.YEAR, SortingKey.ALBUM),
            true,
        )

    override val additionalStatistics
        get() = emptyList<String>()

    override val items
        get() =
            albumArtist.tracks.map { track ->
                LibraryScreenCollectionViewItemInfo.LibraryTrack(
                    track = track,
                    title = track.displayTitle,
                    subtitle = Strings.separate(track.album, track.duration.format()),
                    lead = LibraryScreenCollectionViewItemLead.Artwork(Artwork.Track(track)),
                )
            }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return emptyList()
    }
}

@Immutable
data class GenreCollectionViewInfo(val genre: Genre) : CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.GENRE

    override val title
        get() = genre.name

    override val artwork
        get() = null

    override val cards =
        CollectionViewCards(
            genre.artistSlices.map { slice ->
                CollectionViewCardInfo(
                    slice.artist,
                    slice.artist.name,
                    Strings[R.string.count_track].icuFormat(slice.tracks.size),
                    Artwork.Track(slice.artist.tracks.firstOrNull() ?: InvalidTrack),
                ) { library ->
                    library.genres[genre.name]
                        ?.artistSlices
                        ?.firstOrNull { it.artist.name == slice.artist.name }
                        ?.let { ArtistSliceCollectionViewInfo(it) }
                }
            },
            listOf(SortingKey.ARTIST),
            true,
        )

    override val additionalStatistics
        get() = emptyList<String>()

    override val items
        get() =
            genre.tracks.map { track ->
                LibraryScreenCollectionViewItemInfo.LibraryTrack(
                    track = track,
                    title = track.displayTitle,
                    subtitle = Strings.separate(track.displayArtist, track.duration.format()),
                    lead = LibraryScreenCollectionViewItemLead.Artwork(Artwork.Track(track)),
                )
            }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return emptyList()
    }
}

@Immutable
data class FolderCollectionViewInfo(val folder: Folder, val folderIndex: Map<String, Folder>) :
    CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.FOLDER

    override val title
        get() = folder.fileName

    override val artwork
        get() = null

    override val cards
        get() = null

    override val additionalStatistics
        get() =
            listOfNotNull(
                folder.childFolders.size
                    .takeIf { it != 0 }
                    ?.let { Strings[R.string.count_folder].icuFormat(it) }
            )

    override val items
        get() =
            folder.childFolders.map { child ->
                val childFolder = folderIndex[child]!!
                LibraryScreenCollectionViewItemInfo.LibraryFolder(
                    folder = childFolder,
                    childTracksRecursive = { childFolder.childTracksRecursive(folderIndex) },
                    title = childFolder.fileName,
                    subtitle = childFolder.displayStatistics,
                    lead =
                        LibraryScreenCollectionViewItemLead.Artwork(
                            Artwork.Icon(Icons.Outlined.Folder, childFolder.path.hashColor())
                        ),
                )
            } +
                folder.childTracks.map { track ->
                    LibraryScreenCollectionViewItemInfo.LibraryTrack(
                        track = track,
                        title = track.fileName,
                        subtitle = track.duration.format(),
                        lead = LibraryScreenCollectionViewItemLead.Artwork(Artwork.Track(track)),
                    )
                }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return emptyList()
    }
}

@Immutable
data class PlaylistCollectionViewInfo(val key: UUID, val playlist: RealizedPlaylist) :
    CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.PLAYLIST

    override val title
        get() = playlist.displayName

    override val artwork
        get() = null

    override val cards
        get() = null

    override val additionalStatistics
        get() = emptyList<String>()

    override val items
        get() =
            playlist.entries
                .filter { it.track != null }
                .map { entry ->
                    val track = entry.track!!
                    LibraryScreenCollectionViewItemInfo.PlaylistEntry(
                        playlistKey = key,
                        playlistEntry = entry,
                        title = track.displayTitle,
                        subtitle = Strings.separate(track.displayArtist, track.duration.format()),
                        lead = LibraryScreenCollectionViewItemLead.Artwork(Artwork.Track(track)),
                    )
                }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return playlistCollectionMenuItems(
            key,
            playlist.displayName,
            viewModel.application.applicationContext,
            viewModel.uiManager,
        )
    }
}

@Immutable
data class AlbumSliceCollectionViewInfo(val albumSlice: AlbumSlice) : CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.ALBUM_SLICE

    override val title
        get() = albumSlice.album.name

    override val artwork
        get() = albumSlice.album.tracks.firstOrNull()?.let { Artwork.Track(it) }

    override val cards
        get() = null

    override val additionalStatistics
        get() = listOfNotNull(albumSlice.album.year?.toString())

    override val items
        get() =
            albumSlice.tracks.map { track ->
                LibraryScreenCollectionViewItemInfo.LibraryTrack(
                    track = track,
                    title = track.displayTitle,
                    subtitle = track.duration.format(),
                    lead = LibraryScreenCollectionViewItemLead.Text(track.displayNumber),
                )
            }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return emptyList()
    }
}

@Immutable
data class ArtistSliceCollectionViewInfo(val artistSlice: ArtistSlice) : CollectionViewInfo() {
    override val type
        get() = LibraryScreenCollectionType.ARTIST_SLICE

    override val title
        get() = artistSlice.artist.name

    override val artwork
        get() = null

    override val cards
        get() = null

    override val additionalStatistics
        get() = emptyList<String>()

    override val items
        get() =
            artistSlice.tracks.map { track ->
                LibraryScreenCollectionViewItemInfo.LibraryTrack(
                    track = track,
                    title = track.displayTitle,
                    subtitle = track.duration.format(),
                    lead = LibraryScreenCollectionViewItemLead.Artwork(Artwork.Track(track)),
                )
            }

    @Stable
    override fun extraCollectionMenuItems(viewModel: MainViewModel): List<MenuItem> {
        return emptyList()
    }
}

@Immutable
data class CollectionViewCardInfo(
    val sortable: Sortable,
    val title: String,
    val subtitle: String,
    val artwork: Artwork,
    val content: (LibraryIndex) -> CollectionViewInfo?,
) : Sortable by sortable

@Composable
fun LibraryScreenCollectionView(
    state: LibraryScreenCollectionViewState,
    viewModel: MainViewModel = viewModel(),
) {
    val uiManager = viewModel.uiManager
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val multiSelectState = state.multiSelectState
    val cardsLazyListState = state.cardsLazyListState
    val tracksLazyListState = state.tracksLazyListState
    val info by
        state.info
            .filterNotNull()
            .collectAsStateWithLifecycle(state.info.value ?: InvalidCollectionViewInfo)
    val items by multiSelectState.items.collectAsStateWithLifecycle()
    val itemInfos = remember(items) { items.map { it.value.info } }
    val searchIndex by state.searchIndex.collectAsStateWithLifecycle()
    val searchResults by state.searchResults.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (info.artwork == null && info.cards?.items?.isEmpty() != false && info.items.isEmpty()) {
            EmptyListIndicator()
        } else {
            Scrollbar(
                tracksLazyListState,
                {
                    items
                        .getOrNull((it - state.listItemBeforeTracksCount()).coerceAtLeast(0))
                        ?.value
                        ?.scrollHint
                },
                preferences.alwaysShowHintOnScroll,
            ) {
                LazyColumn(state = tracksLazyListState, modifier = Modifier.fillMaxSize()) {
                    if (info.artwork != null) {
                        item {
                            ArtworkImage(
                                artwork = info.artwork!!,
                                artworkColorPreference = preferences.artworkColorPreference,
                                shape = RoundedCornerShape(0.dp),
                                highRes = preferences.highResArtworkPreference.library,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .aspectRatio(1f, matchHeightConstraintsFirst = true),
                            )
                        }
                    }
                    if (info.cards?.items?.isNotEmpty() == true) {
                        item {
                            val sortedCards =
                                remember(info, preferences) {
                                    info.cards!!
                                        .items
                                        .sorted(
                                            preferences.sortCollator,
                                            info.cards!!.sortingKeys,
                                            info.cards!!.sortAscending,
                                        )
                                }
                            LazyRow(
                                state = cardsLazyListState,
                                contentPadding = PaddingValues(horizontal = (16 - 8).dp),
                                modifier = Modifier.padding(vertical = 16.dp),
                            ) {
                                items(sortedCards) { card ->
                                    LibraryListItemCompactCard(
                                        title = card.title,
                                        subtitle = card.subtitle,
                                        shape = preferences.shapePreference.cardShape,
                                        image = {
                                            ArtworkImage(
                                                artwork = card.artwork,
                                                artworkColorPreference =
                                                    preferences.artworkColorPreference,
                                                shape = RoundedCornerShape(0.dp),
                                                highRes =
                                                    preferences.highResArtworkPreference.library,
                                                modifier =
                                                    Modifier.fillMaxWidth()
                                                        .aspectRatio(
                                                            1f,
                                                            matchHeightConstraintsFirst = true,
                                                        ),
                                            )
                                        },
                                        modifier =
                                            Modifier.padding(horizontal = 8.dp)
                                                .width(144.dp)
                                                .clickable {
                                                    uiManager.openCollectionView(card.content)
                                                },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        val totalDuration =
                            items
                                .sumOfDuration { item ->
                                    item.value.info.playTracks.sumOfDuration { it.duration }
                                }
                                .format()
                        LibraryListHeader(
                            Strings.separate(
                                info.additionalStatistics +
                                    listOf(
                                        Strings[R.string.count_track].icuFormat(
                                            items.sumOf { it.value.info.playTracks.size }
                                        ),
                                        totalDuration,
                                    )
                            )
                        )
                    }
                    itemsIndexed(items, { _, (item, _) -> item.info.composeKey }) {
                        index,
                        (item, selected) ->
                        val (info, _) = item
                        val menuState = remember { mutableStateOf(false) }
                        LibraryListItemHorizontal(
                            title = info.title,
                            subtitle = info.subtitle,
                            lead = {
                                when (info.lead) {
                                    is LibraryScreenCollectionViewItemLead.Text -> {
                                        Text(
                                            text =
                                                (info.lead
                                                        as LibraryScreenCollectionViewItemLead.Text)
                                                    .text,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    is LibraryScreenCollectionViewItemLead.Artwork -> {
                                        ArtworkImage(
                                            artwork =
                                                (info.lead
                                                        as
                                                        LibraryScreenCollectionViewItemLead.Artwork)
                                                    .artwork,
                                            artworkColorPreference =
                                                preferences.artworkColorPreference,
                                            shape = preferences.shapePreference.artworkShape,
                                            highRes = preferences.highResArtworkPreference.small,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            },
                            actions = {
                                OverflowMenu(
                                    info.getMenuItems(itemInfos, index, viewModel),
                                    state = menuState,
                                )
                            },
                            modifier =
                                Modifier.multiSelectClickable(
                                        items,
                                        index,
                                        multiSelectState,
                                        haptics,
                                    ) {
                                        info.onClick(itemInfos, index, viewModel) {
                                            menuState.value = true
                                        }
                                    }
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null),
                            selected = selected,
                            highlighted = searchResults.contains(index),
                            bordered = index == searchIndex,
                        )
                    }
                }
            }
        }
    }

    // https://issuetracker.google.com/issues/209652366#comment35
    SideEffect {
        tracksLazyListState.requestScrollToItem(
            index = tracksLazyListState.firstVisibleItemIndex,
            scrollOffset = tracksLazyListState.firstVisibleItemScrollOffset,
        )
    }
}

@Immutable
@Serializable
enum class LibraryScreenCollectionType(val sortingOptions: Map<String, SortingOption>) {
    INVALID(mapOf("" to SortingOption(null, emptyList()))),
    ALBUM(Album.TrackSortingOptions),
    ARTIST(Artist.TrackSortingOptions),
    ALBUM_ARTIST(AlbumArtist.TrackSortingOptions),
    GENRE(Genre.TrackSortingOptions),
    FOLDER(Folder.SortingOptions),
    PLAYLIST(RealizedPlaylist.TrackSortingOptions),
    ALBUM_SLICE(AlbumSlice.TrackSortingOptions),
    ARTIST_SLICE(ArtistSlice.TrackSortingOptions),
}
