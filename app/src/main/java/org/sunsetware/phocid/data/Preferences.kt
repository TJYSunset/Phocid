package org.sunsetware.phocid.data

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ibm.icu.text.Collator
import com.ibm.icu.text.RuleBasedCollator
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.sunsetware.phocid.R
import org.sunsetware.phocid.globals.SystemLocale
import org.sunsetware.phocid.service.NotificationButton
import org.sunsetware.phocid.ui.theme.GRAY
import org.sunsetware.phocid.ui.theme.Oklch
import org.sunsetware.phocid.ui.theme.hashColor
import org.sunsetware.phocid.ui.theme.toOklch
import org.sunsetware.phocid.ui.views.library.LibraryScreenCollectionType
import org.sunsetware.phocid.ui.views.library.LibraryScreenTabInfo
import org.sunsetware.phocid.ui.views.library.LibraryScreenTabType
import org.sunsetware.phocid.ui.views.library.LibraryTrackClickAction
import org.sunsetware.phocid.ui.views.player.PlayerScreenLayoutType
import org.sunsetware.phocid.utils.UUIDSerializer

/**
 * Changes to this class should not change types of existing members, and new members must have a
 * default value, or else the user will have their preferences wiped after an app update.
 */
@Immutable
@Serializable
data class Preferences(
    // Interface
    val darkTheme: DarkThemePreference = DarkThemePreference.SYSTEM,
    val themeColorSource: ThemeColorSource = ThemeColorSource.MATERIAL_YOU,
    val customThemeColor: CustomThemeColor = CustomThemeColor(50, 0),
    val coloredGlobalTheme: Boolean = true,
    val pureBackgroundColor: Boolean = false,
    val artworkColorPreference: ArtworkColorPreference = ArtworkColorPreference.MUTED_FIRST,
    val shapePreference: ShapePreference = ShapePreference.SQUARE,
    val densityMultiplier: Float = 1f,
    val swipeThresholdMultiplier: Float = 1f,
    val notificationButtonOrderAndVisibility: List<Pair<NotificationButton, Boolean>> =
        NotificationButton.entries.map {
            it to (it == NotificationButton.REPEAT || it == NotificationButton.SHUFFLE)
        },
    val highResArtworkPreference: HighResArtworkPreference = HighResArtworkPreference.PLAYER_ONLY,
    val alwaysShowHintOnScroll: Boolean = false,
    val conjunctionSymbol: String = "",
    // Home screen
    val tabOrderAndVisibility: List<Pair<LibraryScreenTabType, Boolean>> =
        LibraryScreenTabType.entries.map { it to true },
    val scrollableTabs: Boolean = true,
    val tabStyle: TabStylePreference = TabStylePreference.TEXT_ONLY,
    val folderTabRoot: String? = null,
    val coloredCards: Boolean = true,
    val libraryTrackClickAction: LibraryTrackClickAction = LibraryTrackClickAction.PLAY_ALL,
    val sortingLocaleLanguageTag: String? = null,
    // Now playing
    val playerScreenLayout: PlayerScreenLayoutType = PlayerScreenLayoutType.DEFAULT,
    /** Use artwork color as theme color */
    val coloredPlayer: Boolean = true,
    val colorfulPlayerBackground: Boolean = true,
    val swipeToRemoveFromQueue: Boolean = false,
    val lyricsDisplay: LyricsDisplayPreference = LyricsDisplayPreference.DEFAULT,
    val lyricsSizeMultiplier: Float = 1f,
    // Playback
    val playOnOutputDeviceConnection: Boolean = false,
    val pauseOnFocusLoss: Boolean = true,
    val reshuffleOnRepeat: Boolean = false,
    val defaultShuffleModeTrack: DefaultShuffleMode = DefaultShuffleMode.KEEP_CURRENT,
    val defaultShuffleModeList: DefaultShuffleMode = DefaultShuffleMode.KEEP_CURRENT,
    val audioOffloading: Boolean = false,
    val historyRecordMinPositionPercentage: Float = 0.3f,
    val historyRecordMinPositionSeconds: Int = 25,
    // Indexing
    val advancedMetadataExtraction: Boolean = false,
    val disableArtworkColorExtraction: Boolean = false,
    val alwaysRescanMediaStore: Boolean = false,
    val scanProgressTimeoutSeconds: Int = 1,
    val artistMetadataSeparators: List<String> = listOf("&", ";", ",", "+", "/", " feat.", " ft."),
    val artistMetadataSeparatorExceptions: List<String> = emptyList(),
    val genreMetadataSeparators: List<String> = listOf("&", ";", ",", "+", "/"),
    val genreMetadataSeparatorExceptions: List<String> =
        listOf("R&B", "Rhythm & Blues", "D&B", "Drum & Bass"),
    val blacklist: List<String> = emptyList(),
    val whitelist: List<String> = emptyList(),
    // Miscellaneous
    val charsetName: String? = null,
    val treatEmbeddedLyricsAsLrc: Boolean = true,
    val playlistIoSettings: PlaylistIoSettings = PlaylistIoSettings(),
    val playlistIoSyncLocation: String? = null,
    val playlistIoSyncSettings: PlaylistIoSettings =
        PlaylistIoSettings(ignoreLocation = false, removeInvalid = false, exportRelative = true),
    val playlistIoSyncMappings: Map<@Serializable(with = UUIDSerializer::class) UUID, String> =
        emptyMap(),
    // Widget
    val widgetArtworkBackground: Boolean = true,
    val widgetAccentBackground: Boolean = true,
    val widgetDarkTheme: DarkThemePreference = DarkThemePreference.SYSTEM,
    val widgetLayout: WidgetLayout = WidgetLayout.SMALL,
    val widgetArtworkResolutionLimit: Int = 700,
    // Radio
    val radioMixRatio: Float = 0.5f,
    val radioInfiniteMode: Boolean = false,
    val radioBatchSize: Int = 50,
    // Hidden
    val tabSettings: Map<LibraryScreenTabType, LibraryScreenTabInfo> =
        LibraryScreenTabType.entries.associateWith { LibraryScreenTabInfo(it) },
    val collectionViewSorting: Map<LibraryScreenCollectionType, Pair<String, Boolean>> =
        LibraryScreenCollectionType.entries.associateWith {
            Pair(it.sortingOptions.keys.first(), true)
        },
) {
    fun upgrade(): Preferences {
        val newTabSettings =
            tabSettings.filterKeys { LibraryScreenTabType.entries.contains(it) } +
                LibraryScreenTabType.entries.toSet().minus(tabSettings.keys).associateWith {
                    LibraryScreenTabInfo(it)
                }
        val newTabOrderAndVisibility =
            tabOrderAndVisibility
                .filter { LibraryScreenTabType.entries.contains(it.first) }
                .distinctBy { it.first } +
                LibraryScreenTabType.entries
                    .toSet()
                    .minus(tabOrderAndVisibility.map { it.first }.toSet())
                    .map { it to false }
        val newCollectionViewSorting =
            collectionViewSorting.filterKeys { LibraryScreenCollectionType.entries.contains(it) } +
                LibraryScreenCollectionType.entries
                    .toSet()
                    .minus(collectionViewSorting.keys)
                    .associateWith { Pair(it.sortingOptions.keys.first(), true) }
        val newNotificationButtonOrderAndVisibility =
            notificationButtonOrderAndVisibility
                .filter { NotificationButton.entries.contains(it.first) }
                .distinctBy { it.first } +
                NotificationButton.entries
                    .toSet()
                    .minus(notificationButtonOrderAndVisibility.map { it.first }.toSet())
                    .map { it to false }
        return copy(
            tabSettings = newTabSettings,
            tabOrderAndVisibility = newTabOrderAndVisibility,
            collectionViewSorting = newCollectionViewSorting,
            notificationButtonOrderAndVisibility = newNotificationButtonOrderAndVisibility,
        )
    }

    @Transient
    val blacklistRegexes =
        blacklist.mapNotNull {
            try {
                Regex(it, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                null
            }
        }

    @Transient
    val whitelistRegexes =
        whitelist.mapNotNull {
            try {
                Regex(it, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                null
            }
        }

    @Transient
    val tabs =
        tabOrderAndVisibility
            .filter { it.second }
            .mapNotNull { tabSettings[it.first] }
            .takeIf { it.isNotEmpty() } ?: listOf(tabSettings[LibraryScreenTabType.TRACKS]!!)

    @Transient
    val notificationButtons =
        notificationButtonOrderAndVisibility.filter { it.second }.map { it.first }

    @Transient val sortingLocale = sortingLocaleLanguageTag?.let { Locale.forLanguageTag(it) }

    @Transient
    val sortCollator =
        (if (sortingLocale != null) Collator.getInstance(sortingLocale)
            else Collator.getInstance(SystemLocale))
            .apply {
                this.strength = Collator.PRIMARY
                (this as RuleBasedCollator).numericCollation = true
            }
            .freeze() as RuleBasedCollator

    @Transient
    val searchCollator =
        (if (sortingLocale != null) Collator.getInstance(sortingLocale)
            else Collator.getInstance(SystemLocale))
            .apply { this.strength = Collator.PRIMARY }
            .freeze() as RuleBasedCollator
}

@Serializable
enum class TabStylePreference(val stringId: Int) {
    TEXT_AND_ICON(R.string.preferences_tab_style_text_and_icon),
    TEXT_ONLY(R.string.preferences_tab_style_text_only),
    ICON_ONLY(R.string.preferences_tab_style_icon_only),
}

@Serializable
enum class DarkThemePreference(val stringId: Int, val boolean: Boolean?) {
    SYSTEM(R.string.preferences_dark_theme_system, null),
    DARK(R.string.preferences_dark_theme_dark, true),
    LIGHT(R.string.preferences_dark_theme_light, false),
}

@Serializable
enum class ThemeColorSource(val stringId: Int) {
    DEFAULT(R.string.preferences_theme_color_source_default),
    MATERIAL_YOU(R.string.preferences_theme_color_source_material_you),
    CUSTOM(R.string.preferences_theme_color_source_custom),
}

@Serializable
data class CustomThemeColor(val chromaPercentage: Int, val hueDegrees: Int) {
    fun toColor(lightness: Float): Color {
        return Oklch(lightness, chromaPercentage / 100f * 0.4f, hueDegrees / 180f * PI.toFloat())
            .toColor()
    }
}

@Serializable
enum class ArtworkColorPreference(val stringId: Int) {
    VIBRANT_FIRST(R.string.preferences_artwork_color_vibrant_first),
    MUTED_FIRST(R.string.preferences_artwork_color_muted_first),
    MUTED_ONLY(R.string.preferences_artwork_color_muted_only),
}

@Serializable
enum class ShapePreference(val stringId: Int, val artworkShape: Shape, val cardShape: Shape) {
    SQUARE(R.string.preferences_shape_square, RoundedCornerShape(0.dp), RoundedCornerShape(0.dp)),
    ROUNDED_SQUARE(
        R.string.preferences_shape_rounded_square,
        RoundedCornerShape(8.dp),
        RoundedCornerShape(12.dp),
    ),
    CIRCLE(R.string.preferences_shape_circle, CircleShape, RoundedCornerShape(12.dp)),
}

@Stable
fun Track.getArtworkColor(preference: ArtworkColorPreference): Color {
    return if (this === InvalidTrack) GRAY
    else
        when (preference) {
            ArtworkColorPreference.VIBRANT_FIRST ->
                vibrantColor ?: mutedColor ?: if (hasArtwork) GRAY else fileName.hashColor()
            ArtworkColorPreference.MUTED_FIRST ->
                mutedColor ?: vibrantColor ?: if (hasArtwork) GRAY else fileName.hashColor()
            ArtworkColorPreference.MUTED_ONLY ->
                mutedColor
                    ?: vibrantColor?.toOklch()?.copy(c = 0.05f)?.toColor()
                    ?: if (hasArtwork) GRAY else fileName.hashColor()
        }
}

@Serializable
enum class HighResArtworkPreference(
    val stringId: Int,
    val player: Boolean,
    val library: Boolean,
    val small: Boolean,
) {
    NEVER(R.string.preferences_high_res_artwork_never, false, false, false),
    PLAYER_ONLY(R.string.preferences_high_res_artwork_player_only, true, false, false),
    LARGE_ONLY(R.string.preferences_high_res_artwork_large_only, true, true, false),
    ALWAYS(R.string.preferences_high_res_artwork_always, true, true, true),
}

@Serializable
enum class LyricsDisplayPreference(val stringId: Int) {
    DISABLED(R.string.preferences_lyrics_display_disabled),
    DEFAULT(R.string.preferences_lyrics_display_default),
    TWO_LINES(R.string.preferences_lyrics_display_two_lines),
}

@Serializable
enum class DefaultShuffleMode(val stringId: Int) {
    KEEP_CURRENT(R.string.preferences_default_shuffle_mode_keep_current),
    OFF(R.string.preferences_default_shuffle_mode_off),
    ON(R.string.preferences_default_shuffle_mode_on),
}

@Serializable
enum class WidgetLayout(val stringId: Int, val previewId: Int, val standaloneArtwork: Boolean) {
    SMALL(R.string.preferences_widget_layout_small, R.drawable.widget_preview_small, false),
    MEDIUM(R.string.preferences_widget_layout_medium, R.drawable.widget_preview_medium, false),
    LARGE(R.string.preferences_widget_layout_large, R.drawable.widget_preview_large, false),
    EXTRA_LARGE(
        R.string.preferences_widget_layout_extra_large,
        R.drawable.widget_preview_extra_large,
        false,
    ),
    SIDE_ARTWORK(
        R.string.preferences_widget_layout_side_artwork,
        R.drawable.widget_preview_side_artwork,
        true,
    ),
    SIDE_ARTWORK_LARGE(
        R.string.preferences_widget_layout_side_artwork_large,
        R.drawable.widget_preview_side_artwork_large,
        true,
    ),
}
