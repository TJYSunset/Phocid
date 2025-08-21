@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import org.sunsetware.phocid.DEFAULT_SWIPE_THRESHOLD
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.TNUM
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.HighResArtworkPreference
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.PlayerManager
import org.sunsetware.phocid.data.SortingOption
import org.sunsetware.phocid.data.sorted
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.AnimatedForwardBackwardTransition
import org.sunsetware.phocid.ui.components.Artwork
import org.sunsetware.phocid.ui.components.ArtworkCache
import org.sunsetware.phocid.ui.components.ArtworkImage
import org.sunsetware.phocid.ui.components.BinaryDragState
import org.sunsetware.phocid.ui.components.DragLock
import org.sunsetware.phocid.ui.components.FloatingToolbar
import org.sunsetware.phocid.ui.components.IndefiniteSnackbar
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.MultiSelectManager
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.components.SortingOptionPicker
import org.sunsetware.phocid.ui.components.TrackCarousel
import org.sunsetware.phocid.ui.components.negativePadding
import org.sunsetware.phocid.ui.theme.EnterFromBottom
import org.sunsetware.phocid.ui.theme.ExitToBottom
import org.sunsetware.phocid.ui.theme.LocalThemeAccent
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.contentColorVariant
import org.sunsetware.phocid.ui.theme.emphasizedEnter
import org.sunsetware.phocid.ui.theme.emphasizedExit
import org.sunsetware.phocid.ui.views.MenuItem
import org.sunsetware.phocid.ui.views.collectionMenuItemsWithoutPlay
import org.sunsetware.phocid.ui.views.playlist.NewPlaylistDialog
import org.sunsetware.phocid.ui.views.playlist.PlaylistIoScreen
import org.sunsetware.phocid.ui.views.preferences.PreferencesScreen
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.flatMapLatest
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.map
import org.sunsetware.phocid.utils.roundToIntOrZero
import org.sunsetware.phocid.utils.runningReduce

@Immutable
interface LibraryScreenItem<T : LibraryScreenItem<T>> {
    @Stable
    fun getMultiSelectMenuItems(
        others: List<T>,
        viewModel: MainViewModel,
        continuation: () -> Unit,
    ): List<MenuItem.Button>
}

@Composable
fun LibraryScreen(
    playerScreenDragLock: DragLock,
    isObscured: Boolean,
    viewModel: MainViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerManager = viewModel.playerManager
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val libraryIndex by viewModel.libraryIndex.collectAsStateWithLifecycle()
    val uiManager = viewModel.uiManager
    val homeViewState = uiManager.libraryScreenHomeViewState
    val collectionViewStack by
        uiManager.libraryScreenCollectionViewStack.collectAsStateWithLifecycle()
    val collectionInfos by
        uiManager.libraryScreenCollectionViewStack
            .flatMapLatest(coroutineScope) { states ->
                states.map { it.info }.combine(coroutineScope)
            }
            .runningReduce(coroutineScope) { last, current ->
                current.mapIndexed { index, info ->
                    info ?: (last.getOrNull(index) ?: InvalidCollectionViewInfo)
                }
            }
            .map(coroutineScope) { infos -> infos.map { it ?: InvalidCollectionViewInfo } }
            .collectAsStateWithLifecycle()
    val currentCollection = collectionViewStack.lastOrNull()
    val currentCollectionType = collectionInfos.lastOrNull()?.type
    val currentHomeTabIndex =
        homeViewState.pagerState.targetPage.coerceIn(0, preferences.tabs.size - 1)
    val currentHomeTab = preferences.tabs[currentHomeTabIndex]
    val activeHomeViewMultiSelectState by
        homeViewState.activeMultiSelectState.collectAsStateWithLifecycle()
    val currentMultiSelectState =
        currentCollection?.multiSelectState ?: activeHomeViewMultiSelectState
    val currentMultiSelectItems = currentMultiSelectState?.items?.collectAsStateWithLifecycle()
    val currentSelectedCount =
        remember(currentMultiSelectItems?.value) {
            currentMultiSelectItems?.value?.count { it.selected } ?: 0
        }
    var homeSearchQueryBuffer by remember {
        mutableStateOf(viewModel.uiManager.libraryScreenSearchQuery.value)
    }
    val collectionSearchPositionIndicator by
        remember(currentCollection) {
                currentCollection?.let { collection ->
                    collection.searchResults.combine(coroutineScope, collection.searchIndex) {
                        results,
                        index ->
                        Strings[R.string.search_position_indicator].icuFormat(
                            results.indexOf(index) + 1,
                            results.size,
                        )
                    }
                } ?: MutableStateFlow("")
            }
            .collectAsStateWithLifecycle()
    var collectionSearchQueryBuffer by
        remember(currentCollection) { mutableStateOf(currentCollection?.searchQuery?.value) }
    var viewSettingsVisibility by remember { mutableStateOf(false) }
    var maxGridSize by remember { mutableIntStateOf(4) }
    val overflowMenuItems =
        remember(currentCollection, currentHomeTab, preferences) {
            when {
                currentCollection != null -> {
                    listOf(
                        MenuItem.Button(
                            Strings[R.string.search_in_list],
                            Icons.AutoMirrored.Filled.ManageSearch,
                        ) {
                            collectionSearchQueryBuffer = collectionSearchQueryBuffer ?: ""
                            currentCollection.searchQuery.update { it ?: "" }
                        }
                    ) +
                        collectionMenuItemsWithoutPlay(
                            {
                                currentCollection.multiSelectState.items.value.flatMap {
                                    it.value.info.multiSelectTracks
                                }
                            },
                            playerManager,
                            uiManager,
                        ) +
                        (currentCollection.info.value?.extraCollectionMenuItems(viewModel)
                            ?: emptyList<MenuItem>()) +
                        MenuItem.Divider
                }
                currentHomeTab.type == LibraryScreenTabType.PLAYLISTS ->
                    listOf(
                        MenuItem.Button(Strings[R.string.playlist_new], Icons.Filled.AddBox) {
                            uiManager.openDialog(NewPlaylistDialog())
                        },
                        MenuItem.Button(
                            Strings[R.string.playlist_import_export],
                            Icons.Filled.ImportExport,
                        ) {
                            uiManager.openTopLevelScreen(PlaylistIoScreen.import())
                        },
                        MenuItem.Button(Strings[R.string.playlist_io_sync], Icons.Filled.Sync) {
                            uiManager.openTopLevelScreen(PlaylistIoScreen.sync())
                        },
                        MenuItem.Divider,
                    )
                currentHomeTab.type == LibraryScreenTabType.FOLDERS ->
                    listOfNotNull(
                        preferences.folderTabRoot
                            ?.takeIf { it != libraryIndex.defaultRootFolder }
                            ?.let {
                                MenuItem.Button(
                                    Strings[R.string.library_view_root_folder],
                                    Icons.Filled.FolderOpen,
                                ) {
                                    uiManager.openFolderCollectionView(
                                        libraryIndex.defaultRootFolder
                                    )
                                }
                            }
                    )
                else -> emptyList()
            } +
                listOf(
                    MenuItem.Button(
                        Strings[R.string.view_settings],
                        Icons.AutoMirrored.Filled.ViewList,
                    ) {
                        viewSettingsVisibility = true
                    },
                    MenuItem.Button(Strings[R.string.library_rescan], Icons.Filled.Refresh) {
                        viewModel.scanLibrary(true)
                    },
                    MenuItem.Button(Strings[R.string.preferences], Icons.Filled.Settings) {
                        uiManager.openTopLevelScreen(PreferencesScreen)
                    },
                )
        }
    val floatingToolbarDataSource =
        remember(currentMultiSelectItems?.value) {
            currentMultiSelectItems?.value?.selection ?: emptyList()
        }
    val floatingToolbarItems =
        rememberFloatingToolbarItems(floatingToolbarDataSource, currentMultiSelectState)
    val isScanningLibrary by viewModel.libraryScanState.collectAsStateWithLifecycle()
    var scanSnackbarVisibility by remember { mutableStateOf(false) }

    LaunchedEffect(isScanningLibrary) {
        when (isScanningLibrary) {
            false -> {
                delay(preferences.scanProgressTimeoutSeconds.seconds)
                if (isActive) scanSnackbarVisibility = true
            }
            true -> {
                scanSnackbarVisibility = true
            }
            null -> {
                scanSnackbarVisibility = false
            }
        }
    }

    LaunchedEffect(homeSearchQueryBuffer) {
        uiManager.libraryScreenSearchQuery.update { homeSearchQueryBuffer }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopBar(
                collectionTitles = collectionInfos.map { it.title },
                onBack = { uiManager.back() },
                searchQuery = homeSearchQueryBuffer,
                onSearchQueryChange = { query -> homeSearchQueryBuffer = query },
                onPlayAll = {
                    playerManager.setTracks(
                        currentCollection?.multiSelectState?.items?.value?.flatMap {
                            it.value.info.playTracks
                        }
                            ?: libraryIndex.tracks.values.let { tracks ->
                                val tracksTab =
                                    preferences.tabSettings[LibraryScreenTabType.TRACKS]!!
                                tracks.sorted(
                                    preferences.sortCollator,
                                    tracksTab.sortingKeys,
                                    tracksTab.sortAscending,
                                )
                            },
                        null,
                    )
                },
                selectedCount = currentSelectedCount,
                menuItems = overflowMenuItems,
            )
        },
        bottomBar = {
            BottomBar(
                playerManager,
                libraryIndex,
                viewModel.carouselArtworkCache,
                DEFAULT_SWIPE_THRESHOLD * preferences.swipeThresholdMultiplier,
                preferences.highResArtworkPreference,
                preferences.artworkColorPreference,
                preferences.shapePreference.artworkShape,
                uiManager.playerScreenDragState,
                playerScreenDragLock,
                isObscured,
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(scaffoldPadding)
                    .consumeWindowInsets(scaffoldPadding)
                    .systemBarsPadding()
                    .onSizeChanged { size ->
                        with(density) {
                            maxGridSize = (size.width / 72.dp.toPx()).toInt().coerceAtLeast(4)
                        }
                        uiManager.libraryScreenSize.update {
                            with(density) { size.width.toDp() to size.height.toDp() }
                        }
                    }
        ) {
            AnimatedForwardBackwardTransition(collectionViewStack) { animatedCollectionViewState ->
                if (animatedCollectionViewState == null) {
                    LibraryScreenHomeView(homeViewState)
                } else {
                    LibraryScreenCollectionView(animatedCollectionViewState)
                }
            }

            Column(
                modifier =
                    Modifier.padding(bottom = 16.dp).align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedVisibility(
                    visible = scanSnackbarVisibility,
                    enter = EnterFromBottom,
                    exit = ExitToBottom,
                ) {
                    val libraryScanProgress by
                        viewModel.libraryScanProgress.collectAsStateWithLifecycle()

                    IndefiniteSnackbar(
                        Strings[R.string.snackbar_scanning_library].icuFormat(
                            libraryScanProgress?.first ?: 0,
                            libraryScanProgress?.second ?: "?",
                        )
                    )
                }

                AnimatedVisibility(
                    visible =
                        floatingToolbarDataSource.isNotEmpty() && floatingToolbarItems.isNotEmpty(),
                    enter = EnterFromBottom,
                    exit = ExitToBottom,
                ) {
                    FloatingToolbar(floatingToolbarItems)
                }

                AnimatedVisibility(
                    visible = collectionSearchQueryBuffer != null,
                    enter = EnterFromBottom,
                    exit = ExitToBottom,
                ) {
                    CollectionSearchBar(
                        value = collectionSearchQueryBuffer ?: "",
                        positionIndicator = collectionSearchPositionIndicator,
                        onValueChange = { value ->
                            collectionSearchQueryBuffer = value
                            currentCollection?.searchQuery?.update { value }
                        },
                        onSearchPrevious = { currentCollection?.searchPrevious() },
                        onSearchNext = { currentCollection?.searchNext() },
                    )
                }
            }
        }
    }

    ViewSettings(
        visibility = viewSettingsVisibility,
        onDismissRequest = { viewSettingsVisibility = false },
        sortingOptions =
            currentCollectionType?.sortingOptions ?: currentHomeTab.type.sortingOptions,
        activeSortingOptionId =
            currentCollectionType?.let { preferences.collectionViewSorting[it]!!.first }
                ?: currentHomeTab.sortingOptionId,
        onSetSortingOption = { sortingOptionId ->
            if (currentCollectionType != null) {
                viewModel.updatePreferences {
                    it.copy(
                        collectionViewSorting =
                            it.collectionViewSorting +
                                (currentCollectionType to
                                    Pair(
                                        sortingOptionId,
                                        it.collectionViewSorting[currentCollectionType]!!.second,
                                    ))
                    )
                }
            } else {
                viewModel.updateTabInfo(currentHomeTabIndex) {
                    it.copy(sortingOptionId = sortingOptionId)
                }
            }
        },
        sortAscending =
            currentCollectionType?.let { preferences.collectionViewSorting[it]!!.second }
                ?: currentHomeTab.sortAscending,
        onSetSortAscending = { sortAscending ->
            if (currentCollectionType != null) {
                viewModel.updatePreferences {
                    it.copy(
                        collectionViewSorting =
                            it.collectionViewSorting +
                                (currentCollectionType to
                                    Pair(
                                        it.collectionViewSorting[currentCollectionType]!!.first,
                                        sortAscending,
                                    ))
                    )
                }
            } else {
                viewModel.updateTabInfo(currentHomeTabIndex) {
                    it.copy(sortAscending = sortAscending)
                }
            }
        },
        gridSize = if (currentCollection == null) currentHomeTab.gridSize else null,
        maxGridSize = maxGridSize,
        onSetGridSize = { gridSize ->
            viewModel.updateTabInfo(currentHomeTabIndex) { it.copy(gridSize = gridSize) }
        },
    )
}

@Composable
private fun TopBar(
    collectionTitles: List<String>,
    onBack: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPlayAll: () -> Unit,
    selectedCount: Int,
    menuItems: List<MenuItem>,
) {
    val titles =
        collectionTitles +
            listOfNotNull(
                if (selectedCount > 0)
                    Strings[R.string.list_multi_select_title].icuFormat(selectedCount)
                else null
            )
    TopAppBar(
        title = {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.negativePadding(start = 16.dp),
            ) {
                AnimatedForwardBackwardTransition(
                    if (titles.isEmpty()) emptyList() else listOf(Unit),
                    slide = false,
                    modifier = Modifier.padding(horizontal = 4.dp).height(48.dp),
                ) { animatedCollectionTitle ->
                    if (animatedCollectionTitle != null) {
                        IconButton(
                            onClick = onBack,
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Strings[R.string.commons_back],
                            )
                        }
                    } else {
                        Box(modifier = Modifier.width(0.dp).height(48.dp))
                    }
                }
                AnimatedForwardBackwardTransition(
                    titles,
                    slide = false,
                    modifier = Modifier.padding(start = 16.dp).fillMaxWidth().height(48.dp),
                    keepRoot = false,
                ) { animatedTitle ->
                    if (animatedTitle == null) {
                        HomeSearchBar(searchQuery, onSearchQueryChange)
                    } else {
                        Box(modifier = Modifier.fillMaxHeight())
                    }
                }
                AnimatedForwardBackwardTransition(
                    titles,
                    slide = false,
                    modifier =
                        Modifier.padding(start = (48 + 4 * 2).dp).fillMaxWidth().height(48.dp),
                ) { animatedTitle ->
                    if (animatedTitle == null) {
                        Box(modifier = Modifier.fillMaxHeight())
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            SingleLineText(animatedTitle, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        actions = {
            Row {
                IconButton(onClick = onPlayAll) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = Strings[R.string.track_play_all],
                    )
                }
                OverflowMenu(menuItems)
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

@Composable
private fun HomeSearchBar(value: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var focus by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions { focusManager.clearFocus() },
        textStyle = Typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.onFocusChanged { focus = it.isFocused }.height(48.dp),
    ) { innerTextField ->
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(end = 16.dp).fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && !focus) {
                        Text(
                            text = Strings[R.string.search],
                            style = Typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        innerTextField()
                    }
                }
                if (value.isNotEmpty()) {
                    IconButton({
                        onValueChange("")
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Filled.Clear, Strings[R.string.commons_clear])
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionSearchBar(
    value: String,
    positionIndicator: String,
    onValueChange: (String?) -> Unit,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var focus by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions { focusManager.clearFocus() },
        textStyle = Typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSecondary),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSecondary),
        modifier =
            Modifier.focusRequester(focusRequester)
                .onFocusChanged { focus = it.isFocused }
                .height(48.dp),
    ) { innerTextField ->
        ElevatedCard(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors =
                CardColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    disabledContainerColor = Color.Unspecified,
                    disabledContentColor = Color.Unspecified,
                ),
            elevation = CardDefaults.elevatedCardElevation(6.dp, 6.dp, 6.dp, 6.dp, 6.dp, 6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 4.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && !focus) {
                        SingleLineText(
                            text = Strings[R.string.search],
                            style = Typography.bodyLarge,
                            color = contentColorVariant(),
                        )
                    } else {
                        innerTextField()
                    }
                }
                SingleLineText(
                    text = positionIndicator,
                    style = Typography.labelSmall.copy(fontFeatureSettings = TNUM),
                    color = contentColorVariant(),
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                )
                IconButton(onClick = { onSearchPrevious() }) {
                    Icon(Icons.Filled.ArrowUpward, Strings[R.string.search_previous])
                }
                IconButton(onClick = { onSearchNext() }) {
                    Icon(Icons.Filled.ArrowDownward, Strings[R.string.search_next])
                }
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onValueChange(null)
                    }
                ) {
                    Icon(Icons.Filled.Clear, Strings[R.string.commons_close])
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun BottomBar(
    playerManager: PlayerManager,
    libraryIndex: LibraryIndex,
    carouselArtworkCache: ArtworkCache,
    swipeThreshold: Dp,
    highResArtworkPreference: HighResArtworkPreference,
    artworkColorPreference: ArtworkColorPreference,
    artworkShape: Shape,
    playerScreenDragState: BinaryDragState,
    playerScreenDragLock: DragLock,
    isObscured: Boolean,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var progress by remember { mutableFloatStateOf(0f) }

    val playerState by playerManager.state.collectAsStateWithLifecycle()
    val currentTrack by
        playerManager.state
            .map(coroutineScope) { state ->
                val id = state.actualPlayQueue.getOrNull(state.currentIndex)
                if (id != null) libraryIndex.tracks[id] ?: InvalidTrack else null
            }
            .runningReduce(coroutineScope) { last, current -> current ?: last }
            .collectAsStateWithLifecycle()
    val isPlaying by
        playerManager.transientState
            .map(coroutineScope) { it.isPlaying }
            .collectAsStateWithLifecycle()
    val playerTransientStateVersion by
        playerManager.transientState
            .map(coroutineScope) { it.version }
            .collectAsStateWithLifecycle()

    val animatedThemeAccent = animateColorAsState(LocalThemeAccent.current)

    // Update progress
    LaunchedEffect(currentTrack, isObscured) {
        if (isObscured) return@LaunchedEffect

        val frameTime = (1f / context.display.refreshRate).toDouble().milliseconds

        while (isActive) {
            progress =
                if (currentTrack == null) 0f
                else
                    playerManager.currentPosition.toFloat() /
                        currentTrack!!.duration.inWholeMilliseconds
            delay(frameTime)
        }
    }

    AnimatedContent(
        targetState = playerState.actualPlayQueue.isNotEmpty(),
        transitionSpec = { slideInVertically { it } togetherWith slideOutVertically { it } },
    ) { animatedVisibility ->
        if (animatedVisibility) {
            Column {
                LinearProgressIndicator(
                    progress = { progress.takeIf { it.isFinite() } ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    drawStopIndicator = {},
                )
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    contentPadding = PaddingValues(start = 0.dp, top = 4.dp, end = 4.dp),
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { playerManager.togglePlay() },
                            containerColor = animatedThemeAccent.value,
                            contentColor = animatedThemeAccent.value.contentColor(),
                        ) {
                            AnimatedContent(targetState = isPlaying) { animatedIsPlaying ->
                                if (animatedIsPlaying) {
                                    Icon(
                                        Icons.Filled.Pause,
                                        contentDescription = Strings[R.string.player_pause],
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = Strings[R.string.player_play],
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        TrackCarousel(
                            state = playerState,
                            key = playerTransientStateVersion,
                            swipeThreshold = swipeThreshold,
                            countSelector = { it.actualPlayQueue.size },
                            indexSelector = { it.currentIndex },
                            repeatSelector = { it.repeat != Player.REPEAT_MODE_OFF },
                            indexEqualitySelector = { playerState, index ->
                                if (playerState.shuffle)
                                    playerState.unshuffledPlayQueueMapping!!.indexOf(index)
                                else index
                            },
                            tapKey = Unit,
                            onTap = { playerScreenDragState.animateTo(1f) },
                            onVerticalDrag = {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        playerScreenDragState.onDragStart(playerScreenDragLock)
                                    },
                                    onDragCancel = {
                                        playerScreenDragState.onDragEnd(
                                            playerScreenDragLock,
                                            density,
                                        )
                                    },
                                    onDragEnd = {
                                        playerScreenDragState.onDragEnd(
                                            playerScreenDragLock,
                                            density,
                                        )
                                    },
                                ) { _, dragAmount ->
                                    playerScreenDragState.onDrag(playerScreenDragLock, dragAmount)
                                }
                            },
                            onPrevious = { playerManager.seekToPrevious() },
                            onNext = { playerManager.seekToNext() },
                        ) { state, index ->
                            val id = state.actualPlayQueue.getOrNull(index)
                            val track =
                                (if (id != null) libraryIndex.tracks[id] else null) ?: InvalidTrack
                            LibraryListItemHorizontal(
                                title = track.displayTitle,
                                subtitle = track.displayArtistWithAlbum,
                                lead = {
                                    ArtworkImage(
                                        artwork = Artwork.Track(track),
                                        artworkColorPreference = artworkColorPreference,
                                        shape = artworkShape,
                                        highRes = highResArtworkPreference.player,
                                        highResCache = carouselArtworkCache,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                },
                                actions = {},
                                modifier = Modifier.fillMaxHeight(),
                                marquee = true,
                            )
                        }
                    },
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {}
        }
    }
}

@Composable
private fun <T : LibraryScreenItem<T>> rememberFloatingToolbarItems(
    dataSource: List<LibraryScreenItem<T>>,
    multiSelectManager: MultiSelectManager?,
): List<MenuItem.Button> {
    val viewModel = viewModel<MainViewModel>()
    val selectionItems =
        remember(multiSelectManager) {
            listOf(
                MenuItem.Button(
                    Strings[R.string.list_multi_select_select_all],
                    Icons.Default.SelectAll,
                ) {
                    multiSelectManager?.selectAll()
                },
                MenuItem.Button(
                    Strings[R.string.list_multi_select_select_inverse],
                    Icons.Default.BorderStyle,
                ) {
                    multiSelectManager?.selectInverse()
                },
            )
        }
    var actionItems by remember { mutableStateOf(emptyList<MenuItem.Button>()) }

    LaunchedEffect(dataSource) {
        if (dataSource.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            actionItems =
                dataSource
                    .first()
                    .getMultiSelectMenuItems(
                        others = dataSource.drop(1) as List<T>,
                        viewModel = viewModel,
                        continuation = { multiSelectManager?.clearSelection() },
                    )
        }
    }
    val items =
        remember(selectionItems, actionItems) {
            if (actionItems.isNotEmpty()) selectionItems + actionItems else emptyList()
        }
    return items
}

@Composable
private inline fun ViewSettings(
    visibility: Boolean,
    noinline onDismissRequest: () -> Unit,
    sortingOptions: Map<String, SortingOption>,
    activeSortingOptionId: String,
    crossinline onSetSortingOption: (String) -> Unit,
    sortAscending: Boolean,
    crossinline onSetSortAscending: (Boolean) -> Unit,
    gridSize: Int? = null,
    maxGridSize: Int = 0,
    crossinline onSetGridSize: (Int) -> Unit = {},
) {
    AnimatedVisibility(
        visibility,
        enter = slideInVertically(emphasizedEnter()) { it },
        exit = slideOutVertically(emphasizedExit()) { it },
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (gridSize != null) {
                    Text(
                        text = Strings[R.string.view_settings_grid_size],
                        style = Typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = gridSize.toFloat(),
                        valueRange = 0f..maxGridSize.toFloat(),
                        steps = (maxGridSize - 1).coerceAtLeast(0),
                        onValueChange = { onSetGridSize(it.roundToIntOrZero()) },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(text = Strings[R.string.view_settings_sort_by], style = Typography.labelLarge)
                Spacer(modifier = Modifier.height(16.dp))
                SortingOptionPicker(
                    sortingOptions = sortingOptions,
                    activeSortingOptionId = activeSortingOptionId,
                    sortAscending = sortAscending,
                    onSetSortingOption = onSetSortingOption,
                    onSetSortAscending = onSetSortAscending,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
