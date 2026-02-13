package org.sunsetware.phocid

import android.app.Application
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.glance.appwidget.updateAll
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.HistoryList
import org.sunsetware.phocid.data.PlayerState
import org.sunsetware.phocid.data.PlaylistManager
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.SaveManager
import org.sunsetware.phocid.data.UnfilteredTrackIndex
import org.sunsetware.phocid.data.loadCbor
import org.sunsetware.phocid.globals.GlobalData
import org.sunsetware.phocid.globals.StringSource
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.map

class MainApplication : Application() {
    private data class WidgetPlayerStateKey(
        val queue: List<Long>,
        val currentIndex: Int,
        val shuffle: Boolean,
        val repeat: Int,
    )

    private val mainScope = MainScope()
    private val defaultScope = CoroutineScope(mainScope.coroutineContext + Dispatchers.Default)
    private val ioScope = CoroutineScope(mainScope.coroutineContext + Dispatchers.IO)
    private val saveManagers = mutableListOf<SaveManager<*>>()

    override fun onCreate() {
        super.onCreate()

        Strings =
            object : StringSource {
                override fun get(id: Int): String {
                    return getString(id)
                }
            }
        Thread.setDefaultUncaughtExceptionHandler(::onUncaughtException)

        with(GlobalData) {
            val context = this@MainApplication
            ioScope.launch {
                preferences =
                    MutableStateFlow(
                        loadCbor<Preferences>(context, PREFERENCES_FILE_NAME, false)?.upgrade()
                            ?: Preferences()
                    )
                unfilteredTrackIndex =
                    MutableStateFlow(
                        loadCbor<UnfilteredTrackIndex>(context, TRACK_INDEX_FILE_NAME, false)
                            ?.upgrade() ?: UnfilteredTrackIndex(null, emptyMap())
                    )
                playerState =
                    MutableStateFlow(
                        loadCbor<PlayerState>(context, PLAYER_STATE_FILE_NAME, isCache = false)
                            ?: PlayerState()
                    )
                historyEntries =
                    MutableStateFlow(
                        loadCbor<HistoryList>(context, HISTORY_FILE_NAME, isCache = false)
                            ?: emptyList()
                    )

                // LibraryIndex() is expensive, so extracting only the relevant
                // preferences first would avoid unnecessary computation
                libraryIndex =
                    unfilteredTrackIndex.combine(
                        defaultScope,
                        preferences.map(defaultScope) {
                            object {
                                val collator = it.sortCollator
                                val blacklist = it.blacklistRegexes
                                val whitelist = it.whitelistRegexes
                            }
                        },
                    ) { trackIndex, tuple ->
                        LibraryIndex(trackIndex, tuple.collator, tuple.blacklist, tuple.whitelist)
                    }

                playlistManager = PlaylistManager(context, defaultScope, preferences, libraryIndex)
                playlistManager.initialize()

                saveManagers +=
                    SaveManager(context, ioScope, preferences, PREFERENCES_FILE_NAME, false)
                saveManagers +=
                    SaveManager(
                        context,
                        ioScope,
                        unfilteredTrackIndex,
                        TRACK_INDEX_FILE_NAME,
                        false,
                    )
                saveManagers +=
                    SaveManager(context, ioScope, playerState, PLAYER_STATE_FILE_NAME, false)
                saveManagers +=
                    SaveManager(context, ioScope, historyEntries, HISTORY_FILE_NAME, false)

                defaultScope.launch {
                    playerState
                        .map {
                            WidgetPlayerStateKey(
                                it.actualPlayQueue,
                                it.currentIndex,
                                it.shuffle,
                                it.repeat,
                            )
                        }
                        .distinctUntilChanged()
                        .onEach { MainAppWidget().updateAll(context) }
                        .collect()
                }

                initialized.set(true)
            }
        }
    }

    private fun onUncaughtException(@Suppress("unused") thread: Thread, ex: Throwable) {
        Log.e("Phocid", "Uncaught exception", ex)
        val file = File(getExternalFilesDir(null), "crash.txt")

        file.bufferedWriter().use { writer ->
            writer.write(BuildConfig.VERSION_NAME)
            writer.write("\n\n")
            writer.write("API level ${Build.VERSION.SDK_INT}")
            writer.write("\n\n")
            writer.write(ex.stackTraceToString())
            writer.write("\n\n")

            try {
                Runtime.getRuntime().exec("logcat -d").inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine()
                        if (line == null) break
                        writer.write(line)
                        writer.write("\n")
                    }
                }
            } catch (ex: Exception) {
                writer.write("An exception occurred reading logcat:\n")
                writer.write(ex.stackTraceToString())
            }
        }

        Toast.makeText(
                this,
                Strings[R.string.toast_crash_saved_to].icuFormat(file.path),
                Toast.LENGTH_LONG,
            )
            .show()

        exitProcess(1)
    }
}
