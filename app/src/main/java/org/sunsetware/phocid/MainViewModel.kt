package org.sunsetware.phocid

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import org.sunsetware.phocid.data.Lyrics
import org.sunsetware.phocid.data.PlayerManager
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.HistoryClearRange
import org.sunsetware.phocid.data.HistoryList
import org.sunsetware.phocid.data.cutoffMillis
import org.sunsetware.phocid.data.scanTracks
import org.sunsetware.phocid.globals.GlobalData
import org.sunsetware.phocid.ui.components.ArtworkCache
import org.sunsetware.phocid.ui.views.library.LibraryScreenTabInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private val _initialized = MutableStateFlow(false)
    val initialized = _initialized.asStateFlow()
    private val initializationStarted = AtomicBoolean(false)
    private val scanMutex = Mutex()

    lateinit var playerManager: PlayerManager
    lateinit var uiManager: UiManager

    val preferences
        get() = GlobalData.preferences.asStateFlow()

    val unfilteredTrackIndex
        get() = GlobalData.unfilteredTrackIndex.asStateFlow()

    val libraryIndex
        get() = GlobalData.libraryIndex

    val historyEntries
        get() = GlobalData.historyEntries.asStateFlow()

    val playlistManager
        get() = GlobalData.playlistManager

    val lyricsCache = AtomicReference(null as Pair<Long, Lyrics>?)
    val carouselArtworkCache = ArtworkCache(viewModelScope, 4)
    val playlistIoDirectory = MutableStateFlow(null as Uri?)
    private val _libraryScanState = MutableStateFlow(null as Boolean?)
    /**
     * - null: not scanning
     * - true: forced (manual)
     * - false: not forced (auto)
     */
    val libraryScanState = _libraryScanState.asStateFlow()
    private val _libraryScanProgress = MutableStateFlow(null as Pair<Int, Int>?)
    val libraryScanProgress = _libraryScanProgress.asStateFlow()

    private val _historyUndoEvent = MutableStateFlow(null as HistoryUndoEvent?)
    val historyUndoEvent = _historyUndoEvent.asStateFlow()

    fun initialize() {
        if (!initializationStarted.getAndSet(true)) {
            viewModelScope.launch {
                while (!GlobalData.initialized.get()) {
                    delay(50)
                }
                playerManager =
                    PlayerManager(GlobalData.playerState, GlobalData.playerTransientState)
                uiManager =
                    UiManager(
                        application.applicationContext,
                        viewModelScope,
                        preferences,
                        libraryIndex,
                        playlistManager,
                        GlobalData.historyEntries.asStateFlow(),
                    )
                playerManager.initialize(application.applicationContext)
                _initialized.update { true }
            }
        }
    }

    override fun onCleared() {
        playerManager.close()
        uiManager.close()
        super.onCleared()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun scanLibrary(force: Boolean): Job {
        return viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (scanMutex.tryLock()) {
                    Log.d("Phocid", "Library scan started")
                    try {
                        _libraryScanProgress.update { null }
                        _libraryScanState.update { force }

                        if (force || preferences.value.alwaysRescanMediaStore) {
                            // Try to obtain all external storage paths through hack.
                            // Result from getExternalStorageDirectory() is still kept in case the
                            // hack no longer works.
                            val storages =
                                application.applicationContext
                                    .getExternalFilesDirs(null)
                                    .mapNotNull {
                                        it?.parentFile?.parentFile?.parentFile?.parentFile?.path
                                    }
                                    .plus(Environment.getExternalStorageDirectory().path)
                                    .distinct()
                                    .toTypedArray()
                            suspendCancellableCoroutine<Unit> { continuation ->
                                val resumed = AtomicBoolean(false)
                                MediaScannerConnection.scanFile(
                                    application.applicationContext,
                                    storages,
                                    arrayOf("audio/*"),
                                ) { _, _ ->
                                    if (resumed.compareAndSet(false, true) && continuation.isActive)
                                        continuation.resume(Unit)
                                }
                            }
                        }

                        val newTrackIndex =
                            scanTracks(
                                application.applicationContext,
                                preferences.value.advancedMetadataExtraction,
                                preferences.value.disableArtworkColorExtraction,
                                if (force) null else unfilteredTrackIndex.value,
                                preferences.value.artistMetadataSeparators,
                                preferences.value.artistMetadataSeparatorExceptions,
                                preferences.value.genreMetadataSeparators,
                                preferences.value.genreMetadataSeparatorExceptions,
                            ) { current, total ->
                                _libraryScanProgress.update { current to total }
                            }
                        if (newTrackIndex != null) {
                            GlobalData.unfilteredTrackIndex.update { newTrackIndex }
                            GlobalData.libraryIndex.first {
                                it.flowVersion >= newTrackIndex.flowVersion
                            }
                            Log.d("Phocid", "Library scan completed")
                        } else {
                            Log.d("Phocid", "Library scan aborted: permission denied")
                        }
                        playlistManager.syncPlaylists()
                    } finally {
                        scanMutex.unlock()
                        _libraryScanState.update { null }
                    }
                } else {
                    scanMutex.withLock {}
                }
            }
        }
    }

    fun updatePreferences(transform: (Preferences) -> Preferences) {
        GlobalData.preferences.update(transform)
    }

    fun updateTabInfo(index: Int, transform: (LibraryScreenTabInfo) -> LibraryScreenTabInfo) {
        GlobalData.preferences.update { preferences ->
            val type = preferences.tabs[index].type
            preferences.copy(
                tabSettings =
                    preferences.tabSettings.mapValues {
                        if (it.key == type) transform(it.value) else it.value
                    }
            )
        }
    }

    fun clearHistory(range: HistoryClearRange) {
        val now = System.currentTimeMillis()
        val cutoff = range.cutoffMillis(now)
        val previous = GlobalData.historyEntries.value
        val remaining =
            if (cutoff == null) {
                emptyList()
            } else {
                previous.filter { it.timestamp < cutoff }
            }
        val removedCount = previous.size - remaining.size
        if (removedCount > 0) {
            _historyUndoEvent.update { HistoryUndoEvent(previous, removedCount) }
            GlobalData.historyEntries.update { remaining }
        }
    }

    fun undoClearHistory() {
        val event = _historyUndoEvent.value ?: return
        GlobalData.historyEntries.update { event.previousEntries }
        _historyUndoEvent.update { null }
    }

    fun consumeHistoryUndoEvent() {
        _historyUndoEvent.update { null }
    }
}

data class HistoryUndoEvent(
    val previousEntries: HistoryList,
    val removedCount: Int,
)
