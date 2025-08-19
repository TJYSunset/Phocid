package org.sunsetware.phocid.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ibm.icu.text.Collator
import com.ibm.icu.text.DateFormat
import com.ibm.icu.util.ULocale
import java.util.Date
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.R
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.utils.initialLetter
import org.sunsetware.phocid.utils.toLocalizedString

/**
 * All properties should return null if and only if this type doesn't support that property. Missing
 * values should be represented as "" or 0.
 */
interface Sortable {
    val sortTitle: String?
        get() = null

    val sortArtist: String?
        get() = null

    val sortAlbum: String?
        get() = null

    val sortAlbumArtist: String?
        get() = null

    val sortDiscNumber: Int?
        get() = null

    val sortTrackNumber: Int?
        get() = null

    val sortTrackNumberDisplay: String?
        get() = null

    val sortYear: Int?
        get() = null

    val sortOriginalYear: Int?
        get() = null

    val sortGenre: String?
        get() = null

    val sortPlaylist: Pair<SpecialPlaylist?, String>?
        get() = null

    val sortIsFolder: Boolean?
        get() = null

    val sortFilename: String?
        get() = null

    val sortDateAdded: Long?
        get() = null

    val sortDateModified: Long?
        get() = null

    val sortTrackCount: Int?
        get() = null

    val sortAlbumCount: Int?
        get() = null
}

@Immutable
@Serializable
enum class SortingKey {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    TRACK,
    YEAR,
    ORIGINAL_YEAR,
    GENRE,
    PLAYLIST,
    FILE_NAME,
    DATE_ADDED,
    DATE_MODIFIED,
    TRACK_COUNT,
    ALBUM_COUNT,
}

@Immutable data class SortingOption(val stringId: Int?, val keys: List<SortingKey>)

@Stable
inline fun <T> Iterable<T>.sortedBy(
    collator: Collator,
    sortingKeys: List<SortingKey>,
    ascending: Boolean,
    crossinline selector: (T) -> Sortable,
): List<T> {
    val comparator =
        Comparator<T> { genericA, genericB ->
            val a = selector(genericA)
            val b = selector(genericB)
            val isFolderResult =
                a.sortIsFolder?.compareTo(b.sortIsFolder!!)?.let { -it }?.takeIf { it != 0 }
            if (isFolderResult != null) return@Comparator isFolderResult
            sortingKeys.forEachIndexed { index, item ->
                val result =
                    when (item) {
                        SortingKey.TITLE -> collator.compare(a.sortTitle, b.sortTitle)
                        SortingKey.ARTIST -> collator.compare(a.sortArtist, b.sortArtist)
                        SortingKey.ALBUM -> collator.compare(a.sortAlbum, b.sortAlbum)
                        SortingKey.ALBUM_ARTIST ->
                            collator.compare(a.sortAlbumArtist, b.sortAlbumArtist)

                        SortingKey.TRACK ->
                            a.sortDiscNumber!!.compareTo(b.sortDiscNumber!!).takeIf { it != 0 }
                                ?: a.sortTrackNumber!!.compareTo(b.sortTrackNumber!!)

                        SortingKey.YEAR -> a.sortYear!!.compareTo(b.sortYear!!)
                        SortingKey.ORIGINAL_YEAR ->
                            a.sortOriginalYear!!.compareTo(b.sortOriginalYear!!)
                        SortingKey.GENRE -> collator.compare(a.sortGenre, b.sortGenre)
                        SortingKey.PLAYLIST ->
                            (a.sortPlaylist!!.first?.order ?: Int.MAX_VALUE)
                                .compareTo(b.sortPlaylist!!.first?.order ?: Int.MAX_VALUE)
                                .takeIf { it != 0 }
                                ?: collator.compare(
                                    a.sortPlaylist!!.second,
                                    b.sortPlaylist!!.second,
                                )

                        SortingKey.FILE_NAME -> collator.compare(a.sortFilename!!, b.sortFilename!!)

                        SortingKey.DATE_ADDED -> a.sortDateAdded!!.compareTo(b.sortDateAdded!!)
                        SortingKey.DATE_MODIFIED ->
                            a.sortDateModified!!.compareTo(b.sortDateModified!!)

                        SortingKey.TRACK_COUNT -> a.sortTrackCount!!.compareTo(b.sortTrackCount!!)

                        SortingKey.ALBUM_COUNT -> a.sortAlbumCount!!.compareTo(b.sortAlbumCount!!)
                    } * (if (index == 0 && !ascending) -1 else 1)
                if (result != 0) return@Comparator result
            }
            return@Comparator 0
        }

    return sortedWith(comparator)
}

@Stable
fun <T : Sortable> Iterable<T>.sorted(
    collator: Collator,
    sortingKeys: List<SortingKey>,
    ascending: Boolean,
): List<T> {
    return sortedBy(collator, sortingKeys, ascending) { it }
}

@Stable
inline fun <T> Iterable<T>.hintBy(
    collator: Collator,
    sortingKeys: List<SortingKey>,
    crossinline selector: (T) -> Sortable,
): List<Pair<T, String>> {
    val locale = collator.getLocale(ULocale.VALID_LOCALE).toLocale()
    return mapIndexed { index, item ->
        val sortable = selector(item)
        item to
            when (sortingKeys.firstOrNull()) {
                SortingKey.TITLE -> sortable.sortTitle!!.initialLetter(locale)
                SortingKey.ARTIST -> sortable.sortArtist!!.initialLetter(locale)
                SortingKey.ALBUM -> sortable.sortAlbum!!.initialLetter(locale)
                SortingKey.ALBUM_ARTIST -> sortable.sortAlbumArtist!!.initialLetter(locale)
                SortingKey.TRACK -> sortable.sortTrackNumberDisplay!!
                SortingKey.YEAR ->
                    sortable.sortYear!!.takeIf { it > 0 }?.toString()
                        ?: Strings[R.string.track_number_not_available]
                SortingKey.ORIGINAL_YEAR ->
                    sortable.sortOriginalYear!!.takeIf { it > 0 }?.toString()
                        ?: Strings[R.string.track_number_not_available]
                SortingKey.GENRE -> sortable.sortGenre!!.initialLetter(locale)
                SortingKey.PLAYLIST ->
                    sortable.sortPlaylist!!.let {
                        if (it.first != null) Strings[R.string.track_number_not_available]
                        else it.second.initialLetter(locale)
                    }
                SortingKey.FILE_NAME -> sortable.sortFilename!!.initialLetter(locale)
                SortingKey.DATE_ADDED ->
                    DateFormat.getInstance().format(Date(sortable.sortDateAdded!! * 1000))
                SortingKey.DATE_MODIFIED ->
                    DateFormat.getInstance().format(Date(sortable.sortDateModified!! * 1000))
                SortingKey.TRACK_COUNT -> sortable.sortTrackCount!!.toLocalizedString()
                SortingKey.ALBUM_COUNT -> sortable.sortAlbumCount!!.toLocalizedString()
                null -> (index + 1).toLocalizedString()
            }
    }
}

@Stable
fun <T : Sortable> Iterable<T>.hint(
    collator: Collator,
    sortingKeys: List<SortingKey>,
): List<Pair<T, String>> {
    return hintBy(collator, sortingKeys) { it }
}
