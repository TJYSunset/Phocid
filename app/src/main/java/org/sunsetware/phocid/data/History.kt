package org.sunsetware.phocid.data

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.R
import org.sunsetware.phocid.utils.UUIDSerializer

const val HISTORY_MAX_ENTRIES = 250

typealias HistoryList = List<HistoryEntry>

@Serializable
sealed class HistoryEntry {
    abstract val timestamp: Long
}

@Serializable
@SerialName("track")
data class TrackHistoryEntry(
    val trackId: Long,
    override val timestamp: Long,
) : HistoryEntry()

@Serializable
@SerialName("album")
data class AlbumHistoryEntry(
    val albumKey: String,
    override val timestamp: Long,
) : HistoryEntry()

@Serializable
@SerialName("playlist")
data class PlaylistHistoryEntry(
    @Serializable(with = UUIDSerializer::class) val playlistKey: UUID,
    override val timestamp: Long,
) : HistoryEntry()

sealed class HistoryStartContext {
    data class Album(val albumKey: AlbumKey) : HistoryStartContext()
    data class Playlist(val playlistKey: UUID) : HistoryStartContext()
}

enum class HistoryClearRange(val stringId: Int, val durationMs: Long?) {
    LAST_24_HOURS(R.string.history_clear_last_24_hours, 24L * 60L * 60L * 1000L),
    LAST_7_DAYS(R.string.history_clear_last_7_days, 7L * 24L * 60L * 60L * 1000L),
    ALL(R.string.history_clear_all, null),
}

fun HistoryClearRange.cutoffMillis(now: Long): Long? {
    return durationMs?.let { now - it }
}

fun HistoryList.appendEntry(entry: HistoryEntry, maxEntries: Int = HISTORY_MAX_ENTRIES): HistoryList {
    val updated = this + entry
    return if (updated.size <= maxEntries) updated else updated.takeLast(maxEntries)
}

fun HistoryList.trackPlayCounts(): Map<Long, Int> {
    val counts = mutableMapOf<Long, Int>()
    for (entry in this) {
        when (entry) {
            is TrackHistoryEntry ->
                counts[entry.trackId] = (counts[entry.trackId] ?: 0) + 1
            else -> {}
        }
    }
    return counts
}
