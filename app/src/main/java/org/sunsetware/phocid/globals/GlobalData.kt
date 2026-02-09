package org.sunsetware.phocid.globals

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.PlayHistory
import org.sunsetware.phocid.data.PlayerState
import org.sunsetware.phocid.data.PlayerTransientState
import org.sunsetware.phocid.data.PlaylistManager
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.UnfilteredTrackIndex

/**
 * These are meant for sharing data between contexts. End consumers should not read these directly!
 *
 * Initialized and saved by [org.sunsetware.phocid.MainApplication].
 */
object GlobalData {
    val initialized = AtomicBoolean(false)

    @Volatile lateinit var preferences: MutableStateFlow<Preferences>
    @Volatile lateinit var unfilteredTrackIndex: MutableStateFlow<UnfilteredTrackIndex>
    @Volatile lateinit var playerState: MutableStateFlow<PlayerState>

    val playerTransientState = MutableStateFlow(PlayerTransientState())

    @Volatile lateinit var libraryIndex: StateFlow<LibraryIndex>

    @Volatile lateinit var playHistory: MutableStateFlow<PlayHistory>

    val radioSeedTrackId = MutableStateFlow(null as Long?)

    @Volatile lateinit var playlistManager: PlaylistManager
}
