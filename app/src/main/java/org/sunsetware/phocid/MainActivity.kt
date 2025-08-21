package org.sunsetware.phocid

import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ibm.icu.util.ULocale
import java.lang.ref.WeakReference
import java.net.URLConnection
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.PlayerManager
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.RealizedPlaylist
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.getArtworkColor
import org.sunsetware.phocid.data.sorted
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.globals.SystemLocale
import org.sunsetware.phocid.ui.components.AnimatedForwardBackwardTransition
import org.sunsetware.phocid.ui.components.DragLock
import org.sunsetware.phocid.ui.theme.PhocidTheme
import org.sunsetware.phocid.ui.views.PermissionRequestDialog
import org.sunsetware.phocid.ui.views.library.LibraryScreen
import org.sunsetware.phocid.ui.views.library.LibraryScreenTabType
import org.sunsetware.phocid.ui.views.player.PlayerScreen
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.roundToIntOrZero
import org.sunsetware.phocid.utils.trimAndNormalize

class MainActivity : ComponentActivity(), IntentLauncher {
    private val launchIntent = AtomicReference<Intent>(null)

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val viewModel by viewModels<MainViewModel>()
        viewModel.initialize()
        intent?.let { launchIntent.set(it) }

        installSplashScreen().setKeepOnScreenCondition { !viewModel.initialized.value }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false

        // Set locale to the actual locale displayed, or the user might see funny formatting
        SystemLocale = requireNotNull(LocaleListCompat.getDefault()[0])
        val resourceLocaleTag = Strings[R.string.locale]
        val resourceLocale = Locale.forLanguageTag(resourceLocaleTag)
        val systemLocale = LocaleListCompat.getDefault().getFirstMatch(arrayOf(resourceLocaleTag))
        val locale =
            if (systemLocale?.language == resourceLocale.language) systemLocale else resourceLocale
        ULocale.setDefault(ULocale.forLocale(locale))

        super.onCreate(savedInstanceState)

        setContent {
            val initialized by viewModel.initialized.collectAsStateWithLifecycle()

            if (initialized) {
                val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
                val coroutineScope = rememberCoroutineScope()

                var permissionGranted by remember { mutableStateOf(false) }
                val permissions =
                    rememberMultiplePermissionsState(
                        listOfNotNull(READ_PERMISSION),
                        onPermissionsResult = { result ->
                            permissionGranted = result.all { it.value }
                        },
                    )
                permissionGranted = permissions.permissions.all { it.status.isGranted }

                val uiManager = viewModel.uiManager
                val topLevelScreenStack by
                    uiManager.topLevelScreenStack.collectAsStateWithLifecycle()
                val dialog by uiManager.dialog.collectAsStateWithLifecycle()
                val backHandlerEnabled by uiManager.backHandlerEnabled.collectAsStateWithLifecycle()
                // Don't put locks in ViewModel, as they should be reset after activity recreation.
                val playerScreenOpenDragLock = remember { DragLock() }
                val playerScreenCloseDragLock = remember { DragLock() }
                val playerScreenDragState = uiManager.playerScreenDragState

                val overrideStatusBarLightColor by
                    uiManager.overrideStatusBarLightColor.collectAsStateWithLifecycle()

                viewModel.uiManager.intentLauncher = WeakReference(this)

                val preferences by viewModel.preferences.collectAsStateWithLifecycle()

                val currentTrackColor by
                    remember {
                            viewModel.playerManager.state.combine(
                                coroutineScope,
                                viewModel.libraryIndex,
                                viewModel.preferences,
                            ) { state, library, preferences ->
                                if (state.actualPlayQueue.isEmpty()) null
                                else
                                    library.tracks[state.actualPlayQueue[state.currentIndex]]
                                        ?.getArtworkColor(preferences.artworkColorPreference)
                            }
                        }
                        .collectAsStateWithLifecycle()

                LaunchedEffect(coroutineScope) {
                    playerScreenDragState.coroutineScope = WeakReference(coroutineScope)
                    uiManager.playerScreenQueueDragState.coroutineScope =
                        WeakReference(coroutineScope)
                }

                LaunchedEffect(lifecycleState) {
                    if (lifecycleState == Lifecycle.State.RESUMED) {
                        val scanJob = viewModel.scanLibrary(false)
                        viewModel.viewModelScope.launch {
                            launchIntent.getAndSet(null)?.let {
                                handleIntent(
                                    viewModel.uiManager,
                                    viewModel.playerManager,
                                    { viewModel.libraryIndex.value },
                                    { viewModel.playlistManager.playlists.value },
                                    viewModel.preferences.value,
                                    scanJob,
                                    it,
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(permissionGranted) {
                    if (!permissionGranted) {
                        uiManager.openDialog(
                            PermissionRequestDialog(
                                permissions = permissions,
                                onPermissionGranted = { viewModel.scanLibrary(false) },
                            )
                        )
                    }
                }

                BackHandler(backHandlerEnabled) { uiManager.back() }

                PhocidTheme(
                    themeColorSource = preferences.themeColorSource,
                    customThemeColor = preferences.customThemeColor,
                    overrideThemeColor =
                        if (preferences.coloredGlobalTheme) currentTrackColor else null,
                    darkTheme = preferences.darkTheme.boolean ?: isSystemInDarkTheme(),
                    pureBackgroundColor = preferences.pureBackgroundColor,
                    overrideStatusBarLightColor = overrideStatusBarLightColor,
                    densityMultiplier = preferences.densityMultiplier,
                ) {
                    Box(
                        modifier =
                            Modifier.background(MaterialTheme.colorScheme.surface).onSizeChanged {
                                playerScreenDragState.length = it.height.toFloat()
                            }
                    ) {
                        AnimatedForwardBackwardTransition(topLevelScreenStack) { screen ->
                            when (screen) {
                                null -> {
                                    LibraryScreen(
                                        playerScreenOpenDragLock,
                                        isObscured =
                                            playerScreenDragState.position == 1f ||
                                                topLevelScreenStack.isNotEmpty(),
                                    )

                                    if (playerScreenDragState.position > 0) {
                                        val scrimColor = MaterialTheme.colorScheme.scrim
                                        Box(
                                            modifier =
                                                Modifier.fillMaxSize().drawBehind {
                                                    drawRect(
                                                        scrimColor,
                                                        alpha = playerScreenDragState.position,
                                                    )
                                                }
                                        )
                                        Box(
                                            modifier =
                                                Modifier.offset {
                                                    IntOffset(
                                                        0,
                                                        ((1 - playerScreenDragState.position) *
                                                                playerScreenDragState.length)
                                                            .roundToIntOrZero(),
                                                    )
                                                }
                                        ) {
                                            PlayerScreen(playerScreenCloseDragLock)
                                        }
                                    }
                                }
                                else -> screen.Compose(viewModel)
                            }
                        }

                        if (dialog != null) {
                            dialog!!.Compose(viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        launchIntent.set(intent)
        super.onNewIntent(intent)
    }

    private val openDocumentTreeContinuation = AtomicReference(null as ((Uri?) -> Unit)?)
    private val openDocumentTreeIntent =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            openDocumentTreeContinuation.get()?.invoke(uri)
        }
    private val createJsonDocumentContinuation = AtomicReference(null as ((Uri?) -> Unit)?)
    private val createJsonDocumentIntent =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            createJsonDocumentContinuation.get()?.invoke(uri)
        }
    private val openJsonDocumentContinuation = AtomicReference(null as ((Uri?) -> Unit)?)
    private val openJsonDocumentIntent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            openJsonDocumentContinuation.get()?.invoke(uri)
        }

    override fun openDocumentTree(continuation: (Uri?) -> Unit) {
        openDocumentTreeContinuation.set(continuation)
        openDocumentTreeIntent.launch(null)
    }

    override fun createJsonDocument(fileName: String, continuation: (Uri?) -> Unit) {
        createJsonDocumentContinuation.set(continuation)
        createJsonDocumentIntent.launch(fileName)
    }

    override fun openJsonDocument(continuation: (Uri?) -> Unit) {
        openJsonDocumentContinuation.set(continuation)
        openJsonDocumentIntent.launch(arrayOf("application/json"))
    }

    override fun share(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        val shareIntent =
            if (tracks.size == 1) {
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, tracks.first().uri)
                    // guessContentTypeFromName can fail if file name contains special characters
                    type =
                        URLConnection.guessContentTypeFromName(
                            "a." + FilenameUtils.getExtension(tracks.first().path)
                        )
                }
            } else {
                Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        ArrayList(tracks.map { it.uri }),
                    )
                    type =
                        tracks
                            .map {
                                URLConnection.guessContentTypeFromName(
                                    "a." + FilenameUtils.getExtension(it.path)
                                )
                            }
                            .distinct()
                            .singleOrNull() ?: "audio/*"
                }
            }
        startActivity(Intent.createChooser(shareIntent, null))
    }

    private suspend fun handleIntent(
        uiManager: UiManager,
        playerManager: PlayerManager,
        libraryIndex: () -> LibraryIndex,
        playlists: () -> Map<UUID, RealizedPlaylist>,
        preferences: Preferences,
        scanJob: Job,
        intent: Intent,
    ) {
        when (intent.action) {
            SHORTCUT_CONTINUE -> playerManager.play()
            SHORTCUT_SHUFFLE -> {
                scanJob.join()

                val tracksTab = preferences.tabSettings[LibraryScreenTabType.TRACKS]!!
                playerManager.enableShuffle()
                playerManager.state.takeWhile { !it.shuffle }.collect()
                playerManager.setTracks(
                    libraryIndex()
                        .tracks
                        .values
                        .sorted(
                            preferences.sortCollator,
                            tracksTab.sortingKeys,
                            tracksTab.sortAscending,
                        ),
                    null,
                )
            }
            SHORTCUT_PLAYLIST -> {
                scanJob.join()

                val playlist =
                    playlists()[
                        intent.extras?.getString(SHORTCUT_PLAYLIST_EXTRA_KEY)?.let {
                            try {
                                UUID.fromString(it)
                            } catch (_: Exception) {
                                null
                            }
                        }]
                if (playlist == null) {
                    uiManager.toast(Strings[R.string.toast_shortcut_playlist_not_found])
                } else {
                    playerManager.setTracks(playlist.entries.mapNotNull { it.track }, null)
                }
            }
            ACTION_VIEW -> {
                Log.d("Phocid", "View intent: ${intent.data}")

                if (intent.data == null) {
                    uiManager.toast(Strings[R.string.toast_view_intent_not_found])
                } else {
                    val (fileName, size) =
                        contentResolver
                            .query(
                                intent.data!!,
                                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                                null,
                                null,
                                null,
                            )
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    cursor.getString(0).trimAndNormalize() to cursor.getLong(1)
                                } else {
                                    null
                                }
                            } ?: (null to null)

                    if (fileName == null) {
                        uiManager.toast(Strings[R.string.toast_view_intent_not_found])
                    } else {
                        var track =
                            libraryIndex().tracks.values.firstOrNull {
                                it.fileName.equals(fileName, true) && it.size == size
                            }
                        if (track == null) {
                            // Retry after the scan completes
                            scanJob.join()
                            track =
                                libraryIndex().tracks.values.firstOrNull {
                                    it.fileName.equals(fileName, true) && it.size == size
                                }
                        }

                        if (track == null) {
                            uiManager.toast(Strings[R.string.toast_view_intent_not_found])
                        } else {
                            playerManager.setTracks(listOf(track), 0)
                        }
                    }
                }
            }
        }
    }
}
