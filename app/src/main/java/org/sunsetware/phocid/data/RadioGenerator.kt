package org.sunsetware.phocid.data

import org.sunsetware.phocid.utils.Random

object RadioGenerator {
    fun generate(
        seedTrack: Track,
        libraryIndex: LibraryIndex,
        historyEntries: HistoryList,
        mixRatio: Float,
        batchSize: Int,
    ): List<Track> {
        val playCounts = historyEntries.trackPlayCounts()
        val allTracks = libraryIndex.tracks.values.filter { it.id != seedTrack.id }
        if (allTracks.isEmpty()) return emptyList()

        val seedArtists = seedTrack.artists.map { it.lowercase() }.toSet()
        val seedGenres = seedTrack.genres.map { it.lowercase() }.toSet()

        val relatedTracks =
            allTracks.filter { track ->
                val trackArtists = track.artists.map { it.lowercase() }.toSet()
                val trackGenres = track.genres.map { it.lowercase() }.toSet()
                trackArtists.intersect(seedArtists).isNotEmpty() ||
                    trackGenres.intersect(seedGenres).isNotEmpty()
            }

        val contextualTopTracks =
            if (relatedTracks.isNotEmpty()) {
                relatedTracks
                    .filter { playCounts.containsKey(it.id) }
                    .sortedByDescending { playCounts[it.id] ?: 0 }
            } else {
                allTracks
                    .filter { playCounts.containsKey(it.id) }
                    .sortedByDescending { playCounts[it.id] ?: 0 }
            }

        val topPlayedCount = (batchSize * mixRatio).toInt().coerceAtMost(batchSize)
        val relatedCount = batchSize - topPlayedCount

        val result = mutableListOf<Track>()
        val usedIds = mutableSetOf(seedTrack.id)

        val shuffledRelated = relatedTracks.shuffled(Random)
        for (track in shuffledRelated) {
            if (result.size >= relatedCount) break
            if (usedIds.add(track.id)) {
                result.add(track)
            }
        }

        for (track in contextualTopTracks) {
            if (result.size >= relatedCount + topPlayedCount) break
            if (usedIds.add(track.id)) {
                result.add(track)
            }
        }

        if (result.size < batchSize) {
            val globalTop =
                allTracks
                    .filter { playCounts.containsKey(it.id) && !usedIds.contains(it.id) }
                    .sortedByDescending { playCounts[it.id] ?: 0 }
            for (track in globalTop) {
                if (result.size >= batchSize) break
                if (usedIds.add(track.id)) {
                    result.add(track)
                }
            }
        }

        if (result.size < batchSize) {
            val remaining = allTracks.filter { !usedIds.contains(it.id) }.shuffled(Random)
            for (track in remaining) {
                if (result.size >= batchSize) break
                if (usedIds.add(track.id)) {
                    result.add(track)
                }
            }
        }

        return result.shuffled(Random)
    }
}
