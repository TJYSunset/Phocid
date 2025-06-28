@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)

package org.sunsetware.phocid.ui.views.preferences

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ibm.icu.number.Notation
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
import com.ibm.icu.util.MeasureUnit
import java.io.File
import java.nio.charset.Charset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.BuildConfig
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.TopLevelScreen
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.DarkThemePreference
import org.sunsetware.phocid.data.HighResArtworkPreference
import org.sunsetware.phocid.data.LyricsDisplayPreference
import org.sunsetware.phocid.data.ShapePreference
import org.sunsetware.phocid.data.TabStylePreference
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.service.NotificationButton
import org.sunsetware.phocid.ui.components.UtilityListHeader
import org.sunsetware.phocid.ui.components.UtilityListItem
import org.sunsetware.phocid.ui.components.UtilitySwitchListItem
import org.sunsetware.phocid.ui.components.negativePadding
import org.sunsetware.phocid.ui.views.library.LibraryScreenTabType
import org.sunsetware.phocid.ui.views.library.LibraryTrackClickAction
import org.sunsetware.phocid.ui.views.player.PlayerScreenLayoutType
import org.sunsetware.phocid.ui.views.playlist.PlaylistIoScreen
import org.sunsetware.phocid.ui.views.playlist.PlaylistIoSettingsDialog
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.roundToIntOrZero

@Stable
object PreferencesScreen : TopLevelScreen() {
    val scrollState: ScrollState = ScrollState(0)

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val uiManager = viewModel.uiManager
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val filteredCount by
            viewModel.unfilteredTrackIndex
                .combine(coroutineScope, viewModel.libraryIndex) { unfiltered, filtered ->
                    unfiltered.tracks.size - filtered.tracks.size
                }
                .collectAsStateWithLifecycle()
        Scaffold(
            topBar = {
                key(MaterialTheme.colorScheme) {
                    TopAppBar(
                        title = { Text(Strings[R.string.preferences]) },
                        navigationIcon = {
                            IconButton(onClick = { uiManager.back() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = Strings[R.string.commons_back],
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
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    UtilityListHeader(Strings[R.string.preferences_interface])
                    UtilityListItem(
                        title = Strings[R.string.preferences_tabs],
                        subtitle =
                            preferences.tabs.joinToString(
                                Strings[R.string.preferences_tabs_separator]
                            ) {
                                Strings[it.type.stringId]
                            },
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesOrderAndVisibilityDialog<LibraryScreenTabType>(
                                        title = Strings[R.string.preferences_tabs],
                                        itemName = { Strings[it.stringId] },
                                        value = { it.tabOrderAndVisibility },
                                        onSetValue = { preferences, value ->
                                            preferences.copy(tabOrderAndVisibility = value)
                                        },
                                    )
                                )
                            },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_scrollable_tabs],
                        checked = preferences.scrollableTabs,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(scrollableTabs = checked) }
                        },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_tab_style],
                        subtitle = Strings[preferences.tabStyle.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<TabStylePreference>(
                                        title = Strings[R.string.preferences_tab_style],
                                        options =
                                            TabStylePreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.tabStyle },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(tabStyle = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_folder_tab_root],
                        subtitle =
                            preferences.folderTabRoot?.let { "/$it" }
                                ?: Strings[R.string.commons_automatic],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesFolderPickerDialog(
                                        unfiltered = false,
                                        initialPath = preferences.folderTabRoot,
                                        onConfirmOrDismiss = { path ->
                                            if (path != null) {
                                                viewModel.updatePreferences {
                                                    it.copy(folderTabRoot = path)
                                                }
                                            }
                                        },
                                    )
                                )
                            },
                        actions = {
                            IconButton(
                                onClick = {
                                    viewModel.updatePreferences { it.copy(folderTabRoot = null) }
                                },
                                modifier = Modifier.negativePadding(end = 12.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    Strings[R.string.commons_reset],
                                )
                            }
                        },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_library_track_click_action],
                        subtitle = Strings[preferences.libraryTrackClickAction.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<LibraryTrackClickAction>(
                                        title =
                                            Strings[
                                                R.string.preferences_library_track_click_action],
                                        options =
                                            LibraryTrackClickAction.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.libraryTrackClickAction },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(libraryTrackClickAction = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_always_show_hint_on_scroll],
                        checked = preferences.alwaysShowHintOnScroll,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences {
                                it.copy(alwaysShowHintOnScroll = checked)
                            }
                        },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_player_screen_layout],
                        subtitle = Strings[preferences.playerScreenLayout.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<PlayerScreenLayoutType>(
                                        title = Strings[R.string.preferences_player_screen_layout],
                                        options =
                                            PlayerScreenLayoutType.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.playerScreenLayout },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(playerScreenLayout = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_swipe_to_remove_from_queue],
                        checked = preferences.swipeToRemoveFromQueue,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences {
                                it.copy(swipeToRemoveFromQueue = checked)
                            }
                        },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_high_res_artwork],
                        subtitle = Strings[preferences.highResArtworkPreference.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<HighResArtworkPreference>(
                                        title = Strings[R.string.preferences_high_res_artwork],
                                        options =
                                            HighResArtworkPreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.highResArtworkPreference },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(highResArtworkPreference = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_sorting_language],
                        subtitle =
                            preferences.sortingLocale?.displayName
                                ?: Strings[R.string.preferences_sorting_language_system],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesSortingLocaleDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_lyrics_display],
                        subtitle = Strings[preferences.lyricsDisplay.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<LyricsDisplayPreference>(
                                        title = Strings[R.string.preferences_lyrics_display],
                                        options =
                                            LyricsDisplayPreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.lyricsDisplay },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(lyricsDisplay = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_notification_buttons],
                        subtitle =
                            preferences.notificationButtons
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(Strings[R.string.preferences_tabs_separator]) {
                                    Strings[it.stringId]
                                } ?: Strings[R.string.preferences_notification_buttons_none],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesOrderAndVisibilityDialog<NotificationButton>(
                                        title = Strings[R.string.preferences_notification_buttons],
                                        itemName = { Strings[it.stringId] },
                                        value = { it.notificationButtonOrderAndVisibility },
                                        onSetValue = { preferences, value ->
                                            preferences.copy(
                                                notificationButtonOrderAndVisibility = value
                                            )
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_ui_scaling],
                        subtitle =
                            Strings[R.string.preferences_multiplier_number].icuFormat(
                                preferences.densityMultiplier
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSteppedSliderDialog(
                                        title = Strings[R.string.preferences_ui_scaling],
                                        initialValue = {
                                            it.preferences.value.densityMultiplier
                                                .times(100)
                                                .roundToIntOrZero()
                                        },
                                        defaultValue = 100,
                                        min = 50,
                                        max = 200,
                                        numberFormatter = {
                                            Strings[R.string.preferences_multiplier_number]
                                                .icuFormat(it / 100f)
                                        },
                                        onSetValue = { viewModel, value ->
                                            viewModel.updatePreferences { preferences ->
                                                preferences.copy(densityMultiplier = value / 100f)
                                            }
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_lyrics_scaling],
                        subtitle =
                            Strings[R.string.preferences_multiplier_number].icuFormat(
                                preferences.lyricsSizeMultiplier
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSteppedSliderDialog(
                                        title = Strings[R.string.preferences_lyrics_scaling],
                                        initialValue = {
                                            it.preferences.value.lyricsSizeMultiplier
                                                .times(100)
                                                .roundToIntOrZero()
                                        },
                                        defaultValue = 100,
                                        min = 50,
                                        max = 400,
                                        numberFormatter = {
                                            Strings[R.string.preferences_multiplier_number]
                                                .icuFormat(it / 100f)
                                        },
                                        onSetValue = { viewModel, value ->
                                            viewModel.updatePreferences { preferences ->
                                                preferences.copy(
                                                    lyricsSizeMultiplier = value / 100f
                                                )
                                            }
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_swipe_threshold],
                        subtitle =
                            Strings[R.string.preferences_multiplier_number].icuFormat(
                                preferences.swipeThresholdMultiplier
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSteppedSliderDialog(
                                        title = Strings[R.string.preferences_swipe_threshold],
                                        initialValue = {
                                            it.preferences.value.swipeThresholdMultiplier
                                                .times(10)
                                                .roundToIntOrZero()
                                        },
                                        defaultValue = 10,
                                        min = 1,
                                        max = 100,
                                        numberFormatter = {
                                            Strings[R.string.preferences_multiplier_number]
                                                .icuFormat(it / 10f)
                                        },
                                        onSetValue = { viewModel, value ->
                                            viewModel.updatePreferences { preferences ->
                                                preferences.copy(
                                                    swipeThresholdMultiplier = value / 10f
                                                )
                                            }
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListHeader(Strings[R.string.preferences_playback])
                    UtilityListItem(
                        title = Strings[R.string.preferences_system_equalizer],
                        modifier =
                            Modifier.clickable {
                                if (!viewModel.playerManager.openSystemEqualizer(context)) {
                                    uiManager.toast(
                                        Strings[R.string.toast_cant_open_system_equalizer]
                                    )
                                }
                            },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_play_on_output_device_connection],
                        checked = preferences.playOnOutputDeviceConnection,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences {
                                it.copy(playOnOutputDeviceConnection = checked)
                            }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_pause_on_focus_loss],
                        checked = preferences.pauseOnFocusLoss,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(pauseOnFocusLoss = checked) }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_audio_offloading],
                        subtitle = Strings[R.string.preferences_audio_offloading_subtitle],
                        checked = preferences.audioOffloading,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(audioOffloading = checked) }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_reshuffle_on_repeat],
                        checked = preferences.reshuffleOnRepeat,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(reshuffleOnRepeat = checked) }
                        },
                    )
                    UtilityListHeader(Strings[R.string.preferences_theme])
                    UtilityListItem(
                        title = Strings[R.string.preferences_dark_theme],
                        subtitle = Strings[preferences.darkTheme.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<DarkThemePreference>(
                                        title = Strings[R.string.preferences_dark_theme],
                                        options =
                                            DarkThemePreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.darkTheme },
                                        updatePreferences = { preferences, darkTheme ->
                                            preferences.copy(darkTheme = darkTheme)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_theme_color],
                        subtitle = Strings[preferences.themeColorSource.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesThemeColorDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_shape],
                        subtitle = Strings[preferences.shapePreference.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<ShapePreference>(
                                        title = Strings[R.string.preferences_shape],
                                        options =
                                            ShapePreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.shapePreference },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(shapePreference = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_pure_background_color],
                        checked = preferences.pureBackgroundColor,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(pureBackgroundColor = checked) }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_colored_global_theme],
                        checked = preferences.coloredGlobalTheme,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(coloredGlobalTheme = checked) }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_colored_cards],
                        checked = preferences.coloredCards,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(coloredCards = checked) }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_colored_player],
                        checked = preferences.coloredPlayer,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(coloredPlayer = checked) }
                        },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_artwork_color],
                        subtitle = Strings[preferences.artworkColorPreference.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<ArtworkColorPreference>(
                                        title = Strings[R.string.preferences_artwork_color],
                                        options =
                                            ArtworkColorPreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.artworkColorPreference },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(artworkColorPreference = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListHeader(Strings[R.string.preferences_indexing])
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_indexing_advanced_metadata_extraction],
                        subtitle =
                            Strings[
                                R.string
                                    .preferences_indexing_advanced_metadata_extraction_subtitle],
                        checked = preferences.advancedMetadataExtraction,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences {
                                it.copy(advancedMetadataExtraction = checked)
                            }
                        },
                    )
                    UtilitySwitchListItem(
                        title =
                            Strings[R.string.preferences_indexing_disable_artwork_color_extraction],
                        subtitle =
                            Strings[
                                R.string
                                    .preferences_indexing_disable_artwork_color_extraction_subtitle],
                        checked = preferences.disableArtworkColorExtraction,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences {
                                it.copy(disableArtworkColorExtraction = checked)
                            }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_indexing_always_rescan_mediastore],
                        subtitle =
                            Strings[
                                R.string.preferences_indexing_always_rescan_mediastore_subtitle],
                        checked = preferences.alwaysRescanMediaStore,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences {
                                it.copy(alwaysRescanMediaStore = checked)
                            }
                        },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_scan_progress_timeout],
                        subtitle =
                            NumberFormatter.withLocale(Locale.getDefault())
                                .notation(Notation.simple())
                                .unit(MeasureUnit.SECOND)
                                .precision(Precision.integer())
                                .format(preferences.scanProgressTimeoutSeconds)
                                .toString(),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSteppedSliderDialog(
                                        title = Strings[R.string.preferences_scan_progress_timeout],
                                        initialValue = {
                                            it.preferences.value.scanProgressTimeoutSeconds
                                        },
                                        defaultValue = 1,
                                        min = 0,
                                        max = 60,
                                        numberFormatter = {
                                            NumberFormatter.withLocale(Locale.getDefault())
                                                .notation(Notation.simple())
                                                .unit(MeasureUnit.SECOND)
                                                .precision(Precision.integer())
                                                .format(it)
                                                .toString()
                                        },
                                        onSetValue = { viewModel, value ->
                                            viewModel.updatePreferences { preferences ->
                                                preferences.copy(scanProgressTimeoutSeconds = value)
                                            }
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_artist_separators],
                        subtitle =
                            Strings[R.string.preferences_indexing_artist_separators_subtitle]
                                .icuFormat(preferences.artistMetadataSeparators.size),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesArtistSeparatorDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_artist_separator_exceptions],
                        subtitle =
                            Strings[
                                    R.string
                                        .preferences_indexing_artist_separator_exceptions_subtitle]
                                .icuFormat(preferences.artistMetadataSeparatorExceptions.size),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesArtistSeparatorExceptionDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_genre_separators],
                        subtitle =
                            Strings[R.string.preferences_indexing_genre_separators_subtitle]
                                .icuFormat(preferences.genreMetadataSeparators.size),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesGenreSeparatorDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_genre_separator_exceptions],
                        subtitle =
                            Strings[
                                    R.string
                                        .preferences_indexing_genre_separator_exceptions_subtitle]
                                .icuFormat(preferences.genreMetadataSeparatorExceptions.size),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesGenreSeparatorExceptionDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_blacklist],
                        subtitle =
                            Strings[R.string.preferences_indexing_blacklist_subtitle].icuFormat(
                                preferences.blacklist.size,
                                filteredCount,
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesBlacklistDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_whitelist],
                        subtitle =
                            Strings[R.string.preferences_indexing_whitelist_subtitle].icuFormat(
                                preferences.whitelist.size
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesWhitelistDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_help],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesIndexingHelpDialog())
                            },
                    )
                    UtilityListHeader(Strings[R.string.preferences_data])
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val imagesPermissionState =
                            rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
                        val partialPermissionState =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                rememberPermissionState(
                                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                                )
                            } else {
                                null
                            }

                        if (!imagesPermissionState.status.isGranted) {
                            UtilityListItem(
                                title = Strings[R.string.preferences_grant_images_permission],
                                subtitle =
                                    Strings[R.string.preferences_grant_images_permission_subtitle],
                                modifier =
                                    Modifier.clickable {
                                        if (partialPermissionState?.status?.isGranted == true) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                    .apply {
                                                        setData(
                                                            Uri.fromParts(
                                                                "package",
                                                                context.packageName,
                                                                null,
                                                            )
                                                        )
                                                    }
                                            )
                                        } else {
                                            imagesPermissionState.launchPermissionRequest()
                                        }
                                    },
                            )
                        }
                    }
                    UtilityListItem(
                        title = Strings[R.string.preferences_text_encoding],
                        subtitle =
                            preferences.charsetName
                                ?: Strings[R.string.preferences_text_encoding_auto_detect],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<String?>(
                                        title = Strings[R.string.preferences_text_encoding],
                                        options =
                                            listOf(
                                                null to
                                                    Strings[
                                                        R.string
                                                            .preferences_text_encoding_auto_detect]
                                            ) +
                                                Charset.availableCharsets().map {
                                                    it.value.name() to it.value.displayName()
                                                },
                                        activeOption = { it.charsetName },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(charsetName = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_playlist_io_settings],
                        modifier =
                            Modifier.clickable { uiManager.openDialog(PlaylistIoSettingsDialog()) },
                    )
                    UtilityListItem(
                        title = Strings[R.string.playlist_io_sync],
                        modifier =
                            Modifier.clickable {
                                uiManager.openTopLevelScreen(PlaylistIoScreen.sync())
                            },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_treat_embedded_lyrics_as_lrc],
                        checked = preferences.treatEmbeddedLyricsAsLrc,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences {
                                it.copy(treatEmbeddedLyricsAsLrc = checked)
                            }
                        },
                    )
                    UtilityListHeader(Strings[R.string.preferences_about])
                    UtilityListItem(
                        title = Strings[R.string.preferences_version],
                        subtitle = BuildConfig.VERSION_NAME,
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_website],
                        subtitle = Strings[R.string.app_url],
                        modifier =
                            Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(Strings[R.string.app_url]))
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_license],
                        subtitle = Strings[R.string.app_license_name],
                        modifier =
                            Modifier.clickable { uiManager.openDialog(PreferencesLicenseDialog()) },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_third_party_licenses],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesThirdPartyLicensesDialog())
                            },
                    )
                    val logcatPath = remember {
                        context.getExternalFilesDir(null)?.let {
                            FilenameUtils.concat(it.path, "logcat.txt")
                        }
                    }
                    UtilityListItem(
                        title = Strings[R.string.preferences_dump_logcat],
                        subtitle =
                            Strings[R.string.preferences_dump_logcat_subtitle].icuFormat(
                                logcatPath
                            ),
                        modifier =
                            Modifier.clickable {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            File(checkNotNull(logcatPath)).bufferedWriter().use {
                                                writer ->
                                                writer.write(BuildConfig.VERSION_NAME)
                                                writer.write("\n\n")
                                                writer.write("API level ${Build.VERSION.SDK_INT}")
                                                writer.write("\n\n")
                                                Runtime.getRuntime()
                                                    .exec("logcat -d")
                                                    .inputStream
                                                    .bufferedReader()
                                                    .use { reader ->
                                                        while (true) {
                                                            val line = reader.readLine()
                                                            if (line == null) break
                                                            writer.write(line)
                                                            writer.write("\n")
                                                        }
                                                    }

                                                uiManager.toast(
                                                    Strings[R.string.toast_dump_logcat_success]
                                                )
                                            }
                                        } catch (ex: Exception) {
                                            Log.e("Phocid", "Can't dump logcat", ex)
                                            uiManager.toast(
                                                Strings[R.string.toast_dump_logcat_failed]
                                                    .icuFormat(ex.toString())
                                            )
                                        }
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}
