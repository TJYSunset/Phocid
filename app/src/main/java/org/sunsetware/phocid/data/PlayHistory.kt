package org.sunsetware.phocid.data

import kotlinx.serialization.Serializable

@Serializable
data class PlayHistoryEntry(
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
)

typealias PlayHistory = Map<Long, PlayHistoryEntry>
