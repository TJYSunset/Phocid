@file:OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)

package org.sunsetware.phocid.data

import android.app.ActivityManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.palette.graphics.Palette
import androidx.palette.graphics.Target
import com.ibm.icu.text.Collator
import com.ibm.icu.util.CaseInsensitiveString
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.apache.commons.io.FilenameUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.KeyNotFoundException
import org.jaudiotagger.tag.TagTextField
import org.sunsetware.omio.VORBIS_COMMENT_ALBUM
import org.sunsetware.omio.VORBIS_COMMENT_ARTIST
import org.sunsetware.omio.VORBIS_COMMENT_GENRE
import org.sunsetware.omio.VORBIS_COMMENT_TITLE
import org.sunsetware.omio.VORBIS_COMMENT_TRACKNUMBER
import org.sunsetware.omio.VORBIS_COMMENT_UNOFFICIAL_ALBUMARTIST
import org.sunsetware.omio.VORBIS_COMMENT_UNOFFICIAL_COMMENT
import org.sunsetware.omio.VORBIS_COMMENT_UNOFFICIAL_DISCNUMBER
import org.sunsetware.omio.VORBIS_COMMENT_UNOFFICIAL_LYRICS
import org.sunsetware.omio.VORBIS_COMMENT_UNOFFICIAL_YEAR
import org.sunsetware.omio.readOpusMetadata
import org.sunsetware.phocid.R
import org.sunsetware.phocid.READ_PERMISSION
import org.sunsetware.phocid.UNKNOWN
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.utils.CaseInsensitiveMap
import org.sunsetware.phocid.utils.ColorSerializer
import org.sunsetware.phocid.utils.distinctCaseInsensitive
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.mode
import org.sunsetware.phocid.utils.modeOrNull
import org.sunsetware.phocid.utils.trimAndNormalize

@Immutable
@Serializable
data class Track(
    val id: Long,
    val path: String,
    val fileName: String,
    val dateAdded: Long,
    val version: Long,
    val title: String?,
    val artists: List<String>,
    val album: String?,
    @Deprecated("") @EncodeDefault(EncodeDefault.Mode.NEVER) val albumArtist: String? = null,
    val albumArtists: List<String> = emptyList(),
    val genres: List<String>,
    val year: Int?,
    val originalYear: Int? = null,
    val trackNumber: Int?,
    val discNumber: Int?,
    val duration: Duration,
    val size: Long,
    val format: String,
    val sampleRate: Int,
    val bitRate: Long,
    val bitDepth: Int,
    val hasArtwork: Boolean,
    @Serializable(with = ColorSerializer::class) val vibrantColor: Color?,
    @Serializable(with = ColorSerializer::class) val mutedColor: Color?,
    val unsyncedLyrics: String?,
    val comment: String?,
) : Searchable, Sortable {
    @Suppress("DEPRECATION")
    fun upgrade(): Track {
        return if (albumArtist == null) this
        else copy(albumArtist = null, albumArtists = listOfNotNull(albumArtist))
    }

    val uri
        get() = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id)

    val displayTitle
        get() = title ?: FilenameUtils.getBaseName(path)

    val displayArtistOrNull
        get() = if (artists.any()) Strings.conjoin(artists) else null

    val displayArtist
        get() = displayArtistOrNull ?: UNKNOWN

    val displayAlbum
        get() = album ?: UNKNOWN

    val displayAlbumArtist
        get() = if (albumArtists.any()) Strings.conjoin(albumArtists) else UNKNOWN

    val displayGenre
        get() = if (genres.any()) Strings.conjoin(genres) else UNKNOWN

    val displayYear
        get() = year?.toString() ?: UNKNOWN

    val displayNumber
        get() =
            if (trackNumber != null) {
                if (discNumber != null) {
                    Strings[R.string.track_number_with_disc].icuFormat(discNumber, trackNumber)
                } else {
                    Strings[R.string.track_number_without_disc].icuFormat(trackNumber)
                }
            } else {
                if (discNumber != null) {
                    Strings[R.string.track_disc_without_number].icuFormat(discNumber)
                } else {
                    Strings[R.string.track_number_not_available]
                }
            }

    val displayArtistWithAlbum
        get() = Strings.separate(displayArtist, album)

    @Transient
    override val searchableStrings =
        listOfNotNull(displayTitle, displayArtist, album, displayAlbumArtist, fileName)

    override val sortTitle
        get() = displayTitle

    override val sortArtist
        get() = Strings.conjoin(artists)

    override val sortAlbum
        get() = album ?: ""

    override val sortAlbumArtist
        get() = displayAlbumArtist

    override val sortDiscNumber
        get() = discNumber ?: 0

    override val sortTrackNumber
        get() = trackNumber ?: 0

    override val sortTrackNumberDisplay: String?
        get() = displayNumber

    override val sortGenre
        get() = Strings.conjoin(genres)

    override val sortYear
        get() = year ?: 0

    override val sortOriginalYear
        get() = originalYear ?: year ?: 0

    override val sortIsFolder
        get() = false

    override val sortFilename
        get() = fileName

    override val sortDateAdded
        get() = dateAdded

    override val sortDateModified
        get() = version

    override val sortTrackCount
        get() = 1

    companion object {
        val SortingOptions =
            mapOf(
                "Title" to
                    SortingOption(
                        R.string.sorting_title,
                        listOf(
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.YEAR,
                            SortingKey.FILE_NAME,
                        ),
                    ),
                "Artist" to
                    SortingOption(
                        R.string.sorting_artist,
                        listOf(
                            SortingKey.ARTIST,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.YEAR,
                            SortingKey.FILE_NAME,
                        ),
                    ),
                "Album" to
                    SortingOption(
                        R.string.sorting_album,
                        listOf(
                            SortingKey.ALBUM,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.YEAR,
                            SortingKey.FILE_NAME,
                        ),
                    ),
                "Album artist" to
                    SortingOption(
                        R.string.sorting_album_artist,
                        listOf(
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.YEAR,
                            SortingKey.FILE_NAME,
                        ),
                    ),
                "Year" to
                    SortingOption(
                        R.string.sorting_year,
                        listOf(
                            SortingKey.YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.FILE_NAME,
                        ),
                    ),
                "Original year" to
                    SortingOption(
                        R.string.sorting_original_release_year,
                        listOf(
                            SortingKey.ORIGINAL_YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.FILE_NAME,
                        ),
                    ),
                "Date added" to
                    SortingOption(
                        R.string.sorting_date_added,
                        listOf(
                            SortingKey.DATE_ADDED,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.YEAR,
                            SortingKey.FILE_NAME,
                        ),
                    ),
                "Date modified" to
                    SortingOption(
                        R.string.sorting_date_modified,
                        listOf(
                            SortingKey.DATE_MODIFIED,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.YEAR,
                            SortingKey.FILE_NAME,
                        ),
                    ),
            )
    }
}

fun loadLyrics(track: Track, charsetName: String?): Lyrics? {
    try {
        val trackFileNameWithoutExtension = FilenameUtils.getBaseName(track.path)
        val trackFileName = FilenameUtils.getName(track.path)
        val files = File(FilenameUtils.getPath(track.path)).listFiles()
        return files
            ?.filter { it.extension.equals("lrc", true) }
            ?.firstOrNull {
                it.nameWithoutExtension.equals(trackFileNameWithoutExtension, true) ||
                    it.nameWithoutExtension.equals(trackFileName, true)
            }
            ?.readBytes()
            ?.let { parseLrc(it, charsetName) }
    } catch (ex: Exception) {
        Log.e("Phocid", "Can't load lyrics for ${track.path}", ex)
        return null
    }
}

val InvalidTrack =
    Track(
        -1,
        "",
        "",
        0,
        0,
        "<error>",
        listOf("<error>"),
        null,
        null,
        emptyList(),
        emptyList(),
        null,
        null,
        null,
        null,
        Duration.ZERO,
        0,
        "<error>",
        0,
        0,
        0,
        false,
        null,
        null,
        null,
        null,
    )

@Immutable
data class Album(
    val name: String,
    val albumArtists: List<String> = emptyList(),
    val year: Int? = null,
    val originalYear: Int?,
    val tracks: List<Track> = emptyList(),
) : Searchable, Sortable {
    val displayAlbumArtist
        get() =
            albumArtists.takeIf { it.isNotEmpty() }?.let(Strings::conjoin)
                ?: tracks
                    .flatMap { it.artists }
                    .modeOrNull()
                    ?.let { Strings[R.string.track_inferred_album_artist].icuFormat(it) }
                ?: UNKNOWN

    @Transient override val searchableStrings = listOf(name, displayAlbumArtist)

    override val sortAlbum
        get() = name

    override val sortAlbumArtist
        get() = displayAlbumArtist

    override val sortYear
        get() = year ?: 0

    override val sortOriginalYear
        get() = originalYear ?: year ?: 0

    override val sortDateAdded = tracks.maxOf { it.dateAdded }

    override val sortDateModified = tracks.maxOf { it.version }

    override val sortTrackCount
        get() = tracks.size

    companion object {
        val CollectionSortingOptions =
            mapOf(
                "Name" to
                    SortingOption(
                        R.string.sorting_name,
                        listOf(SortingKey.ALBUM, SortingKey.ALBUM_ARTIST, SortingKey.YEAR),
                    ),
                "Album artist" to
                    SortingOption(
                        R.string.sorting_album_artist,
                        listOf(SortingKey.ALBUM_ARTIST, SortingKey.ALBUM, SortingKey.YEAR),
                    ),
                "Year" to
                    SortingOption(
                        R.string.sorting_year,
                        listOf(SortingKey.YEAR, SortingKey.ALBUM_ARTIST, SortingKey.ALBUM),
                    ),
                "Original year" to
                    SortingOption(
                        R.string.sorting_original_release_year,
                        listOf(SortingKey.ORIGINAL_YEAR, SortingKey.ALBUM_ARTIST, SortingKey.ALBUM),
                    ),
                "Date added" to
                    SortingOption(
                        R.string.sorting_date_added,
                        listOf(
                            SortingKey.DATE_ADDED,
                            SortingKey.ALBUM,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.YEAR,
                        ),
                    ),
                "Date modified" to
                    SortingOption(
                        R.string.sorting_date_modified,
                        listOf(
                            SortingKey.DATE_MODIFIED,
                            SortingKey.ALBUM,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.YEAR,
                        ),
                    ),
                "Track count" to
                    SortingOption(
                        R.string.sorting_track_count,
                        listOf(
                            SortingKey.TRACK_COUNT,
                            SortingKey.ALBUM,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.YEAR,
                        ),
                    ),
            )

        val TrackSortingOptions =
            mapOf(
                "Number" to
                    SortingOption(
                        R.string.sorting_number,
                        listOf(SortingKey.TRACK, SortingKey.ARTIST, SortingKey.TITLE),
                    ),
                "Title" to
                    SortingOption(
                        R.string.sorting_title,
                        listOf(SortingKey.TITLE, SortingKey.ARTIST, SortingKey.TRACK),
                    ),
                "Artist" to
                    SortingOption(
                        R.string.sorting_artist,
                        listOf(SortingKey.ARTIST, SortingKey.TITLE, SortingKey.TRACK),
                    ),
            )
    }
}

@Immutable
data class AlbumKey(
    val name: CaseInsensitiveString,
    val albumArtists: List<CaseInsensitiveString>,
) {
    /**
     * Since the default [toString] doesn't escape strings, two different [AlbumKey]s could still
     * theoretically collide without overriding this.
     *
     * TODO: Find a less hacky hack
     */
    override fun toString(): String {
        return listOf(Base64.encode(name.string.toByteArray(Charsets.UTF_8)))
            .plus(
                albumArtists.map {
                    it.string?.let { Base64.encode(it.toByteArray(Charsets.UTF_8)) }
                }
            )
            .joinToString(" ")
    }

    constructor(
        name: String,
        albumArtists: List<String>,
    ) : this(CaseInsensitiveString(name), albumArtists.map { CaseInsensitiveString(it) })
}

fun AlbumKey(string: String): AlbumKey? {
    try {
        val segments = string.split(' ')
        return AlbumKey(
            Base64.decode(segments[0]).toString(Charsets.UTF_8),
            segments.drop(1).map { Base64.decode(it).toString(Charsets.UTF_8) },
        )
    } catch (ex: Exception) {
        Log.e("Phocid", "Attempted to decode an invalid AlbumKey $string", ex)
        return null
    }
}

val Track.albumKey: AlbumKey?
    get() = album?.let { AlbumKey(it, albumArtists) }
val Album.albumKey: AlbumKey
    get() = AlbumKey(name, albumArtists)

@Immutable
data class Artist(
    val name: String,
    val tracks: List<Track> = emptyList(),
    val albumSlices: List<AlbumSlice> = emptyList(),
) : Searchable, Sortable {
    @Stable
    val displayStatistics
        get() =
            Strings.separate(
                albumSlices.size
                    .takeIf { it != 0 }
                    ?.let { Strings[R.string.count_album].icuFormat(it) },
                Strings[R.string.count_track].icuFormat(tracks.size),
            )

    @Transient override val searchableStrings = listOf(name)

    override val sortArtist
        get() = name

    override val sortDateAdded = tracks.maxOf { it.dateAdded }

    override val sortDateModified = tracks.maxOf { it.version }

    override val sortTrackCount
        get() = tracks.size

    override val sortAlbumCount
        get() = albumSlices.size

    companion object {
        val CollectionSortingOptions =
            mapOf(
                "Name" to SortingOption(R.string.sorting_name, listOf(SortingKey.ARTIST)),
                "Date added" to
                    SortingOption(
                        R.string.sorting_date_added,
                        listOf(SortingKey.DATE_ADDED, SortingKey.ARTIST),
                    ),
                "Date modified" to
                    SortingOption(
                        R.string.sorting_date_modified,
                        listOf(SortingKey.DATE_MODIFIED, SortingKey.ARTIST),
                    ),
                "Track count" to
                    SortingOption(
                        R.string.sorting_track_count,
                        listOf(SortingKey.TRACK_COUNT, SortingKey.ARTIST),
                    ),
                "Album count" to
                    SortingOption(
                        R.string.sorting_album_count,
                        listOf(SortingKey.ALBUM_COUNT, SortingKey.ARTIST),
                    ),
            )
        val TrackSortingOptions =
            mapOf(
                "Album" to
                    SortingOption(
                        R.string.sorting_album,
                        listOf(
                            SortingKey.ALBUM,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.YEAR,
                        ),
                    ),
                "Title" to
                    SortingOption(
                        R.string.sorting_title,
                        listOf(
                            SortingKey.TITLE,
                            SortingKey.YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                        ),
                    ),
                "Year" to
                    SortingOption(
                        R.string.sorting_year,
                        listOf(
                            SortingKey.YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                        ),
                    ),
                "Original year" to
                    SortingOption(
                        R.string.sorting_original_release_year,
                        listOf(
                            SortingKey.ORIGINAL_YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                        ),
                    ),
            )
    }
}

@Immutable
data class AlbumArtist(
    val name: String,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
) : Searchable, Sortable {
    @Stable
    val displayStatistics
        get() =
            Strings.separate(
                Strings[R.string.count_album].icuFormat(albums.size),
                Strings[R.string.count_track].icuFormat(tracks.size),
            )

    @Transient override val searchableStrings = listOf(name)

    override val sortAlbumArtist
        get() = name

    override val sortDateAdded = tracks.maxOf { it.dateAdded }

    override val sortDateModified = tracks.maxOf { it.version }

    override val sortTrackCount
        get() = tracks.size

    override val sortAlbumCount
        get() = albums.size

    companion object {
        val CollectionSortingOptions =
            mapOf(
                "Name" to SortingOption(R.string.sorting_name, listOf(SortingKey.ALBUM_ARTIST)),
                "Date added" to
                    SortingOption(
                        R.string.sorting_date_added,
                        listOf(SortingKey.DATE_ADDED, SortingKey.ALBUM_ARTIST),
                    ),
                "Date modified" to
                    SortingOption(
                        R.string.sorting_date_modified,
                        listOf(SortingKey.DATE_MODIFIED, SortingKey.ALBUM_ARTIST),
                    ),
                "Track count" to
                    SortingOption(
                        R.string.sorting_track_count,
                        listOf(SortingKey.TRACK_COUNT, SortingKey.ALBUM_ARTIST),
                    ),
                "Album count" to
                    SortingOption(
                        R.string.sorting_album_count,
                        listOf(SortingKey.ALBUM_COUNT, SortingKey.ALBUM_ARTIST),
                    ),
            )
        val TrackSortingOptions =
            mapOf(
                "Album" to
                    SortingOption(
                        R.string.sorting_album,
                        listOf(
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.YEAR,
                        ),
                    ),
                "Title" to
                    SortingOption(
                        R.string.sorting_title,
                        listOf(
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.YEAR,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                        ),
                    ),
                "Artist" to
                    SortingOption(
                        R.string.sorting_artist,
                        listOf(
                            SortingKey.ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.YEAR,
                        ),
                    ),
                "Year" to
                    SortingOption(
                        R.string.sorting_year,
                        listOf(
                            SortingKey.YEAR,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                        ),
                    ),
                "Original year" to
                    SortingOption(
                        R.string.sorting_original_release_year,
                        listOf(
                            SortingKey.ORIGINAL_YEAR,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                        ),
                    ),
            )
    }
}

@Immutable
data class Genre(
    val name: String,
    val tracks: List<Track> = emptyList(),
    val artistSlices: List<ArtistSlice> = emptyList(),
) : Searchable, Sortable {
    @Stable
    val displayStatistics
        get() = Strings[R.string.count_track].icuFormat(tracks.size)

    @Transient override val searchableStrings = listOf(name)

    override val sortGenre
        get() = name

    override val sortDateAdded = tracks.maxOf { it.dateAdded }

    override val sortDateModified = tracks.maxOf { it.version }

    override val sortTrackCount
        get() = tracks.size

    companion object {
        val CollectionSortingOptions =
            mapOf(
                "Name" to SortingOption(R.string.sorting_name, listOf(SortingKey.GENRE)),
                "Date added" to
                    SortingOption(
                        R.string.sorting_date_added,
                        listOf(SortingKey.DATE_ADDED, SortingKey.GENRE),
                    ),
                "Date modified" to
                    SortingOption(
                        R.string.sorting_date_modified,
                        listOf(SortingKey.DATE_MODIFIED, SortingKey.GENRE),
                    ),
                "Track count" to
                    SortingOption(
                        R.string.sorting_track_count,
                        listOf(SortingKey.TRACK_COUNT, SortingKey.GENRE),
                    ),
            )
        val TrackSortingOptions =
            mapOf(
                "Title" to
                    SortingOption(
                        R.string.sorting_title,
                        listOf(
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.YEAR,
                        ),
                    ),
                "Artist" to
                    SortingOption(
                        R.string.sorting_artist,
                        listOf(
                            SortingKey.ARTIST,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.YEAR,
                        ),
                    ),
                "Album" to
                    SortingOption(
                        R.string.sorting_album,
                        listOf(
                            SortingKey.ALBUM,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                            SortingKey.YEAR,
                        ),
                    ),
                "Year" to
                    SortingOption(
                        R.string.sorting_year,
                        listOf(
                            SortingKey.YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                        ),
                    ),
                "Original year" to
                    SortingOption(
                        R.string.sorting_original_release_year,
                        listOf(
                            SortingKey.ORIGINAL_YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.ARTIST,
                        ),
                    ),
            )
    }
}

@Immutable
data class Folder(
    val path: String,
    val fileName: String,
    val childFolders: List<String>,
    val childTracks: List<Track>,
    val childTracksCountRecursive: Int,
    val dateAdded: Long,
    val dateModified: Long,
) : Searchable, Sortable {
    val displayStatistics
        get() =
            Strings.separate(
                childFolders.size
                    .takeIf { it != 0 }
                    ?.let { Strings[R.string.count_folder].icuFormat(it) },
                Strings[R.string.count_track].icuFormat(childTracksCountRecursive),
            )

    fun childTracksRecursive(folderIndex: Map<String, Folder>): List<Track> {
        val stack = mutableListOf(this)
        val tracks = mutableListOf<Track>()
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(0)
            stack.addAll(0, current.childFolders.mapNotNull { folderIndex[it] })
            tracks.addAll(current.childTracks)
        }
        return tracks
    }

    fun childItemsRecursive(folderIndex: Map<String, Folder>): Pair<List<Folder>, List<Track>> {
        val stack = mutableListOf(this)
        val folders = mutableListOf<Folder>()
        val tracks = mutableListOf<Track>()
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(0)
            stack.addAll(0, current.childFolders.mapNotNull { folderIndex[it] })
            folders.add(current)
            tracks.addAll(current.childTracks)
        }
        folders.removeAt(0)
        return folders to tracks
    }

    override val searchableStrings: List<String>
        get() = listOf(fileName)

    // Dummy sortable properties are required for compatibility with tracks.

    override val sortTitle
        get() = ""

    override val sortArtist
        get() = ""

    override val sortAlbum
        get() = ""

    override val sortAlbumArtist
        get() = ""

    override val sortDiscNumber
        get() = 0

    override val sortTrackNumber
        get() = 0

    override val sortTrackNumberDisplay: String?
        get() = Strings[R.string.track_number_not_available]

    override val sortGenre
        get() = ""

    override val sortYear
        get() = 0

    override val sortOriginalYear
        get() = 0

    override val sortIsFolder
        get() = true

    override val sortFilename
        get() = fileName

    override val sortDateAdded
        get() = dateAdded

    override val sortDateModified
        get() = dateModified

    override val sortTrackCount
        get() = childTracksCountRecursive

    companion object {
        val SortingOptions =
            mapOf(
                "File name" to
                    SortingOption(R.string.sorting_file_name, listOf(SortingKey.FILE_NAME)),
                "Track count" to
                    SortingOption(
                        R.string.sorting_track_count,
                        listOf(SortingKey.TRACK_COUNT, SortingKey.FILE_NAME),
                    ),
            ) + Track.SortingOptions
    }
}

private data class MutableFolder(
    val path: String,
    val childFolders: MutableSet<String> = mutableSetOf(),
    val childTracks: MutableList<Track> = mutableListOf(),
    var childTracksCountRecursive: Int = 0,
    var dateAdded: Long = 0,
    var dateModified: Long = 0,
) {
    fun toFolder(collator: Collator): Folder {
        return Folder(
            path,
            FilenameUtils.getName(path),
            childFolders
                .map {
                    it to Folder(it, FilenameUtils.getName(it), emptyList(), emptyList(), 0, 0, 0)
                }
                .sortedBy(collator, Folder.SortingOptions.values.first().keys, true) { it.second }
                .map { it.first },
            childTracks.sorted(collator, Folder.SortingOptions.values.first().keys, true),
            childTracksCountRecursive,
            dateAdded,
            dateModified,
        )
    }
}

@Immutable
data class AlbumSlice(val album: Album, val tracks: List<Track> = emptyList()) {
    companion object {
        val TrackSortingOptions =
            mapOf(
                "Number" to
                    SortingOption(
                        R.string.sorting_number,
                        listOf(SortingKey.TRACK, SortingKey.TITLE),
                    ),
                "Title" to
                    SortingOption(
                        R.string.sorting_title,
                        listOf(SortingKey.TITLE, SortingKey.TRACK),
                    ),
            )
    }
}

@Immutable
data class ArtistSlice(val artist: Artist, val tracks: List<Track> = emptyList()) {
    companion object {
        val TrackSortingOptions =
            mapOf(
                "Title" to
                    SortingOption(
                        R.string.sorting_title,
                        listOf(
                            SortingKey.TITLE,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.YEAR,
                        ),
                    ),
                "Album" to
                    SortingOption(
                        R.string.sorting_album,
                        listOf(
                            SortingKey.ALBUM,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                            SortingKey.YEAR,
                        ),
                    ),
                "Year" to
                    SortingOption(
                        R.string.sorting_year,
                        listOf(
                            SortingKey.YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                        ),
                    ),
                "Original year" to
                    SortingOption(
                        R.string.sorting_original_release_year,
                        listOf(
                            SortingKey.ORIGINAL_YEAR,
                            SortingKey.ALBUM_ARTIST,
                            SortingKey.ALBUM,
                            SortingKey.TRACK,
                            SortingKey.TITLE,
                        ),
                    ),
            )
    }
}

private val flowVersionCounter = AtomicLong(0)

@Immutable
@Serializable
data class UnfilteredTrackIndex(
    val version: String?,
    val tracks: Map<Long, Track>,
    /**
     * Automatically assigned monotonically increasing value, used for syncing updates between the
     * flow of [UnfilteredTrackIndex] and [LibraryIndex].
     */
    @Transient val flowVersion: Long = flowVersionCounter.getAndIncrement(),
) {
    fun upgrade(): UnfilteredTrackIndex {
        return copy(tracks = tracks.mapValues { it.value.upgrade() })
    }

    fun getFolders(collator: Collator): Pair<Map<String, Folder>, String> {
        val folders = getFolders(tracks.values, collator)
        return folders to getRootFolder(folders)
    }
}

@Immutable
data class LibraryIndex(
    val version: String?,
    val tracks: Map<Long, Track>,
    val albums: Map<AlbumKey, Album>,
    val artists: CaseInsensitiveMap<Artist>,
    val albumArtists: CaseInsensitiveMap<AlbumArtist>,
    val genres: CaseInsensitiveMap<Genre>,
    val folders: Map<String, Folder>,
    val defaultRootFolder: String,
    /**
     * Automatically assigned monotonically increasing value, used for syncing updates between the
     * flow of [UnfilteredTrackIndex] and [LibraryIndex].
     */
    val flowVersion: Long,
)

fun LibraryIndex(
    unfilteredTrackIndex: UnfilteredTrackIndex,
    collator: Collator,
    blacklist: List<Regex>,
    whitelist: List<Regex>,
): LibraryIndex {
    val tracks =
        unfilteredTrackIndex.tracks.filter { (_, track) ->
            blacklist.none { it.containsMatchIn(track.path) } ||
                whitelist.any { it.containsMatchIn(track.path) }
        }
    val albums = getAlbums(tracks.values, collator)
    val artists = getArtists(tracks.values, albums, collator)
    val albumArtists = getAlbumArtists(albums, collator)
    val genres = getGenres(tracks.values, artists, collator)
    val folders = getFolders(tracks.values, collator)
    val rootFolder = getRootFolder(folders)
    return LibraryIndex(
        unfilteredTrackIndex.version,
        tracks,
        albums,
        artists,
        albumArtists,
        genres,
        folders,
        rootFolder,
        unfilteredTrackIndex.flowVersion,
    )
}

private fun getAlbums(tracks: Collection<Track>, collator: Collator): Map<AlbumKey, Album> {
    return tracks
        .groupBy { if (it.album != null) AlbumKey(it.album, it.albumArtists) else null }
        .filter { it.key != null }
        .map { (_, tracks) ->
            val sortedTracks =
                tracks.sorted(collator, Album.TrackSortingOptions.values.first().keys, true)
            val name = sortedTracks.mode { it.album!! }
            val albumArtist = sortedTracks.mode { it.albumArtists }
            AlbumKey(name, albumArtist) to
                Album(
                    name,
                    albumArtist,
                    sortedTracks.mode { it.year },
                    sortedTracks.mode { it.originalYear },
                    sortedTracks,
                )
        }
        .toMap()
}

private fun getArtists(
    tracks: Collection<Track>,
    albums: Map<AlbumKey, Album>,
    collator: Collator,
): CaseInsensitiveMap<Artist> {
    return tracks
        .flatMap { it.artists }
        .distinctCaseInsensitive()
        .associateWith { name ->
            val artistTracks =
                tracks
                    .filter { track -> track.artists.any { it.equals(name, true) } }
                    .sorted(collator, Artist.TrackSortingOptions.values.first().keys, true)
            val albumSlices =
                artistTracks
                    .groupBy { if (it.album != null) AlbumKey(it.album, it.albumArtists) else null }
                    .filter { it.key != null }
                    .map {
                        AlbumSlice(
                            albums[it.key]!!,
                            it.value.sorted(
                                collator,
                                Album.TrackSortingOptions.values.first().keys,
                                true,
                            ),
                        )
                    }
                    .sortedBy(collator, Album.CollectionSortingOptions.values.first().keys, true) {
                        it.album
                    }
            Artist(name, artistTracks, albumSlices)
        }
        .let { CaseInsensitiveMap.noMerge(it) }
}

private fun getAlbumArtists(
    albums: Map<AlbumKey, Album>,
    collator: Collator,
): CaseInsensitiveMap<AlbumArtist> {
    return albums.values
        .flatMap { it.albumArtists }
        .distinctCaseInsensitive()
        .associateWith { name ->
            val albumArtistAlbums =
                albums.values
                    .filter { it.albumArtists.any { it.equals(name, true) } }
                    .sorted(collator, Album.CollectionSortingOptions.values.first().keys, true)
            val albumArtistTracks = albumArtistAlbums.flatMap { it.tracks }
            AlbumArtist(name, albumArtistTracks, albumArtistAlbums)
        }
        .let { CaseInsensitiveMap.noMerge(it) }
}

private fun getGenres(
    tracks: Collection<Track>,
    artists: CaseInsensitiveMap<Artist>,
    collator: Collator,
): CaseInsensitiveMap<Genre> {
    return tracks
        .flatMap { it.genres }
        .distinctCaseInsensitive()
        .associateWith { name ->
            val genreTracks =
                tracks
                    .filter { track -> track.genres.any { it.equals(name, true) } }
                    .sorted(collator, Genre.TrackSortingOptions.values.first().keys, true)
            val artistSlices =
                artists.values
                    .map { artist ->
                        ArtistSlice(
                            artist,
                            artist.tracks
                                .filter { track -> track.genres.any { it.equals(name, true) } }
                                .sorted(
                                    collator,
                                    Artist.TrackSortingOptions.values.first().keys,
                                    true,
                                ),
                        )
                    }
                    .filter { it.tracks.isNotEmpty() }
                    .sortedBy(collator, Artist.CollectionSortingOptions.values.first().keys, true) {
                        it.artist
                    }
            Genre(name, genreTracks, artistSlices)
        }
        .let { CaseInsensitiveMap.noMerge(it) }
}

private fun getFolders(tracks: Collection<Track>, collator: Collator): Map<String, Folder> {
    val folders = mutableMapOf<String, MutableFolder>("" to MutableFolder(""))
    tracks.sorted(collator, Folder.SortingOptions.values.first().keys, true).forEach { track ->
        val parentPath = FilenameUtils.getPathNoEndSeparator(track.path)
        val parentFolder = folders.getOrPut(parentPath) { MutableFolder(parentPath) }
        parentFolder.childTracks.add(track)
        parentFolder.dateAdded = max(parentFolder.dateAdded, track.dateAdded)
        parentFolder.dateModified = max(parentFolder.dateModified, track.version)
    }
    for (path in folders.keys.toMutableList()) {
        var currentPath = path
        var parentPath = FilenameUtils.getPathNoEndSeparator(path)
        while (currentPath.isNotEmpty()) {
            val parentFolderExists = folders.containsKey(parentPath)
            val parentFolder = folders.getOrPut(parentPath) { MutableFolder(parentPath) }
            parentFolder.childFolders.add(currentPath)
            if (parentFolderExists) break
            currentPath = parentPath
            parentPath = FilenameUtils.getPathNoEndSeparator(parentPath)
        }
    }
    for ((path, folder) in folders) {
        folder.childTracksCountRecursive += folder.childTracks.size
        var currentPath = path
        while (currentPath.isNotEmpty()) {
            val parentPath = FilenameUtils.getPathNoEndSeparator(currentPath)
            val parentFolder = folders[parentPath]!!
            parentFolder.childTracksCountRecursive += folder.childTracks.size
            parentFolder.dateAdded = max(parentFolder.dateAdded, folder.dateAdded)
            parentFolder.dateModified = max(parentFolder.dateModified, folder.dateModified)
            currentPath = parentPath
        }
    }
    return folders.mapValues { it.value.toFolder(collator) }
}

private fun getRootFolder(folders: Map<String, Folder>): String {
    var root = ""
    while (true) {
        val folder = folders[root]!!
        if (folder.childFolders.size != 1 || folder.childTracks.isNotEmpty()) break
        root = folder.childFolders[0]
    }
    return root
}

private val contentResolverColumns =
    arrayOf(
        Media._ID,
        Media.DATA,
        Media.DATE_ADDED,
        Media.DATE_MODIFIED,
        Media.TITLE,
        Media.ARTIST,
        Media.ALBUM,
        Media.ALBUM_ARTIST,
        Media.GENRE,
        Media.YEAR,
        Media.TRACK,
        Media.DISC_NUMBER,
        Media.DURATION,
        Media.SIZE,
        Media.BITRATE,
    )

suspend fun scanTracks(
    context: Context,
    advancedMetadataExtraction: Boolean,
    disableArtworkColorExtraction: Boolean,
    old: UnfilteredTrackIndex?,
    artistSeparators: List<String>,
    artistSeparatorExceptions: List<String>,
    genreSeparators: List<String>,
    genreSeparatorExceptions: List<String>,
    onProgressReport: (Int, Int) -> Unit,
): UnfilteredTrackIndex? {
    if (
        ContextCompat.checkSelfPermission(context, READ_PERMISSION) ==
            PackageManager.PERMISSION_DENIED
    )
        return null
    val libraryVersion = MediaStore.getVersion(context)

    val query =
        context.contentResolver.query(
            Media.EXTERNAL_CONTENT_URI,
            contentResolverColumns,
            "${Media.IS_MUSIC} AND NOT ${Media.IS_DRM} AND NOT ${Media.IS_TRASHED}",
            null,
            "${Media._ID} ASC",
        )
    val tracks = ConcurrentLinkedQueue<Track>()
    val crudeTracks = ConcurrentLinkedQueue<Track>()
    var maxSize = 0L

    query?.use { cursor ->
        val ci = contentResolverColumns.associateWith { cursor.getColumnIndexOrThrow(it) }
        while (cursor.moveToNext()) {
            val id = cursor.getLong(ci[Media._ID]!!)
            val trackVersion = cursor.getLong(ci[Media.DATE_MODIFIED]!!)
            val path =
                cursor
                    .getString(ci[Media.DATA]!!)
                    .trimAndNormalize()
                    .let { FilenameUtils.normalize(it) }
                    .let { FilenameUtils.separatorsToUnix(it) }
            val oldIndex = old?.tracks?.get(id)

            if (oldIndex?.version == trackVersion && oldIndex.path == path) {
                tracks += oldIndex
            } else {
                val size = cursor.getLong(ci[Media.SIZE]!!)
                maxSize = max(maxSize, size)
                crudeTracks +=
                    Track(
                        id = id,
                        version = trackVersion,
                        path = path,
                        fileName = FilenameUtils.getName(path),
                        dateAdded = cursor.getLong(ci[Media.DATE_ADDED]!!),
                        title = cursor.getStringOrNull(ci[Media.TITLE]!!)?.trimAndNormalize(),
                        artists =
                            listOfNotNull(
                                cursor.getStringOrNull(ci[Media.ARTIST]!!)?.trimAndNormalize()
                            ),
                        album = cursor.getStringOrNull(ci[Media.ALBUM]!!)?.trimAndNormalize(),
                        albumArtists =
                            listOfNotNull(
                                cursor.getStringOrNull(ci[Media.ALBUM_ARTIST]!!)?.trimAndNormalize()
                            ),
                        genres =
                            listOfNotNull(
                                cursor.getStringOrNull(ci[Media.GENRE]!!)?.trimAndNormalize()
                            ),
                        year = cursor.getIntOrNull(ci[Media.YEAR]!!),
                        originalYear = null,
                        // https://developer.android.com/reference/android/provider/MediaStore.Audio.AudioColumns.html#TRACK
                        trackNumber = cursor.getIntOrNull(ci[Media.TRACK]!!)?.let { it % 1000 },
                        discNumber = cursor.getIntOrNull(ci[Media.DISC_NUMBER]!!),
                        duration = cursor.getInt(ci[Media.DURATION]!!).milliseconds,
                        size = size,
                        format = UNKNOWN,
                        sampleRate = 0,
                        bitRate = cursor.getLongOrNull(ci[Media.BITRATE]!!) ?: 0,
                        bitDepth = 0,
                        unsyncedLyrics = null as String?,
                        comment = null as String?,
                        hasArtwork = false,
                        vibrantColor = null,
                        mutedColor = null,
                    )
            }
        }
    }

    val activityManager = context.getSystemService<ActivityManager>(ActivityManager::class.java)
    val freeMemory =
        ActivityManager.MemoryInfo()
            .also { memoryInfo -> activityManager.getMemoryInfo(memoryInfo) }
            .let { it.availMem - it.threshold }
    val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val overheadFactor = 3.0
    // Cast to double to prevent division by zero
    val parallelism =
        if (maxSize == 0L) 1
        else
            floor(freeMemory.toDouble() / maxSize / overheadFactor)
                .toInt()
                .coerceIn(1, min(processorCount, 4))
    Log.d(
        "Phocid",
        "Scanning tracks with parallelism of $parallelism (max file size $maxSize, free memory $freeMemory, processor count $processorCount)",
    )
    val progressCurrent = AtomicInteger(0)
    val progressTotal = crudeTracks.size
    coroutineScope {
        val jobs =
            (0..<parallelism).map {
                launch {
                    while (true) {
                        val crudeTrack = crudeTracks.poll()
                        if (crudeTrack == null) break

                        progressCurrent.incrementAndGet()
                        try {
                            tracks +=
                                scanTrack(
                                    context,
                                    advancedMetadataExtraction,
                                    disableArtworkColorExtraction,
                                    artistSeparators,
                                    artistSeparatorExceptions,
                                    genreSeparators,
                                    genreSeparatorExceptions,
                                    crudeTrack,
                                )
                        } catch (ex: Exception) {
                            Log.e("Phocid", "Error scanning track ${crudeTrack.path}", ex)
                        }
                    }
                }
            }
        launch {
            while (jobs.all { it.isActive }) {
                onProgressReport(progressCurrent.get(), progressTotal)
                delay(100.milliseconds)
            }
        }
    }

    return UnfilteredTrackIndex(libraryVersion, tracks.associateBy { it.id })
}

private val originalYearFieldNames =
    listOf("originalyear", "originaldate", "original_year", "original_date", "origyear")
private val lyricsFieldNames = listOf("lyrics", "unsyncedlyrics", "©lyr")
private val yearRegexes =
    listOf(
        // YYYY-MM(-DD)
        Regex("^(?<year>[0-9]{4})-[0-9]{2}(-[0-9]{2})?$"),
        // YYYYMM(DD)
        Regex("^(?<year>[0-9]{4})[0-9]{2}([0-9]{2})?$"),
        // any integer
        Regex("^(?<year>[0-9]+)$"),
        // last resort
        Regex("^.*?(?<year>[0-9]+).*?$"),
    )

/**
 * Issue #84: some systems might report incorrect durations, but Jaudiotagger only has second-level
 * precision and might be unreliable while OpusMetadataIo will take 100x time to read the duration,
 * so we need to manually extract duration if and only if MediaStore isn't working.
 */
private fun scanTrack(
    context: Context,
    advancedMetadataExtraction: Boolean,
    disableArtworkColorExtraction: Boolean,
    artistSeparators: List<String>,
    artistSeparatorExceptions: List<String>,
    genreSeparators: List<String>,
    genreSeparatorExceptions: List<String>,
    crudeTrack: Track,
): Track {
    val id = crudeTrack.id
    val path = crudeTrack.path

    var title = crudeTrack.title
    var artists = crudeTrack.artists
    var album = crudeTrack.album
    var albumArtists = crudeTrack.albumArtists
    var genres = crudeTrack.genres
    var year = crudeTrack.year
    var originalYear = crudeTrack.originalYear
    var trackNumber = crudeTrack.trackNumber
    var discNumber = crudeTrack.discNumber
    var duration = crudeTrack.duration
    var format = crudeTrack.format
    var sampleRate = crudeTrack.sampleRate
    var bitRate = crudeTrack.bitRate
    var bitDepth = crudeTrack.bitDepth
    var unsyncedLyrics = crudeTrack.unsyncedLyrics
    var comment = crudeTrack.comment

    fun String.parseYear(): Int? {
        return yearRegexes.firstNotNullOfOrNull {
            it.matchEntire(this)?.groups["year"]?.value?.toIntOrNull()
        }
    }

    fun extractWithOmio() {
        val (_, comments, opusDuration) =
            FileInputStream(File(path)).buffered().use { stream ->
                readOpusMetadata(stream, readDuration = duration <= Duration.ZERO)
            }
        title = comments[VORBIS_COMMENT_TITLE]?.firstOrNull() ?: title
        artists = comments[VORBIS_COMMENT_ARTIST] ?: artists
        album = comments[VORBIS_COMMENT_ALBUM]?.firstOrNull() ?: album
        albumArtists = comments[VORBIS_COMMENT_UNOFFICIAL_ALBUMARTIST] ?: albumArtists
        genres = comments[VORBIS_COMMENT_GENRE] ?: genres
        year =
            VORBIS_COMMENT_UNOFFICIAL_YEAR.firstNotNullOfOrNull {
                comments[it]?.firstOrNull()?.parseYear()
            } ?: year
        originalYear =
            originalYearFieldNames.firstNotNullOfOrNull { name ->
                comments[name]?.map { it.parseYear() }?.firstOrNull()
            }
        trackNumber =
            comments[VORBIS_COMMENT_TRACKNUMBER]?.firstNotNullOfOrNull { it.toIntOrNull() }
                ?: trackNumber
        discNumber =
            comments[VORBIS_COMMENT_UNOFFICIAL_DISCNUMBER]?.firstNotNullOfOrNull {
                it.toIntOrNull()
            } ?: discNumber
        duration = opusDuration ?: duration
        unsyncedLyrics =
            VORBIS_COMMENT_UNOFFICIAL_LYRICS.firstNotNullOfOrNull { comments[it]?.firstOrNull() }
                ?: unsyncedLyrics
        comment =
            VORBIS_COMMENT_UNOFFICIAL_COMMENT.firstNotNullOfOrNull { comments[it]?.firstOrNull() }
                ?: unsyncedLyrics
        format = "Ogg Opus"
        sampleRate = 48000
    }

    fun extractWithJaudiotagger() {
        val file = AudioFileIO.read(File(path))
        try {
            title = file.tag.getFirst(FieldKey.TITLE)
        } catch (_: KeyNotFoundException) {}
        try {
            artists =
                file.tag
                    .getFields(FieldKey.ARTIST)
                    .filter { !it.isBinary }
                    .map { (it as TagTextField).content }
        } catch (_: KeyNotFoundException) {}
        try {
            album = file.tag.getFirst(FieldKey.ALBUM)
        } catch (_: KeyNotFoundException) {}
        try {
            albumArtists =
                file.tag
                    .getFields(FieldKey.ALBUM_ARTIST)
                    .filter { !it.isBinary }
                    .map { (it as TagTextField).content }
        } catch (_: KeyNotFoundException) {}
        try {
            genres =
                file.tag
                    .getFields(FieldKey.GENRE)
                    .filter { !it.isBinary }
                    .map { (it as TagTextField).content }
        } catch (_: KeyNotFoundException) {}
        year =
            (try {
                    file.tag.getFirst(FieldKey.YEAR)
                } catch (_: KeyNotFoundException) {
                    null
                }
                    ?: file.tag.fields
                        .asSequence()
                        .firstOrNull { it.id.equals("year", true) }
                        ?.let { it as? TagTextField }
                        ?.content)
                ?.parseYear() ?: year
        originalYear =
            originalYearFieldNames.firstNotNullOfOrNull { name ->
                try {
                    file.tag.fields
                        .asSequence()
                        .firstOrNull { it.id.equals(name, true) }
                        ?.let { it as? TagTextField }
                        ?.content
                        ?.parseYear()
                } catch (_: KeyNotFoundException) {
                    null
                }
            }
        try {
            trackNumber = file.tag.getFirst(FieldKey.TRACK).toIntOrNull()
        } catch (_: KeyNotFoundException) {}
        try {
            discNumber = file.tag.getFirst(FieldKey.DISC_NO).toIntOrNull()
        } catch (_: KeyNotFoundException) {}
        if (duration <= Duration.ZERO) {
            try {
                duration = file.audioHeader.trackLength.seconds
            } catch (_: KeyNotFoundException) {}
        }
        unsyncedLyrics =
            try {
                file.tag.getFirst(FieldKey.LYRICS).takeIf { it.isNotEmpty() }
            } catch (_: KeyNotFoundException) {
                null
            }
                ?: lyricsFieldNames.firstNotNullOfOrNull { name ->
                    try {
                        file.tag.fields
                            .asSequence()
                            .firstOrNull { it.id.equals(name, true) }
                            ?.let { it as? TagTextField }
                            ?.content
                            ?.takeIf { it.isNotEmpty() }
                    } catch (_: KeyNotFoundException) {
                        null
                    }
                }
        try {
            comment = file.tag.getFirst(FieldKey.COMMENT).takeIf { it.isNotEmpty() }
        } catch (_: KeyNotFoundException) {}
        format = file.audioHeader.format
        sampleRate = file.audioHeader.sampleRateAsNumber
        bitDepth = file.audioHeader.bitsPerSample
    }

    if (advancedMetadataExtraction) {
        try {
            val extension = FilenameUtils.getExtension(path).lowercase()
            if (extension == "opus" || extension == "ogg") {
                try {
                    extractWithOmio()
                } catch (_: Exception) {
                    extractWithJaudiotagger()
                }
            } else {
                extractWithJaudiotagger()
            }
        } catch (ex: Exception) {
            Log.e("Phocid", "Error reading extended metadata for $path", ex)
        }
    }

    // In some cases, missing fields will be masqueraded as empty strings
    title = title?.takeIf { it.isNotEmpty() }?.trimAndNormalize()
    artists = splitArtists(title, artists, artistSeparators, artistSeparatorExceptions)
    album = album?.takeIf { it.isNotEmpty() }?.trimAndNormalize()
    albumArtists = splitArtists(null, albumArtists, artistSeparators, artistSeparatorExceptions)
    genres = splitGenres(genres, genreSeparators, genreSeparatorExceptions)

    val palette =
        if (disableArtworkColorExtraction) null
        else
            loadArtwork(context, id, path, false, 64)
                ?.let { Palette.from(it) }
                ?.clearTargets()
                ?.apply {
                    addTarget(Target.VIBRANT)
                    addTarget(Target.MUTED)
                }
                ?.generate()
    val vibrantColor = palette?.getSwatchForTarget(Target.VIBRANT)?.rgb?.let { Color(it) }
    val mutedColor = palette?.getSwatchForTarget(Target.MUTED)?.rgb?.let { Color(it) }

    return crudeTrack.copy(
        title = title,
        artists = artists,
        album = album,
        albumArtists = albumArtists,
        genres = genres,
        year = year,
        originalYear = originalYear,
        trackNumber = trackNumber,
        discNumber = discNumber,
        duration = duration,
        format = format,
        sampleRate = sampleRate,
        bitRate = bitRate,
        bitDepth = bitDepth,
        unsyncedLyrics = unsyncedLyrics,
        comment = comment,
        hasArtwork = palette != null,
        vibrantColor = vibrantColor,
        mutedColor = mutedColor,
    )
}

private val featuringArtistInTitleRegex =
    Regex("""[( ](feat|ft)\. *(?<artist>.+?)(\(|\)|$)""", RegexOption.IGNORE_CASE)

/**
 * Apparently people invent all kinds of workarounds to represent multiple artists. And JAudioTagger
 * "intelligently" replaces some delimiters with null characters.
 *
 * Also there are mysterious trailing whitespaces.
 */
private fun splitArtists(
    title: String?,
    artists: Iterable<String>,
    separators: Collection<String>,
    exceptions: Iterable<String>,
): List<String> {
    val exceptionSurrogates =
        exceptions.take(6400).mapIndexed { index, exception ->
            exception to (0xe000 + index).toChar().toString()
        }
    fun String.replaceExceptions(): String {
        var replaced = this
        exceptionSurrogates.forEach { (exception, surrogate) ->
            replaced = replaced.replace(exception, surrogate, ignoreCase = true)
        }
        return replaced
    }
    fun String.restoreExceptions(): String {
        var restored = this
        exceptionSurrogates.forEach { (exception, surrogate) ->
            restored = restored.replace(surrogate, exception)
        }
        return restored
    }

    val featuringArtistInTitle =
        title?.let {
            featuringArtistInTitleRegex
                .find(it.replaceExceptions())
                ?.groups
                ?.get("artist")
                ?.value
                ?.restoreExceptions()
        }
    return (artists + listOfNotNull(featuringArtistInTitle))
        .flatMap { string ->
            string
                .replaceExceptions()
                .split(*(arrayOf("\u0000") + separators), ignoreCase = true)
                .map { it.restoreExceptions() }
        }
        .map { it.trimAndNormalize() }
        .filter { it.isNotEmpty() }
        .distinctCaseInsensitive()
}

private fun splitGenres(
    genres: Iterable<String>,
    separators: Collection<String>,
    exceptions: Iterable<String>,
): List<String> {
    val exceptionSurrogates =
        exceptions.take(6400).mapIndexed { index, exception ->
            exception to (0xe000 + index).toChar().toString()
        }
    fun String.replaceExceptions(): String {
        var replaced = this
        exceptionSurrogates.forEach { (exception, surrogate) ->
            replaced = replaced.replace(exception, surrogate, ignoreCase = true)
        }
        return replaced
    }
    fun String.restoreExceptions(): String {
        var restored = this
        exceptionSurrogates.forEach { (exception, surrogate) ->
            restored = restored.replace(surrogate, exception)
        }
        return restored
    }

    return genres
        .flatMap { string ->
            string
                .replaceExceptions()
                .split(*(arrayOf("\u0000") + separators), ignoreCase = true)
                .map { it.restoreExceptions() }
        }
        .map { it.trimAndNormalize() }
        .filter { it.isNotEmpty() }
        .distinctCaseInsensitive()
}
