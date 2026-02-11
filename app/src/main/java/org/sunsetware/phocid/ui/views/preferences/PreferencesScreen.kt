@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
    ExperimentalSerializationApi::class,
)

package org.sunsetware.phocid.ui.views.preferences

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Attribution
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.SafetyDivider
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShapeLine
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ibm.icu.number.Notation
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
import com.ibm.icu.text.Collator
import com.ibm.icu.text.ListFormatter
import com.ibm.icu.text.SimpleDateFormat
import com.ibm.icu.util.MeasureUnit
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.BuildConfig
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.TopLevelScreen
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.DarkThemePreference
import org.sunsetware.phocid.data.DefaultShuffleMode
import org.sunsetware.phocid.data.HighResArtworkPreference
import org.sunsetware.phocid.data.LyricsDisplayPreference
import org.sunsetware.phocid.data.PlayerManager
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.QueueDensityPreference
import org.sunsetware.phocid.data.ShapePreference
import org.sunsetware.phocid.data.SwipeDirectionPreference
import org.sunsetware.phocid.data.TabStylePreference
import org.sunsetware.phocid.data.clampScrollbarWidthDp
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.Scrollbar
import org.sunsetware.phocid.ui.components.UtilityListItem
import org.sunsetware.phocid.ui.components.UtilityListItemWithCustomSubtitle
import org.sunsetware.phocid.ui.components.UtilitySwitchListItem
import org.sunsetware.phocid.ui.components.negativePadding
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.views.library.LibraryTrackClickAction
import org.sunsetware.phocid.ui.views.player.PlayerScreenLayoutType
import org.sunsetware.phocid.ui.views.playlist.PlaylistIoScreen
import org.sunsetware.phocid.ui.views.playlist.PlaylistIoSettingsDialog
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.roundToIntOrZero

@Stable
object PreferencesScreen : TopLevelScreen() {
    private val lazyListState = LazyListState()
    private val pages =
        listOf(Interface, HomeScreen, NowPlaying, Playback, Radio, Indexing, Miscellaneous, About)

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        Scaffold(
            topBar = {
                key(MaterialTheme.colorScheme) {
                    TopAppBar(
                        title = { Text(Strings[R.string.preferences]) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.uiManager.back() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    Strings[R.string.commons_back],
                                )
                            }
                        },
                    )
                }
            }
        ) { scaffoldPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Scrollbar(
                    lazyListState,
                    { null },
                    false,
                    alwaysVisible = preferences.alwaysShowScrollbar,
                    width = preferences.scrollbarWidthDp.dp,
                ) {
                    LazyColumn(state = lazyListState) {
                        items(pages) { page ->
                            UtilityListItemWithCustomSubtitle(
                                title = Strings[page.stringId],
                                lead = { LeadIcon(page.icon) },
                                subtitle = {
                                    ExamplesSubtitle(
                                        page.items
                                            .filter { it !is Item.ConditionalClickable }
                                            .map { it.title() }
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        viewModel.uiManager.openTopLevelScreen(
                                            PreferencesSubscreen(page)
                                        )
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Stable
private class PreferencesSubscreen(private val page: Page) : TopLevelScreen() {
    private val lazyListState = LazyListState()

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val filteredTrackCount by
            viewModel.unfilteredTrackIndex
                .combine(coroutineScope, viewModel.libraryIndex) { unfiltered, filtered ->
                    unfiltered.tracks.size - filtered.tracks.size
                }
                .collectAsStateWithLifecycle()
        val logcatDumpPath = remember {
            context.getExternalFilesDir(null)?.let { FilenameUtils.concat(it.path, "logcat.txt") }
        }
        val imagesPermissionState =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
            } else null
        val partialImagesPermissionState =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                rememberPermissionState(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else {
                null
            }
        val preferencesScreenContext =
            PreferencesScreenContext(
                context,
                coroutineScope,
                viewModel.uiManager,
                viewModel.playerManager,
                preferences,
                viewModel::updatePreferences,
                filteredTrackCount,
                logcatDumpPath,
                imagesPermissionState,
                partialImagesPermissionState,
            )

        Scaffold(
            topBar = {
                key(MaterialTheme.colorScheme) {
                    TopAppBar(
                        title = { Text(Strings[page.stringId]) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.uiManager.back() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    Strings[R.string.commons_back],
                                )
                            }
                        },
                    )
                }
            }
        ) { scaffoldPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Scrollbar(
                    lazyListState,
                    { null },
                    false,
                    alwaysVisible = preferences.alwaysShowScrollbar,
                    width = preferences.scrollbarWidthDp.dp,
                ) {
                    LazyColumn(state = lazyListState) {
                        items(page.items) { item ->
                            when (item) {
                                is Item.NonInteractive -> {
                                    UtilityListItem(
                                        title = item.title(),
                                        subtitle =
                                            with(item) { preferencesScreenContext.subtitle() },
                                        lead = { LeadIcon(item.icon) },
                                    )
                                }
                                is Item.Clickable<*> -> {
                                    UtilityListItem(
                                        title = item.title(),
                                        subtitle =
                                            with(item) {
                                                preferencesScreenContext.subtitle(
                                                    value(preferences)
                                                )
                                            },
                                        lead = { LeadIcon(item.icon) },
                                        actions = {
                                            if (item.action != null) {
                                                IconButton(
                                                    onClick = {
                                                        with(item.action) {
                                                            preferencesScreenContext.third()
                                                        }
                                                    },
                                                    modifier = Modifier.negativePadding(end = 12.dp),
                                                ) {
                                                    Icon(item.action.second, item.action.first())
                                                }
                                            }
                                        },
                                        modifier =
                                            Modifier.clickable {
                                                with(item) { preferencesScreenContext.onClick() }
                                            },
                                    )
                                }
                                is Item.ConditionalClickable -> {
                                    if (with(item) { preferencesScreenContext.visibility() }) {
                                        UtilityListItem(
                                            title = item.title(),
                                            subtitle =
                                                with(item) { preferencesScreenContext.subtitle() },
                                            lead = { LeadIcon(item.icon) },
                                            modifier =
                                                Modifier.clickable {
                                                    with(item) {
                                                        preferencesScreenContext.onClick()
                                                    }
                                                },
                                        )
                                    }
                                }
                                is Item.Toggle -> {
                                    UtilitySwitchListItem(
                                        title = item.title(),
                                        subtitle =
                                            with(item) {
                                                preferencesScreenContext.subtitle(
                                                    value(preferences)
                                                )
                                            },
                                        lead = { LeadIcon(item.icon) },
                                        checked = item.value(preferences),
                                        onCheckedChange = { checked ->
                                            viewModel.updatePreferences {
                                                item.onSetValue(it, checked)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeadIcon(icon: ImageVector) {
    Box(modifier = Modifier.padding(end = 16.dp).size(40.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null)
    }
}

@Composable
private fun ExamplesSubtitle(examples: List<String>) {
    Layout(
        content = {
            Text(
                text = examples[0],
                style = Typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            Text(
                text = Strings[R.string.preferences_example_ellipsis],
                style = Typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            examples.take(3).forEachIndexed { index, item ->
                Text(
                    text = item,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (index < min(examples.size, 3) - 1) {
                    Text(
                        text = Strings[R.string.preferences_example_separator],
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val lineHeight = Typography.bodySmall.lineHeight.roundToPx()
        val measureConstraints = Constraints(minHeight = lineHeight, maxHeight = lineHeight)
        val truncatedFirstItem =
            measurables[0].measure(
                Constraints(
                    maxWidth = constraints.maxWidth,
                    minHeight = lineHeight,
                    maxHeight = lineHeight,
                )
            )
        val ellipsis = measurables[1].measure(measureConstraints)
        val items =
            measurables.drop(1).chunked(2).mapIndexed { index, pair ->
                if (index == 0) null to pair[1] else pair[0] to pair[1]
            }
        var x = 0
        var truncated = examples.size > 3

        layout(constraints.maxWidth, lineHeight) {
            for ((separator, item) in items) {
                val separatorPlaceable = separator?.measure(measureConstraints)
                val itemPlaceable = item.measure(measureConstraints)
                val separatorWidth = separatorPlaceable?.measuredWidth ?: 0
                if (
                    separatorWidth + itemPlaceable.measuredWidth <=
                        constraints.maxWidth - (ellipsis.measuredWidth) - x
                ) {
                    separatorPlaceable?.placeRelative(x, 0)
                    itemPlaceable.placeRelative(x + separatorWidth, 0)
                    x += separatorWidth + itemPlaceable.measuredWidth
                } else {
                    truncated = true
                    break
                }
            }

            if (truncated) {
                if (x == 0) {
                    truncatedFirstItem.placeRelative(0, 0)
                } else {
                    ellipsis.placeRelative(x, 0)
                }
            }
        }
    }
}

private val Interface =
    Page(
        R.string.preferences_interface,
        Icons.Filled.Dashboard,
        listOf(
            Item.SingleChoice(
                title = { Strings[R.string.preferences_dark_theme] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.DarkMode,
                options = DarkThemePreference.entries,
                value = { it.darkTheme },
                onSetValue = { preferences, new -> preferences.copy(darkTheme = new) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_theme_color] },
                subtitle = { Strings[it.stringId] },
                icon = Icons.Filled.Palette,
                value = { it.themeColorSource },
                onClick = { uiManager.openDialog(PreferencesThemeColorDialog()) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_pure_background_color] },
                subtitle = { null },
                icon = Icons.Filled.Contrast,
                value = { it.pureBackgroundColor },
                onSetValue = { preferences, new -> preferences.copy(pureBackgroundColor = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_artwork_color] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.Colorize,
                options = ArtworkColorPreference.entries,
                value = { it.artworkColorPreference },
                onSetValue = { preferences, new -> preferences.copy(artworkColorPreference = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_shape] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.RoundedCorner,
                options = ShapePreference.entries,
                value = { it.shapePreference },
                onSetValue = { preferences, new -> preferences.copy(shapePreference = new) },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_ui_scaling] },
                numberFormatter = { Strings[R.string.preferences_multiplier_number].icuFormat(it) },
                icon = Icons.Filled.ZoomIn,
                value = { it.densityMultiplier },
                default = 1f,
                min = 0.5f,
                max = 2f,
                steps = 200 - 50 - 1,
                onSetValue = { preferences, new -> preferences.copy(densityMultiplier = new) },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_swipe_threshold] },
                numberFormatter = { Strings[R.string.preferences_multiplier_number].icuFormat(it) },
                icon = Icons.Filled.Swipe,
                value = { it.swipeThresholdMultiplier },
                default = 1f,
                min = 0.1f,
                max = 10f,
                steps = 100 - 1 - 1,
                onSetValue = { preferences, new ->
                    preferences.copy(swipeThresholdMultiplier = new)
                },
            ),
            Item.OrderAndVisibility(
                title = { Strings[R.string.preferences_notification_buttons] },
                subtitle = { preferences ->
                    preferences.notificationButtons
                        .takeIf { it.isNotEmpty() }
                        ?.let { buttons ->
                            ListFormatter.getInstance().format(buttons.map { Strings[it.stringId] })
                        } ?: Strings[R.string.preferences_notification_buttons_none]
                },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.Notifications,
                value = { it.notificationButtonOrderAndVisibility },
                onSetValue = { preferences, new ->
                    preferences.copy(notificationButtonOrderAndVisibility = new)
                },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_high_res_artwork] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.Hd,
                options = HighResArtworkPreference.entries,
                value = { it.highResArtworkPreference },
                onSetValue = { preferences, new ->
                    preferences.copy(highResArtworkPreference = new)
                },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_always_show_hint_on_scroll] },
                subtitle = { null },
                icon = Icons.AutoMirrored.Filled.Label,
                value = { it.alwaysShowHintOnScroll },
                onSetValue = { preferences, new -> preferences.copy(alwaysShowHintOnScroll = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_always_show_scrollbar] },
                subtitle = { null },
                icon = Icons.Filled.Commit,
                value = { it.alwaysShowScrollbar },
                onSetValue = { preferences, new -> preferences.copy(alwaysShowScrollbar = new) },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_scrollbar_width] },
                numberFormatter = {
                    Strings[R.string.preferences_scrollbar_width_number].icuFormat(it)
                },
                icon = Icons.Filled.Commit,
                value = { it.scrollbarWidthDp },
                default = 4f,
                min = 2f,
                max = 16f,
                steps = 16 - 2 - 1,
                onSetValue = { preferences, new ->
                    preferences.copy(scrollbarWidthDp = clampScrollbarWidthDp(new))
                },
            ),
            Item.TextInput(
                title = { Strings[R.string.preferences_conjunction_symbol] },
                subtitle = { Strings[R.string.preferences_conjunction_symbol_subtitle] },
                icon = Icons.Filled.SafetyDivider,
                value = { it.conjunctionSymbol },
                placeholder = { Strings[R.string.symbol_conjunction] },
                allowEmpty = true,
                updatePreferences = { preferences, value ->
                    preferences.copy(conjunctionSymbol = value)
                },
            ),
        ),
    )

private val HomeScreen =
    Page(
        R.string.preferences_home_screen,
        Icons.Filled.Home,
        listOf(
            Item.OrderAndVisibility(
                title = { Strings[R.string.preferences_tabs] },
                subtitle = { preferences ->
                    preferences.tabs.let { buttons ->
                        ListFormatter.getInstance()
                            .format(buttons.map { Strings[it.type.stringId] })
                    }
                },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.DashboardCustomize,
                value = { it.tabOrderAndVisibility },
                onSetValue = { preferences, new -> preferences.copy(tabOrderAndVisibility = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_scrollable_tabs] },
                subtitle = { null },
                icon = Icons.Filled.Swipe,
                value = { it.scrollableTabs },
                onSetValue = { preferences, new -> preferences.copy(scrollableTabs = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_tab_style] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.Tab,
                options = TabStylePreference.entries,
                value = { it.tabStyle },
                onSetValue = { preferences, new -> preferences.copy(tabStyle = new) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_folder_tab_root] },
                subtitle = { it?.let { "/$it" } ?: Strings[R.string.commons_automatic] },
                icon = Icons.Filled.FolderSpecial,
                value = { it.folderTabRoot },
                onClick = {
                    uiManager.openDialog(
                        PreferencesFolderPickerDialog(
                            unfiltered = false,
                            initialPath = preferences.folderTabRoot,
                            onConfirmOrDismiss = { path ->
                                if (path != null) {
                                    updatePreferences { it.copy(folderTabRoot = path) }
                                }
                            },
                        )
                    )
                },
                action =
                    Triple(
                        { Strings[R.string.commons_reset] },
                        Icons.AutoMirrored.Filled.Undo,
                        { updatePreferences { it.copy(folderTabRoot = null) } },
                    ),
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_colored_cards] },
                subtitle = { null },
                icon = Icons.Filled.FormatColorFill,
                value = { it.coloredCards },
                onSetValue = { preferences, new -> preferences.copy(coloredCards = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_library_track_click_action] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.AdsClick,
                options = LibraryTrackClickAction.entries,
                value = { it.libraryTrackClickAction },
                onSetValue = { preferences, new -> preferences.copy(libraryTrackClickAction = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_sorting_language] },
                itemName = {
                    if (it == null) Strings[R.string.preferences_sorting_language_system]
                    else "${it.displayName} (${it.toLanguageTag()})"
                },
                icon = Icons.Filled.SortByAlpha,
                options =
                    listOf(null) +
                        Collator.getAvailableLocales()
                            .sortedBy { it.toLanguageTag() }
                            .filter { it.language != "zh" || it.country.isEmpty() },
                value = { it.sortingLocale },
                onSetValue = { preferences, new ->
                    preferences.copy(sortingLocaleLanguageTag = new?.toLanguageTag())
                },
            ),
        ),
    )

private val NowPlaying =
    Page(
        R.string.preferences_now_playing,
        Icons.Filled.PlayCircleOutline,
        listOf(
            Item.SingleChoice(
                title = { Strings[R.string.preferences_player_screen_layout] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.SpaceDashboard,
                options = PlayerScreenLayoutType.entries,
                value = { it.playerScreenLayout },
                onSetValue = { preferences, new -> preferences.copy(playerScreenLayout = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_colored_player] },
                subtitle = { Strings[R.string.preferences_colored_player_subtitle] },
                icon = Icons.Filled.FormatColorFill,
                value = { it.coloredPlayer },
                onSetValue = { preferences, new -> preferences.copy(coloredPlayer = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_colorful_player_background] },
                subtitle = { null },
                icon = Icons.Filled.Gradient,
                value = { it.colorfulPlayerBackground },
                onSetValue = { preferences, new ->
                    preferences.copy(colorfulPlayerBackground = new)
                },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_swipe_to_remove_from_queue] },
                subtitle = { null },
                icon = Icons.Filled.ClearAll,
                value = { it.swipeToRemoveFromQueue },
                onSetValue = { preferences, new -> preferences.copy(swipeToRemoveFromQueue = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_queue_swipe_direction] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.Swipe,
                options = SwipeDirectionPreference.entries,
                value = { it.swipeToRemoveDirection },
                onSetValue = { preferences, new ->
                    preferences.copy(swipeToRemoveDirection = new)
                },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_queue_density] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.Tune,
                options = QueueDensityPreference.entries,
                value = { it.queueDensity },
                onSetValue = { preferences, new -> preferences.copy(queueDensity = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_lyrics_display] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.Subtitles,
                options = LyricsDisplayPreference.entries,
                value = { it.lyricsDisplay },
                onSetValue = { preferences, new -> preferences.copy(lyricsDisplay = new) },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_lyrics_scaling] },
                numberFormatter = { Strings[R.string.preferences_multiplier_number].icuFormat(it) },
                icon = Icons.Filled.ZoomIn,
                value = { it.lyricsSizeMultiplier },
                default = 1f,
                min = 0.5f,
                max = 4f,
                steps = 400 - 50 - 1,
                onSetValue = { preferences, new -> preferences.copy(lyricsSizeMultiplier = new) },
            ),
        ),
    )

private val Playback =
    Page(
        R.string.preferences_playback,
        Icons.AutoMirrored.Filled.VolumeUp,
        listOf(
            Item.Toggle(
                title = { Strings[R.string.preferences_play_on_output_device_connection] },
                subtitle = { null },
                icon = Icons.Filled.PlayArrow,
                value = { it.playOnOutputDeviceConnection },
                onSetValue = { preferences, new ->
                    preferences.copy(playOnOutputDeviceConnection = new)
                },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_pause_on_focus_loss] },
                subtitle = { null },
                icon = Icons.Filled.Pause,
                value = { it.pauseOnFocusLoss },
                onSetValue = { preferences, new -> preferences.copy(pauseOnFocusLoss = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_reshuffle_on_repeat] },
                subtitle = { null },
                icon = Icons.Filled.Shuffle,
                value = { it.reshuffleOnRepeat },
                onSetValue = { preferences, new -> preferences.copy(reshuffleOnRepeat = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_default_shuffle_mode_track] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.ShuffleOn,
                options = DefaultShuffleMode.entries,
                value = { it.defaultShuffleModeTrack },
                onSetValue = { preferences, new -> preferences.copy(defaultShuffleModeTrack = new) },
            ),
            Item.SingleChoice(
                title = { Strings[R.string.preferences_default_shuffle_mode_list] },
                itemName = { Strings[it.stringId] },
                icon = Icons.Filled.ShuffleOn,
                options = DefaultShuffleMode.entries,
                value = { it.defaultShuffleModeList },
                onSetValue = { preferences, new -> preferences.copy(defaultShuffleModeList = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_audio_offloading] },
                subtitle = { Strings[R.string.preferences_audio_offloading_subtitle] },
                icon = Icons.Filled.BatterySaver,
                value = { it.audioOffloading },
                onSetValue = { preferences, new -> preferences.copy(audioOffloading = new) },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_history_record_min_percentage] },
                numberFormatter = { "${(it * 100).toInt()}%" },
                icon = Icons.Filled.Tune,
                value = { it.historyRecordMinPositionPercentage },
                default = 0.3f,
                min = 0f,
                max = 1f,
                steps = 100 - 1,
                onSetValue = { preferences, new ->
                    preferences.copy(historyRecordMinPositionPercentage = new)
                },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_history_record_min_seconds] },
                numberFormatter = {
                    NumberFormatter.withLocale(Locale.getDefault())
                        .notation(Notation.simple())
                        .unit(MeasureUnit.SECOND)
                        .precision(Precision.integer())
                        .format(it)
                        .toString()
                },
                icon = Icons.Filled.HourglassFull,
                value = { it.historyRecordMinPositionSeconds.toFloat() },
                default = 25f,
                min = 0f,
                max = 300f,
                steps = 300 - 0 - 1,
                onSetValue = { preferences, new ->
                    preferences.copy(historyRecordMinPositionSeconds = new.roundToIntOrZero())
                },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_system_equalizer] },
                subtitle = { null },
                icon = Icons.Filled.BarChart,
                onClick = {
                    if (!playerManager.openSystemEqualizer(context)) {
                        uiManager.toast(Strings[R.string.toast_cant_open_system_equalizer])
                    }
                },
            ),
        ),
    )

private val Radio =
    Page(
        R.string.preferences_radio,
        Icons.Filled.Radio,
        listOf(
            Item.Slider(
                title = { Strings[R.string.preferences_radio_mix_ratio] },
                numberFormatter = {
                    val relatedPct = ((1f - it) * 100).toInt()
                    val topPct = (it * 100).toInt()
                    "${Strings[R.string.preferences_radio_mix_ratio_related]} $relatedPct% / ${Strings[R.string.preferences_radio_mix_ratio_top_played]} $topPct%"
                },
                icon = Icons.Filled.Tune,
                value = { it.radioMixRatio },
                default = 0.5f,
                min = 0f,
                max = 1f,
                steps = 100 - 1,
                onSetValue = { preferences, new -> preferences.copy(radioMixRatio = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_radio_infinite_mode] },
                subtitle = { Strings[R.string.preferences_radio_infinite_mode_subtitle] },
                icon = Icons.Filled.AllInclusive,
                value = { it.radioInfiniteMode },
                onSetValue = { preferences, new -> preferences.copy(radioInfiniteMode = new) },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_radio_batch_size] },
                numberFormatter = {
                    Strings[R.string.preferences_radio_batch_size_number].icuFormat(it.toInt())
                },
                icon = Icons.Filled.PlayArrow,
                value = { it.radioBatchSize.toFloat() },
                default = 50f,
                min = 10f,
                max = 200f,
                steps = 200 - 10 - 1,
                onSetValue = { preferences, new ->
                    preferences.copy(radioBatchSize = new.toInt())
                },
            ),
        ),
    )

private val Indexing =
    Page(
        R.string.preferences_indexing,
        Icons.Filled.AllInbox,
        listOf(
            Item.Clickable(
                title = { Strings[R.string.preferences_indexing_help] },
                subtitle = { null },
                icon = Icons.AutoMirrored.Filled.Help,
                onClick = { uiManager.openDialog(PreferencesIndexingHelpDialog()) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_indexing_advanced_metadata_extraction] },
                subtitle = {
                    Strings[R.string.preferences_indexing_advanced_metadata_extraction_subtitle]
                },
                icon = Icons.Filled.DocumentScanner,
                value = { it.advancedMetadataExtraction },
                onSetValue = { preferences, new ->
                    preferences.copy(advancedMetadataExtraction = new)
                },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_indexing_disable_artwork_color_extraction] },
                subtitle = {
                    Strings[R.string.preferences_indexing_disable_artwork_color_extraction_subtitle]
                },
                icon = Icons.Filled.FormatColorReset,
                value = { it.disableArtworkColorExtraction },
                onSetValue = { preferences, new ->
                    preferences.copy(disableArtworkColorExtraction = new)
                },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_indexing_always_rescan_mediastore] },
                subtitle = {
                    Strings[R.string.preferences_indexing_always_rescan_mediastore_subtitle]
                },
                icon = Icons.Filled.Refresh,
                value = { it.alwaysRescanMediaStore },
                onSetValue = { preferences, new -> preferences.copy(alwaysRescanMediaStore = new) },
            ),
            Item.Slider(
                title = { Strings[R.string.preferences_scan_progress_timeout] },
                numberFormatter = {
                    NumberFormatter.withLocale(Locale.getDefault())
                        .notation(Notation.simple())
                        .unit(MeasureUnit.SECOND)
                        .precision(Precision.integer())
                        .format(it)
                        .toString()
                },
                icon = Icons.Filled.HourglassFull,
                value = { it.scanProgressTimeoutSeconds.toFloat() },
                default = 1f,
                min = 0f,
                max = 60f,
                steps = 60 - 0 - 1,
                onSetValue = { preferences, new ->
                    preferences.copy(scanProgressTimeoutSeconds = new.roundToIntOrZero())
                },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_indexing_artist_separators] },
                subtitle = {
                    Strings[R.string.preferences_indexing_artist_separators_subtitle].icuFormat(
                        it.size
                    )
                },
                icon = Icons.Filled.SafetyDivider,
                value = { it.artistMetadataSeparators },
                onClick = { uiManager.openDialog(PreferencesArtistSeparatorDialog()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_indexing_artist_separator_exceptions] },
                subtitle = {
                    Strings[R.string.preferences_indexing_artist_separator_exceptions_subtitle]
                        .icuFormat(it.size)
                },
                icon = Icons.Filled.DoNotTouch,
                value = { it.artistMetadataSeparatorExceptions },
                onClick = { uiManager.openDialog(PreferencesArtistSeparatorExceptionDialog()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_indexing_genre_separators] },
                subtitle = {
                    Strings[R.string.preferences_indexing_genre_separators_subtitle].icuFormat(
                        it.size
                    )
                },
                icon = Icons.Filled.ShapeLine,
                value = { it.genreMetadataSeparators },
                onClick = { uiManager.openDialog(PreferencesGenreSeparatorDialog()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_indexing_genre_separator_exceptions] },
                subtitle = {
                    Strings[R.string.preferences_indexing_genre_separator_exceptions_subtitle]
                        .icuFormat(it.size)
                },
                icon = Icons.Filled.DoNotTouch,
                value = { it.genreMetadataSeparatorExceptions },
                onClick = { uiManager.openDialog(PreferencesGenreSeparatorExceptionDialog()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_indexing_blacklist] },
                subtitle = {
                    Strings[R.string.preferences_indexing_blacklist_subtitle].icuFormat(
                        it.size,
                        filteredTrackCount,
                    )
                },
                icon = Icons.Filled.Block,
                value = { it.blacklist },
                onClick = { uiManager.openDialog(PreferencesBlacklistDialog()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_indexing_whitelist] },
                subtitle = {
                    Strings[R.string.preferences_indexing_whitelist_subtitle].icuFormat(it.size)
                },
                icon = Icons.Filled.Check,
                value = { it.whitelist },
                onClick = { uiManager.openDialog(PreferencesWhitelistDialog()) },
            ),
        ),
    )

private val Miscellaneous =
    Page(
        R.string.preferences_miscellaneous,
        Icons.Filled.MoreHoriz,
        listOf(
            Item.SingleChoice(
                title = { Strings[R.string.preferences_text_encoding] },
                itemName = {
                    if (it == null) Strings[R.string.preferences_text_encoding_auto_detect]
                    else Charset.forName(it).displayName()
                },
                icon = Icons.Filled.DataObject,
                options =
                    listOf(null as String?) + Charset.availableCharsets().map { it.value.name() },
                value = { it.charsetName },
                onSetValue = { preferences, new -> preferences.copy(charsetName = new) },
            ),
            Item.Toggle(
                title = { Strings[R.string.preferences_treat_embedded_lyrics_as_lrc] },
                subtitle = { null },
                icon = Icons.Filled.Subtitles,
                value = { it.treatEmbeddedLyricsAsLrc },
                onSetValue = { preferences, new ->
                    preferences.copy(treatEmbeddedLyricsAsLrc = new)
                },
            ),
            Item.ConditionalClickable(
                title = { Strings[R.string.preferences_grant_images_permission] },
                subtitle = { Strings[R.string.preferences_grant_images_permission_subtitle] },
                icon = Icons.Filled.PermMedia,
                visibility = { imagesPermissionState?.status?.isGranted == false },
                onClick = {
                    if (partialImagesPermissionState?.status?.isGranted == true) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                setData(Uri.fromParts("package", context.packageName, null))
                            }
                        )
                    } else {
                        imagesPermissionState?.launchPermissionRequest()
                    }
                },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_playlist_io_settings] },
                subtitle = { null },
                icon = Icons.Filled.ImportExport,
                onClick = { uiManager.openDialog(PlaylistIoSettingsDialog()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.playlist_io_sync] },
                subtitle = { null },
                icon = Icons.Filled.Sync,
                onClick = { uiManager.openTopLevelScreen(PlaylistIoScreen.sync()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_import] },
                subtitle = { null },
                icon = Icons.Filled.UploadFile,
                onClick = {
                    uiManager.intentLauncher.get()?.openJsonDocument { uri ->
                        if (uri == null) return@openJsonDocument
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                updatePreferences {
                                    @Suppress("JSON_FORMAT_REDUNDANT")
                                    Json { ignoreUnknownKeys = true }
                                        .decodeFromStream<Preferences>(stream)
                                }
                            }
                            uiManager.toast(Strings[R.string.toast_preferences_import_success])
                        } catch (ex: Exception) {
                            Log.e("Phocid", "Error importing preferences", ex)
                            uiManager.toast(
                                Strings[R.string.toast_preferences_import_failed].icuFormat(
                                    ex.toString()
                                )
                            )
                        }
                    }
                },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_export] },
                subtitle = { null },
                icon = Icons.Filled.Save,
                onClick = {
                    val date =
                        SimpleDateFormat.getInstanceForSkeleton("yyyy-MM-dd", Locale.ROOT)
                            .format(LocalDateTime.now())
                    uiManager.intentLauncher.get()?.createJsonDocument(
                        "phocid-preferences-$date.json"
                    ) { uri ->
                        if (uri == null) return@createJsonDocument
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                @Suppress("JSON_FORMAT_REDUNDANT")
                                Json { ignoreUnknownKeys = true }
                                    .encodeToStream(preferences, stream)
                            }
                            uiManager.toast(Strings[R.string.toast_preferences_export_success])
                        } catch (ex: Exception) {
                            Log.e("Phocid", "Error exporting preferences", ex)
                            uiManager.toast(
                                Strings[R.string.toast_preferences_export_failed].icuFormat(
                                    ex.toString()
                                )
                            )
                        }
                    }
                },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_dump_logcat] },
                subtitle = {
                    Strings[R.string.preferences_dump_logcat_subtitle].icuFormat(logcatDumpPath)
                },
                icon = Icons.Filled.BugReport,
                onClick = { dumpLogcat() },
            ),
        ),
    )
private val About =
    Page(
        R.string.preferences_about,
        Icons.Filled.Info,
        listOf(
            Item.NonInteractive(
                title = { Strings[R.string.preferences_version] },
                subtitle = { BuildConfig.VERSION_NAME },
                icon = Icons.Filled.Commit,
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_website] },
                subtitle = { Strings[R.string.app_url] },
                icon = Icons.Filled.Link,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Strings[R.string.app_url].toUri())
                    )
                },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_license] },
                subtitle = { Strings[R.string.app_license_name] },
                icon = Icons.Filled.Balance,
                onClick = { uiManager.openDialog(PreferencesLicenseDialog()) },
            ),
            Item.Clickable(
                title = { Strings[R.string.preferences_third_party_licenses] },
                subtitle = { null },
                icon = Icons.Filled.Attribution,
                onClick = { uiManager.openDialog(PreferencesThirdPartyLicensesDialog()) },
            ),
        ),
    )

private fun PreferencesScreenContext.dumpLogcat() {
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            try {
                File(requireNotNull(logcatDumpPath)).bufferedWriter().use { writer ->
                    writer.write(BuildConfig.VERSION_NAME)
                    writer.write("\n\n")
                    writer.write("API level ${Build.VERSION.SDK_INT}")
                    writer.write("\n\n")
                    Runtime.getRuntime().exec("logcat -d").inputStream.bufferedReader().use { reader
                        ->
                        while (true) {
                            val line = reader.readLine()
                            if (line == null) break
                            writer.write(line)
                            writer.write("\n")
                        }
                    }

                    uiManager.toast(Strings[R.string.toast_dump_logcat_success])
                }
            } catch (ex: Exception) {
                Log.e("Phocid", "Can't dump logcat", ex)
                uiManager.toast(Strings[R.string.toast_dump_logcat_failed].icuFormat(ex.toString()))
            }
        }
    }
}

private data class PreferencesScreenContext(
    val context: Context,
    val coroutineScope: CoroutineScope,
    val uiManager: UiManager,
    val playerManager: PlayerManager,
    val preferences: Preferences,
    val updatePreferences: ((Preferences) -> Preferences) -> Unit,
    val filteredTrackCount: Int,
    val logcatDumpPath: String?,
    val imagesPermissionState: PermissionState?,
    val partialImagesPermissionState: PermissionState?,
)

private data class Page(val stringId: Int, val icon: ImageVector, val items: List<Item>)

private sealed class Item {
    abstract val title: () -> String

    data class NonInteractive(
        override val title: () -> String,
        val subtitle: PreferencesScreenContext.() -> String?,
        val icon: ImageVector,
    ) : Item()

    data class Clickable<T>(
        override val title: () -> String,
        val subtitle: PreferencesScreenContext.(T) -> String?,
        val icon: ImageVector,
        val value: (Preferences) -> T,
        val onClick: PreferencesScreenContext.() -> Unit,
        val action: Triple<() -> String, ImageVector, PreferencesScreenContext.() -> Unit>? = null,
    ) : Item()

    data class ConditionalClickable(
        override val title: () -> String,
        val subtitle: PreferencesScreenContext.() -> String?,
        val icon: ImageVector,
        val visibility: PreferencesScreenContext.() -> Boolean,
        val onClick: PreferencesScreenContext.() -> Unit,
    ) : Item()

    data class Toggle(
        override val title: () -> String,
        val subtitle: PreferencesScreenContext.(Boolean) -> String?,
        val icon: ImageVector,
        val value: (Preferences) -> Boolean,
        val onSetValue: (preferences: Preferences, new: Boolean) -> Preferences,
    ) : Item()

    companion object {
        fun Clickable(
            title: () -> String,
            subtitle: PreferencesScreenContext.() -> String?,
            icon: ImageVector,
            onClick: PreferencesScreenContext.() -> Unit,
        ): Clickable<Unit> {
            return Clickable(title, { subtitle() }, icon, {}, onClick)
        }

        @Suppress("FunctionName")
        fun <T> SingleChoice(
            title: () -> String,
            itemName: (T) -> String,
            icon: ImageVector,
            options: List<T>,
            value: (Preferences) -> T,
            onSetValue: (preferences: Preferences, new: T) -> Preferences,
        ): Clickable<T> {
            return Clickable(
                title,
                { itemName(it) },
                icon,
                value,
                {
                    uiManager.openDialog(
                        PreferencesSingleChoiceDialog(
                            title = title(),
                            options = options.map { it to itemName(it) },
                            activeOption = value,
                            updatePreferences = onSetValue,
                        )
                    )
                },
            )
        }

        @Suppress("FunctionName")
        fun <T : Any> OrderAndVisibility(
            title: () -> String,
            subtitle: (Preferences) -> String,
            itemName: (T) -> String,
            icon: ImageVector,
            value: (Preferences) -> List<Pair<T, Boolean>>,
            onSetValue: (preferences: Preferences, new: List<Pair<T, Boolean>>) -> Preferences,
        ): Clickable<Preferences> {
            return Clickable(
                title,
                { subtitle(it) },
                icon,
                { it },
                {
                    uiManager.openDialog(
                        PreferencesOrderAndVisibilityDialog(
                            title = title(),
                            itemName = itemName,
                            value = value,
                            onSetValue = onSetValue,
                        )
                    )
                },
            )
        }

        @Suppress("FunctionName")
        fun Slider(
            title: () -> String,
            numberFormatter: (Float) -> String,
            icon: ImageVector,
            value: (Preferences) -> Float,
            default: Float,
            min: Float,
            max: Float,
            steps: Int,
            onSetValue: (preferences: Preferences, new: Float) -> Preferences,
        ): Clickable<Float> {
            return Clickable(
                title,
                { numberFormatter(it) },
                icon,
                value,
                {
                    uiManager.openDialog(
                        PreferencesSteppedSliderDialog(
                            title = title(),
                            numberFormatter = numberFormatter,
                            initialValue = value,
                            defaultValue = default,
                            min = min,
                            max = max,
                            steps = steps,
                            onSetValue = onSetValue,
                        )
                    )
                },
            )
        }

        @Suppress("FunctionName")
        fun TextInput(
            title: () -> String,
            subtitle: PreferencesScreenContext.(String) -> String?,
            icon: ImageVector,
            value: (Preferences) -> String,
            placeholder: (Preferences) -> String,
            allowEmpty: Boolean,
            updatePreferences: (Preferences, String) -> Preferences,
        ): Clickable<String> {
            return Clickable(
                title,
                subtitle,
                icon,
                value,
                {
                    uiManager.openDialog(
                        PreferencesTextInputDialog(
                            title(),
                            placeholder(preferences),
                            allowEmpty,
                            value(preferences),
                            updatePreferences,
                        )
                    )
                },
            )
        }
    }
}
