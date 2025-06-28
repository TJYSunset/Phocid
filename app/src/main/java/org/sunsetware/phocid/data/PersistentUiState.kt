package org.sunsetware.phocid.data

import kotlinx.serialization.Serializable

@Serializable
data class PersistentUiState(
    val libraryScreenHomeViewPage: Int = 0,
    val playerScreenUseLyricsView: Boolean = false,
    val playerTimerSettings: PlayerTimerSettings = PlayerTimerSettings(),
    val playlistIoSyncHelpShown: Boolean = false,
)
