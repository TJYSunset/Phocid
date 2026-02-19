@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MimeTypes
import com.ibm.icu.text.Collator
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.TopLevelScreen
import org.sunsetware.phocid.UNKNOWN
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.RealizedPlaylist
import org.sunsetware.phocid.data.parseM3u
import org.sunsetware.phocid.data.sortedBy
import org.sunsetware.phocid.data.toM3u
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.EmptyListIndicator
import org.sunsetware.phocid.ui.components.LifecycleLaunchedEffect
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.components.UtilityCheckBoxListItem
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.utils.SafFile
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.listSafFiles
import org.sunsetware.phocid.utils.trimAndNormalize

@Stable
class PlaylistIoScreen
private constructor(tabType: PlaylistIoScreenTabType, initialExportSelection: Set<UUID>) :
    TopLevelScreen() {
    private var currentTabType by mutableStateOf(tabType)

    private val importLazyListState = LazyListState()
    private var importSelection by mutableStateOf(emptySet<Uri>())
    private val exportLazyListState = LazyListState()
    private var exportSelection by mutableStateOf(initialExportSelection)
    private val syncLazyListState = LazyListState()

    private var m3uFiles by mutableStateOf(emptyList<SafFile>())

    companion object {
        fun import(): PlaylistIoScreen {
            return PlaylistIoScreen(PlaylistIoScreenTabType.Import, emptySet())
        }

        fun export(initialExportSelection: Set<UUID>): PlaylistIoScreen {
            return PlaylistIoScreen(PlaylistIoScreenTabType.Export, initialExportSelection)
        }

        fun sync(): PlaylistIoScreen {
            return PlaylistIoScreen(PlaylistIoScreenTabType.Sync, emptySet())
        }
    }

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val playlistManager = viewModel.playlistManager
        val playlists by playlistManager.playlists.collectAsStateWithLifecycle()
        val uiManager = viewModel.uiManager
        val playlistIoDirectoryUri by viewModel.playlistIoDirectory.collectAsStateWithLifecycle()
        val playlistIoDirectory =
            remember(playlistIoDirectoryUri) {
                playlistIoDirectoryUri?.let { DocumentFile.fromTreeUri(context, it) }
            }

        LifecycleLaunchedEffect(playlistIoDirectory) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    m3uFiles =
                        playlistIoDirectoryUri
                            ?.let {
                                listSafFiles(context, it, true) {
                                    it.name.endsWith(".m3u", true) ||
                                        it.name.endsWith(".m3u8", true)
                                }
                            }
                            ?.values
                            ?.toList() ?: emptyList()
                    delay(5.seconds)
                }
            }
        }

        // Show sync help dialog on first use
        LaunchedEffect(currentTabType) {
            if (currentTabType == PlaylistIoScreenTabType.Sync && uiManager.markPlaylistIoSyncHelpShown()) {
                uiManager.openDialog(PlaylistIoSyncHelpDialog())
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (currentTabType != PlaylistIoScreenTabType.Sync)
                            Text(Strings[R.string.playlist_import_export])
                        else Text(Strings[R.string.playlist_io_sync])
                    },
                    navigationIcon = {
                        IconButton(onClick = { uiManager.back() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Strings[R.string.commons_back],
                            )
                        }
                    },
                    actions = {
                        if (currentTabType == PlaylistIoScreenTabType.Sync) {
                            IconButton(
                                onClick = { uiManager.openDialog(PlaylistIoSyncHelpDialog()) }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Help,
                                    Strings[R.string.playlist_io_sync_help],
                                )
                            }
                            IconButton(
                                onClick = { uiManager.openDialog(PlaylistIoSyncLogDialog()) }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.EventNote,
                                    Strings[R.string.playlist_io_sync_log],
                                )
                            }
                            IconButton(
                                onClick = { uiManager.openDialog(PlaylistIoSyncSettingsDialog()) }
                            ) {
                                Icon(
                                    Icons.Filled.SettingsSuggest,
                                    Strings[R.string.playlist_io_sync_settings],
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    for (tabType in PlaylistIoScreenTabType.entries) {
                        NavigationBarItem(
                            selected = currentTabType == tabType,
                            onClick = { currentTabType = tabType },
                            icon = { Icon(tabType.icon, Strings[tabType.stringId]) },
                            label = { Text(Strings[tabType.stringId]) },
                        )
                    }
                }
            },
        ) { scaffoldPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column {
                    when (currentTabType) {
                        PlaylistIoScreenTabType.Import ->
                            ImportTab(
                                playlistIoDirectoryName = playlistIoDirectory?.name,
                                enabled =
                                    playlistIoDirectory != null && importSelection.isNotEmpty(),
                                onLaunchOpenDocumentTreeIntent = {
                                    viewModel.uiManager.intentLauncher.get()?.openDocumentTree { uri
                                        ->
                                        if (uri != null)
                                            viewModel.playlistIoDirectory.update { uri }
                                    }
                                },
                                onImport = { import(context, viewModel) },
                                onOpenSettings = {
                                    uiManager.openDialog(PlaylistIoSettingsDialog())
                                },
                            )
                        PlaylistIoScreenTabType.Export ->
                            ExportTab(
                                playlistIoDirectoryName = playlistIoDirectory?.name,
                                playlists = playlists,
                                collator = preferences.sortCollator,
                                enabled =
                                    playlistIoDirectory != null && exportSelection.isNotEmpty(),
                                onLaunchOpenDocumentTreeIntent = {
                                    viewModel.uiManager.intentLauncher.get()?.openDocumentTree { uri
                                        ->
                                        if (uri != null)
                                            viewModel.playlistIoDirectory.update { uri }
                                    }
                                },
                                onExport = {
                                    playlistIoDirectory?.let {
                                        export(context, viewModel, playlists, it)
                                    }
                                },
                                onOpenSettings = {
                                    uiManager.openDialog(PlaylistIoSettingsDialog())
                                },
                            )
                        PlaylistIoScreenTabType.Sync ->
                            SyncTab(
                                preferences = preferences,
                                playlists = playlists,
                                collator = preferences.sortCollator,
                                onLaunchOpenDocumentTreeIntent = {
                                    viewModel.uiManager.intentLauncher.get()?.openDocumentTree { uri
                                        ->
                                        if (uri == null) return@openDocumentTree

                                        try {
                                            context.contentResolver.releasePersistableUriPermission(
                                                Uri.parse(preferences.playlistIoSyncLocation),
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                            )
                                        } catch (ex: Exception) {
                                            Log.e(
                                                "Phocid",
                                                "Error releasing persistable uri permission",
                                                ex,
                                            )
                                        }

                                        try {
                                            context.contentResolver.takePersistableUriPermission(
                                                uri,
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                            )
                                        } catch (ex: Exception) {
                                            Log.e(
                                                "Phocid",
                                                "Error taking persistable uri permission",
                                                ex,
                                            )
                                        }
                                        viewModel.updatePreferences {
                                            it.copy(playlistIoSyncLocation = uri.toString())
                                        }
                                    }
                                },
                                showClear = preferences.playlistIoSyncLocation != null,
                                onSetPreferences = { preferences ->
                                    viewModel.updatePreferences { preferences }
                                },
                                onClear = {
                                    try {
                                        context.contentResolver.releasePersistableUriPermission(
                                            Uri.parse(preferences.playlistIoSyncLocation),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    } catch (ex: Exception) {
                                        Log.e(
                                            "Phocid",
                                            "Error releasing persistable uri permission",
                                            ex,
                                        )
                                    }
                                    viewModel.updatePreferences {
                                        it.copy(playlistIoSyncLocation = null)
                                    }
                                },
                                onSync = { playlistManager.syncPlaylists() },
                            )
                    }
                }
            }
        }
    }

    private fun import(context: Context, viewModel: MainViewModel) {
        val files = m3uFiles.filter { importSelection.contains(it.uri) }
        files.forEach { file ->
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                viewModel.playlistManager.addPlaylist(
                    parseM3u(
                        FilenameUtils.getBaseName(file.name),
                        inputStream.readBytes(),
                        viewModel.libraryIndex.value.tracks.values.map { it.path }.toSet(),
                        viewModel.preferences.value.playlistIoSettings,
                        if (FilenameUtils.getExtension(file.name).equals("m3u8", true))
                            Charsets.UTF_8.name()
                        else viewModel.preferences.value.charsetName,
                        file.lastModified ?: System.currentTimeMillis(),
                    )
                )
            }
        }

        viewModel.uiManager.toast(Strings[R.string.toast_playlist_imported].icuFormat(files.size))
        importSelection = emptySet()
    }

    private fun export(
        context: Context,
        viewModel: MainViewModel,
        playlists: Map<UUID, RealizedPlaylist>,
        playlistIoDirectory: DocumentFile,
    ) {
        val playlists = playlists.filter { exportSelection.contains(it.key) }

        var failureCount = 0
        playlists.forEach { key, playlist ->
            val file =
                playlistIoDirectory.createFile(MimeTypes.APPLICATION_M3U8, playlist.displayName)
            if (file != null) {
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
                    outputStream.write(
                        playlist
                            .toM3u(viewModel.preferences.value.playlistIoSettings)
                            .toByteArray(Charsets.UTF_8)
                    )
                }
                // SAF doesn't support setting file's last modified date, so we'll have to
                // set the playlist's date instead to keep both the same
                viewModel.playlistManager.updatePlaylist(key, file.lastModified()) { it }
            } else {
                failureCount++
            }
        }
        if (failureCount == 0) {
            viewModel.uiManager.toast(
                Strings[R.string.toast_playlist_exported].icuFormat(playlists.size)
            )
        } else {
            viewModel.uiManager.toast(
                Strings[R.string.toast_playlist_exported_with_failure].icuFormat(
                    playlists.size - failureCount,
                    failureCount,
                )
            )
        }
        exportSelection = emptySet()
    }

    @Composable
    private fun ImportTab(
        playlistIoDirectoryName: String?,
        enabled: Boolean,
        onLaunchOpenDocumentTreeIntent: () -> Unit,
        onImport: () -> Unit,
        onOpenSettings: () -> Unit,
    ) {
        Column {
            DirectoryPicker(
                formatString = Strings[R.string.playlist_io_location],
                notSetString = Strings[R.string.playlist_io_location_not_set],
                directoryName = playlistIoDirectoryName,
                showClear = false,
                onLaunchOpenDocumentTreeIntent,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (m3uFiles.isEmpty()) {
                    EmptyListIndicator()
                } else {
                    LazyColumn(state = importLazyListState, modifier = Modifier.fillMaxSize()) {
                        items(m3uFiles, { it.uri }) { file ->
                            UtilityCheckBoxListItem(
                                text = file.relativePath,
                                checked = importSelection.contains(file.uri),
                                onCheckedChange = {
                                    if (it) {
                                        importSelection += file.uri
                                    } else {
                                        importSelection -= file.uri
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onImport, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(Strings[R.string.playlist_io_import_count].icuFormat(importSelection.size))
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = Strings[R.string.preferences_playlist_io_settings],
                    )
                }
            }
        }
    }

    @Composable
    private fun ExportTab(
        playlistIoDirectoryName: String?,
        playlists: Map<UUID, RealizedPlaylist>,
        collator: Collator,
        enabled: Boolean,
        onLaunchOpenDocumentTreeIntent: () -> Unit,
        onExport: () -> Unit,
        onOpenSettings: () -> Unit,
    ) {
        Column {
            DirectoryPicker(
                formatString = Strings[R.string.playlist_io_location],
                notSetString = Strings[R.string.playlist_io_location_not_set],
                directoryName = playlistIoDirectoryName,
                showClear = false,
                onLaunchOpenDocumentTreeIntent,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (playlists.isEmpty()) {
                    EmptyListIndicator()
                } else {
                    LazyColumn(state = exportLazyListState, modifier = Modifier.fillMaxSize()) {
                        items(
                            playlists.toList().sortedBy(
                                collator,
                                RealizedPlaylist.CollectionSortingOptions.values.first().keys,
                                true,
                            ) {
                                it.second
                            },
                            { (key, _) -> key },
                        ) { (key, playlist) ->
                            UtilityCheckBoxListItem(
                                text = playlist.displayName,
                                checked = exportSelection.contains(key),
                                onCheckedChange = {
                                    if (it) {
                                        exportSelection += key
                                    } else {
                                        exportSelection -= key
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onExport, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(Strings[R.string.playlist_io_export_count].icuFormat(exportSelection.size))
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = Strings[R.string.preferences_playlist_io_settings],
                    )
                }
            }
        }
    }

    @Composable
    private inline fun SyncTab(
        preferences: Preferences,
        playlists: Map<UUID, RealizedPlaylist>,
        collator: Collator,
        showClear: Boolean,
        noinline onLaunchOpenDocumentTreeIntent: () -> Unit,
        noinline onClear: () -> Unit,
        crossinline onSetPreferences: (Preferences) -> Unit,
        noinline onSync: () -> Unit,
    ) {
        val context = LocalContext.current
        val directoryName =
            remember(preferences.playlistIoSyncLocation) {
                preferences.playlistIoSyncLocation?.let {
                    try {
                        DocumentFile.fromTreeUri(context, Uri.parse(it))!!.name
                    } catch (ex: Exception) {
                        Log.e("Phocid", "Error getting directory name of $it", ex)
                        null
                    }
                }
            }
        val items =
            playlists.toList().sortedBy(
                collator,
                RealizedPlaylist.CollectionSortingOptions.values.first().keys,
                true,
            ) {
                it.second
            } + preferences.playlistIoSyncMappings.keys.subtract(playlists.keys).map { it to null }
        var files by remember { mutableStateOf(null as Map<String, SafFile>?) }

        LifecycleLaunchedEffect(
            preferences.playlistIoSyncLocation,
        ) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    files =
                        preferences.playlistIoSyncLocation?.let {
                            listSafFiles(context, Uri.parse(it), false) {
                                it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true)
                            }
                        } ?: emptyMap()
                    delay(5.seconds)
                }
            }
        }

        Column {
            DirectoryPicker(
                formatString = Strings[R.string.playlist_io_sync_location],
                notSetString = Strings[R.string.playlist_io_sync_location_not_set],
                directoryName,
                showClear,
                onLaunchOpenDocumentTreeIntent,
                onClear,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (items.isEmpty()) {
                    EmptyListIndicator()
                } else {
                    LazyColumn(state = syncLazyListState, modifier = Modifier.fillMaxSize()) {
                        items(items, { (key, _) -> key }) { (key, playlist) ->
                            val target = preferences.playlistIoSyncMappings[key]
                            var targetBuffer by remember { mutableStateOf(target ?: "") }
                            val targetNonexistent =
                                target != null && files?.containsKey(target) == false
                            val hasConflict =
                                preferences.playlistIoSyncMappings.values.count { it == target } > 1

                            LaunchedEffect(targetBuffer) {
                                onSetPreferences(
                                    preferences.copy(
                                        playlistIoSyncMappings =
                                            preferences.playlistIoSyncMappings.mapValues {
                                                if (it.key == key) targetBuffer else it.value
                                            }
                                    )
                                )
                            }

                            Column {
                                UtilityCheckBoxListItem(
                                    text = playlist?.displayName ?: UNKNOWN,
                                    checked = target != null,
                                    onCheckedChange = {
                                        onSetPreferences(
                                            preferences.copy(
                                                playlistIoSyncMappings =
                                                    if (target == null) {
                                                        targetBuffer =
                                                            (playlist?.displayName ?: "") + ".m3u"
                                                        preferences.playlistIoSyncMappings +
                                                            Pair(key, targetBuffer)
                                                    } else {
                                                        preferences.playlistIoSyncMappings - key
                                                    }
                                            )
                                        )
                                    },
                                )
                                AnimatedVisibility(target != null) {
                                    TextField(
                                        modifier =
                                            Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                                        value = targetBuffer,
                                        onValueChange = { targetBuffer = it.trimAndNormalize() },
                                        label = {
                                            Text(Strings[R.string.playlist_io_sync_target_file])
                                        },
                                        supportingText = {
                                            Column {
                                                if (hasConflict)
                                                    Text(
                                                        Strings[
                                                            R.string
                                                                .playlist_io_sync_target_file_conflict]
                                                    )
                                                if (targetNonexistent)
                                                    Text(
                                                        Strings[
                                                            R.string
                                                                .playlist_io_sync_target_file_nonexistent]
                                                    )
                                            }
                                        },
                                        isError = hasConflict || targetNonexistent,
                                        singleLine = true,
                                        keyboardOptions =
                                            KeyboardOptions(
                                                capitalization = KeyboardCapitalization.None,
                                                autoCorrectEnabled = false,
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Done,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSync,
                enabled = preferences.playlistIoSyncLocation != null,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            ) {
                Text(Strings[R.string.playlist_io_sync_now])
            }
        }
    }

    @Composable
    private fun DirectoryPicker(
        formatString: String,
        notSetString: String,
        directoryName: String?,
        showClear: Boolean,
        onLaunchOpenDocumentTreeIntent: () -> Unit,
        onClear: () -> Unit = {},
    ) {
        Surface(
            modifier = Modifier.padding(16.dp).height(40.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            onClick = onLaunchOpenDocumentTreeIntent,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = if (showClear) 0.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    Strings[R.string.playlist_io_select_folder],
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                SingleLineText(
                    directoryName?.let { formatString.icuFormat(it) } ?: notSetString,
                    style = Typography.labelLarge,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showClear) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Clear, Strings[R.string.commons_clear])
                    }
                }
            }
        }
    }
}

@Immutable
private enum class PlaylistIoScreenTabType(val stringId: Int, val icon: ImageVector) {
    Import(R.string.commons_import, Icons.Outlined.FileDownload),
    Export(R.string.commons_export, Icons.Outlined.FileUpload),
    Sync(R.string.commons_sync, Icons.Filled.Sync),
}
