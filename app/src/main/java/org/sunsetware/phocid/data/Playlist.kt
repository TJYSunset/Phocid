@file:OptIn(ExperimentalSerializationApi::class)

package org.sunsetware.phocid.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import com.ibm.icu.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeToOrSelf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.MainActivity
import org.sunsetware.phocid.PLAYLISTS_FILE_NAME
import org.sunsetware.phocid.R
import org.sunsetware.phocid.SHORTCUT_PLAYLIST
import org.sunsetware.phocid.SHORTCUT_PLAYLIST_EXTRA_KEY
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.views.library.LibraryScreenTabType
import org.sunsetware.phocid.utils.CaseInsensitiveMap
import org.sunsetware.phocid.utils.UUIDSerializer
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.decodeWithCharsetName
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.listSafFiles
import org.sunsetware.phocid.utils.map
import org.sunsetware.phocid.utils.trimAndNormalize

enum class SpecialPlaylist(
    /** Version 8 UUID. Guaranteed to not collide with [UUID.randomUUID]. */
    val key: UUID,
    val titleId: Int,
    val order: Int,
    val icon: ImageVector,
    val color: Color,
) {
    FAVORITES(
        UUID.fromString("00000000-0000-8000-8000-000000000000"),
        R.string.playlist_special_favorites,
        0,
        Icons.Outlined.FavoriteBorder,
        Color(0xffd2849c),
    )
}

val SpecialPlaylistLookup = SpecialPlaylist.entries.associateBy { it.key }

@Stable
class PlaylistManager(
    private val context: Application,
    private val coroutineScope: CoroutineScope,
    private val preferences: StateFlow<Preferences>,
    private val libraryIndex: StateFlow<LibraryIndex>,
) : AutoCloseable {
    private lateinit var _playlists: MutableStateFlow<Map<UUID, Playlist>>
    lateinit var playlists: StateFlow<Map<UUID, RealizedPlaylist>>
    private lateinit var saveManager: SaveManager<Map<String, Playlist>>
    private lateinit var syncJob: Job
    private lateinit var shortcutJob: Job

    private val syncMutex = Mutex()
    private val syncPending = AtomicBoolean(false)
    private val _syncLog = MutableStateFlow(null as String?)
    val syncLog = _syncLog.asStateFlow()

    fun initialize() {
        _playlists =
            MutableStateFlow(
                loadCbor<Map<String, Playlist>>(context, PLAYLISTS_FILE_NAME, false)?.mapKeys {
                    UUID.fromString(it.key)
                } ?: mapOf(SpecialPlaylist.FAVORITES.key to Playlist(""))
            )
        playlists =
            _playlists.combine(
                coroutineScope,
                libraryIndex.map(coroutineScope) { libraryIndex ->
                    libraryIndex.tracks.values.associateBy { it.path }
                },
            ) { playlists, trackIndex ->
                playlists.mapValues { it.value.realize(SpecialPlaylistLookup[it.key], trackIndex) }
            }
        saveManager =
            SaveManager(
                context,
                coroutineScope,
                _playlists.map(coroutineScope) { playlists ->
                    playlists.mapKeys { it.key.toString() }
                },
                PLAYLISTS_FILE_NAME,
                false,
            )
        syncJob =
            coroutineScope.launch {
                _playlists.onEach { if (syncPending.get()) syncPlaylists() }.collect()
            }
        shortcutJob =
            coroutineScope.launch {
                playlists
                    .combine(
                        preferences.map { it.sortCollator to it.tabSettings }.distinctUntilChanged()
                    ) { playlists, (sortCollator, tabSettings) ->
                        val tabSettings = tabSettings[LibraryScreenTabType.PLAYLISTS]!!
                        val uuids =
                            playlists
                                .asIterable()
                                .sortedBy(
                                    sortCollator,
                                    tabSettings.sortingKeys,
                                    tabSettings.sortAscending,
                                ) {
                                    it.value
                                }
                                .take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
                                .map { it.key }
                                .toSet()

                        // Remove extra shortcuts
                        val invalidShortcuts =
                            ShortcutManagerCompat.getDynamicShortcuts(context)
                                .filter { shortcut ->
                                    shortcut.intent.action == SHORTCUT_PLAYLIST &&
                                        !uuids.contains(
                                            shortcut.intent.extras
                                                ?.getString(SHORTCUT_PLAYLIST_EXTRA_KEY)
                                                ?.let {
                                                    try {
                                                        UUID.fromString(it)
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                }
                                        )
                                }
                                .map { it.id }
                        ShortcutManagerCompat.removeDynamicShortcuts(context, invalidShortcuts)

                        // Push shortcuts
                        val shortcuts =
                            uuids.mapIndexed { i, uuid ->
                                playlistShortcut(
                                    context,
                                    "playlist",
                                    uuid,
                                    checkNotNull(playlists[uuid]).displayName,
                                    i + 1,
                                )
                            }
                        if (!ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)) {
                            Log.e("Phocid", "Shortcut update is rate limited")
                        }
                    }
                    .catch { Log.e("Phocid", "Error updating playlist shortcuts", it) }
                    .collect()
            }
    }

    override fun close() {
        saveManager.close()
        syncJob.cancel()
        shortcutJob.cancel()
    }

    fun updatePlaylist(
        key: UUID,
        lastModified: Long = System.currentTimeMillis(),
        setSyncPending: Boolean = true,
        transform: (Playlist) -> Playlist,
    ) {
        if (setSyncPending) syncPending.set(true)
        _playlists.update { playlists ->
            if (playlists.containsKey(key)) {
                playlists.mapValues {
                    if (it.key == key) transform(it.value).copy(lastModified = lastModified)
                    else it.value
                }
            } else {
                playlists + Pair(key, transform(Playlist("")))
            }
        }
    }

    /** I won't trust Android JVM's randomness. */
    fun addPlaylist(playlist: Playlist): UUID {
        syncPending.set(true)
        while (true) {
            val key = UUID.randomUUID()
            var success = false
            _playlists.update {
                if (!it.containsKey(key)) {
                    success = true
                    (it + Pair(key, playlist))
                } else {
                    // Assignment to false is necessary due to [update] might be rerun.
                    success = false
                    it
                }
            }
            if (success) return key
        }
    }

    fun removePlaylist(key: UUID) {
        syncPending.set(true)
        _playlists.update { it - key }
    }

    fun syncPlaylists() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                syncPending.set(true)
                if (syncMutex.tryLock()) {
                    try {
                        while (syncPending.getAndSet(false)) {
                            syncPlaylistsInner()
                        }
                    } finally {
                        syncMutex.unlock()
                    }
                }
            }
        }
    }

    /** Reduce nesting. */
    private fun syncPlaylistsInner() {
        val preferences = preferences.value
        val libraryIndex = libraryIndex.value
        if (preferences.playlistIoSyncLocation == null) return
        val playlists = playlists.value
        val uri = Uri.parse(preferences.playlistIoSyncLocation)
        var error = false
        val syncLog = StringBuilder()
        syncLog.appendLine(DateFormat.getInstance().format(Date(System.currentTimeMillis())))

        val hasPermission =
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        if (!hasPermission) {
            error = true
            syncLog.appendLine(Strings[R.string.playlist_io_sync_log_no_persistable_permission])
        }

        val files =
            listSafFiles(context, uri, false) {
                it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true)
            }
        if (files == null) {
            error = true
            syncLog.appendLine(Strings[R.string.playlist_io_sync_log_error_listing_files])
        }

        if (preferences.playlistIoSyncMappings.values.groupBy { it }.any { it.value.size > 1 }) {
            error = true
            syncLog.appendLine(Strings[R.string.playlist_io_sync_log_conflicting_mappings])
        }

        if (!error) {
            for ((key, fileName) in preferences.playlistIoSyncMappings) {
                val playlist = playlists[key]
                val file = files?.get(fileName)
                if (playlist == null) {
                    error = true
                    syncLog.appendLine(
                        Strings[R.string.playlist_io_sync_log_missing_playlist].icuFormat(fileName)
                    )
                } else if (file == null) {
                    error = true
                    syncLog.appendLine(
                        Strings[R.string.playlist_io_sync_log_missing_file].icuFormat(
                            playlist.displayName,
                            fileName,
                        )
                    )
                } else if (file.lastModified == null) {
                    error = true
                    syncLog.appendLine(
                        Strings[R.string.playlist_io_sync_log_no_file_timestamp].icuFormat(
                            playlist.displayName,
                            fileName,
                        )
                    )
                } else if (playlist.lastModified < file.lastModified) {
                    try {
                        requireNotNull(context.contentResolver.openInputStream(file.uri)).use {
                            inputStream ->
                            val newPlaylist =
                                parseM3u(
                                    FilenameUtils.getBaseName(file.name),
                                    inputStream.readBytes(),
                                    libraryIndex.tracks.values.map { it.path }.toSet(),
                                    preferences.playlistIoSyncSettings,
                                    if (FilenameUtils.getExtension(file.name).equals("m3u8", true))
                                        Charsets.UTF_8.name()
                                    else preferences.charsetName,
                                    0,
                                )
                            updatePlaylist(key, file.lastModified, false) { newPlaylist }
                        }
                        syncLog.appendLine(
                            Strings[R.string.playlist_io_sync_log_import_ok].icuFormat(
                                playlist.displayName,
                                fileName,
                            )
                        )
                    } catch (ex: Exception) {
                        error = true
                        syncLog.appendLine(
                            Strings[R.string.playlist_io_sync_log_import_error].icuFormat(
                                playlist.displayName,
                                fileName,
                                ex.stackTraceToString(),
                            )
                        )
                    }
                } else if (playlist.lastModified > file.lastModified) {
                    try {
                        requireNotNull(context.contentResolver.openOutputStream(file.uri, "wt"))
                            .use { outputStream ->
                                outputStream.write(
                                    playlist
                                        .toM3u(preferences.playlistIoSyncSettings)
                                        .toByteArray(Charsets.UTF_8)
                                )
                            }
                        // SAF doesn't support setting file's last modified date, so we'll have to
                        // set the playlist's date instead to keep both the same
                        updatePlaylist(
                            key,
                            requireNotNull(
                                listSafFiles(context, uri, false) {
                                        it.name.endsWith(".m3u", true) ||
                                            it.name.endsWith(".m3u8", true)
                                    }
                                    ?.get(file.relativePath)
                                    ?.lastModified
                            ),
                            false,
                        ) {
                            it
                        }
                        syncLog.appendLine(
                            Strings[R.string.playlist_io_sync_log_export_ok].icuFormat(
                                playlist.displayName,
                                fileName,
                            )
                        )
                    } catch (ex: Exception) {
                        error = true
                        syncLog.appendLine(
                            Strings[R.string.playlist_io_sync_log_export_error].icuFormat(
                                playlist.displayName,
                                fileName,
                                ex.stackTraceToString(),
                            )
                        )
                    }
                } else {
                    syncLog.appendLine(
                        Strings[R.string.playlist_io_sync_log_up_to_date].icuFormat(
                            playlist.displayName,
                            fileName,
                        )
                    )
                }
            }
        } else {
            syncLog.appendLine(Strings[R.string.playlist_io_sync_log_skipped_all])
        }

        _syncLog.update { syncLog.toString() }

        if (error) {
            Log.e("PhocidPlaylistSync", syncLog.toString())
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(
                        context,
                        Strings[R.string.toast_playlist_io_sync_error],
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }
    }

    fun toggleFavorite(track: Track) {
        if (track == InvalidTrack) return
        updatePlaylist(SpecialPlaylist.FAVORITES.key) { playlist ->
            if (playlist.entries.any { it.path == track.path }) {
                playlist.copy(entries = playlist.entries.filter { it.path != track.path })
            } else {
                playlist.addTracks(listOf(track))
            }
        }
    }
}

fun Map<UUID, RealizedPlaylist>.isFavorite(track: Track): Boolean {
    return this[SpecialPlaylist.FAVORITES.key]?.entries?.any { it.track?.id == track.id } == true
}

/**
 * Changes to this class should not change types of existing members, and new members must have a
 * default value, or else the user will have their playlists wiped after an app update.
 */
@Immutable
@Serializable
data class Playlist(
    val name: String,
    @Required val entries: List<PlaylistEntry> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val lastModified: Long = 0,
) {
    /** I won't trust Android JVM's randomness. */
    fun addPaths(paths: List<String>): Playlist {
        val existingKeys = entries.map { it.key }.toSet()
        val newKeys = mutableListOf<UUID>()
        repeat(paths.size) {
            var key = UUID.randomUUID()
            while (existingKeys.contains(key) || newKeys.contains(key)) {
                key = UUID.randomUUID()
            }
            newKeys.add(key)
        }
        return copy(
            entries = entries + newKeys.zip(paths) { key, path -> PlaylistEntry(key, path) }
        )
    }

    fun addTracks(tracks: List<Track>): Playlist {
        return addPaths(tracks.map { it.path })
    }
}

private fun Playlist.realize(
    specialType: SpecialPlaylist?,
    trackIndex: Map<String, Track>,
): RealizedPlaylist {
    return RealizedPlaylist(
        specialType,
        name,
        entries.mapIndexed { index, entry ->
            RealizedPlaylistEntry(entry.key, index, trackIndex[entry.path], entry)
        },
        lastModified,
    )
}

@Immutable
data class RealizedPlaylist(
    val specialType: SpecialPlaylist?,
    val customName: String,
    val entries: List<RealizedPlaylistEntry> = emptyList(),
    val lastModified: Long,
) : Searchable, Sortable {
    val displayName
        get() = specialType?.titleId?.let { Strings[it] } ?: customName

    val validTracks = entries.mapNotNull { it.track }
    val invalidCount = entries.count { it.track == null }
    val displayStatistics
        get() =
            Strings.separate(
                Strings[R.string.count_track].icuFormat(validTracks.size),
                invalidCount
                    .takeIf { it != 0 }
                    ?.let { Strings[R.string.count_invalid_track].icuFormat(it) },
            )

    override val searchableStrings = listOf(displayName)
    override val sortPlaylist
        get() = Pair(specialType, customName)

    override val sortTrackCount
        get() = entries.size

    companion object {
        val CollectionSortingOptions =
            mapOf(
                "Name" to SortingOption(R.string.sorting_name, listOf(SortingKey.PLAYLIST)),
                "Track count" to
                    SortingOption(
                        R.string.sorting_track_count,
                        listOf(SortingKey.TRACK_COUNT, SortingKey.PLAYLIST),
                    ),
            )
        val TrackSortingOptions =
            mapOf("Custom" to SortingOption(R.string.sorting_custom, emptyList())) +
                Track.SortingOptions
    }
}

/**
 * TODO: Use relative path? (But MediaStore provides no validity guarantee of the relative path, so
 *   it will likely break in a future Android release considering Google's track record)
 */
@Immutable
@Serializable
data class PlaylistEntry(
    @Serializable(with = UUIDSerializer::class) val key: UUID,
    val path: String,
)

@Immutable
data class RealizedPlaylistEntry(
    val key: UUID,
    val index: Int,
    val track: Track?,
    val playlistEntry: PlaylistEntry,
)

@Serializable
data class PlaylistIoSettings(
    val ignoreCase: Boolean = true,
    val ignoreLocation: Boolean = true,
    val removeInvalid: Boolean = true,
    val exportRelative: Boolean = false,
    val relativeBase: String = "",
)

/** TODO: is this exhaustive? */
private val absolutePathRegex = Regex("^(/|[^/]*:/).*")

fun parseM3u(
    name: String,
    m3u: ByteArray,
    libraryTrackPaths: Set<String>,
    settings: PlaylistIoSettings,
    charsetName: String?,
    lastModified: Long,
): Playlist {
    val lines =
        m3u.decodeWithCharsetName(charsetName)
            .lines()
            .mapNotNull { it.trimAndNormalize().let(FilenameUtils::separatorsToUnix) }
            .filter { it.isNotBlank() && !it.startsWith('#') }
            .map {
                if (absolutePathRegex.matches(it)) it
                else
                    FilenameUtils.concat(settings.relativeBase, it)
                        ?.let(FilenameUtils::separatorsToUnix) ?: it
            }
    val indexLookup =
        libraryTrackPaths
            .groupBy { if (settings.ignoreLocation) FilenameUtils.getName(it) else it }
            .let { map ->
                if (settings.ignoreCase)
                    CaseInsensitiveMap(map) { duplicates -> duplicates.flatMap { it } }
                else map
            }
    val paths =
        lines.mapNotNull { line ->
            val candidates =
                indexLookup[if (settings.ignoreLocation) FilenameUtils.getName(line) else line]
            val bestMatch =
                candidates?.maxByOrNull { line.commonSuffixWith(it, settings.ignoreCase).length }
            bestMatch ?: if (settings.removeInvalid) null else line
        }
    return Playlist(name, lastModified = lastModified).addPaths(paths)
}

fun RealizedPlaylist.toM3u(settings: PlaylistIoSettings): String {
    val exportRelativeBase =
        if (settings.exportRelative) {
            try {
                Path(settings.relativeBase)
            } catch (_: Exception) {
                null
            }
        } else null
    return entries
        .filter { if (settings.removeInvalid) it.track != null else true }
        .joinToString("\n") {
            if (exportRelativeBase != null) {
                try {
                    Path(it.playlistEntry.path)
                        .relativeToOrSelf(exportRelativeBase)
                        .invariantSeparatorsPathString
                } catch (_: Exception) {
                    it.playlistEntry.path
                }
            } else {
                it.playlistEntry.path
            }
        }
}

fun playlistShortcut(
    context: Context,
    namespace: String,
    key: UUID,
    name: String,
    rank: Int = 0,
): ShortcutInfoCompat {
    return ShortcutInfoCompat.Builder(context, "$namespace:$key")
        .setShortLabel(name)
        .setLongLabel(name)
        .setIcon(IconCompat.createWithResource(context, R.drawable.shortcut_playlist))
        .setRank(rank)
        .setIntent(
            Intent(SHORTCUT_PLAYLIST, null, context, MainActivity::class.java).apply {
                putExtras(bundleOf(SHORTCUT_PLAYLIST_EXTRA_KEY to key.toString()))
            }
        )
        .build()
}
