package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.EmptyListIndicator
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.components.UtilityListItem
import org.sunsetware.phocid.ui.theme.Typography

@Stable
class PreferencesFolderPickerDialog(
    private val unfiltered: Boolean,
    private val initialPath: String?,
    private val onConfirmOrDismiss: (String?) -> Unit,
) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val (folders, defaultRootFolder) =
            remember {
                if (unfiltered)
                    viewModel.unfilteredTrackIndex.value.getFolders(
                        viewModel.preferences.value.sortCollator
                    )
                else viewModel.libraryIndex.value.let { it.folders to it.defaultRootFolder }
            }
        var currentPath by remember {
            mutableStateOf(initialPath.takeIf { folders.contains(it) } ?: defaultRootFolder)
        }
        val currentFolder = folders[currentPath]
        val lazyListState = rememberLazyListState()
        DialogBase(
            title = Strings[R.string.preferences_folder_picker_dialog_title],
            onConfirm = {
                viewModel.uiManager.closeDialog()
                onConfirmOrDismiss(currentPath)
            },
            onDismiss = {
                viewModel.uiManager.closeDialog()
                onConfirmOrDismiss(null)
            },
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    IconButton(
                        onClick = {
                            currentPath = FilenameUtils.getPathNoEndSeparator(currentPath) ?: ""
                            lazyListState.requestScrollToItem(0)
                        },
                        enabled = currentPath.isNotEmpty(),
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            Strings[R.string.preferences_folder_picker_dialog_up],
                        )
                    }
                    SingleLineText(
                        FilenameUtils.getName(currentPath).takeIf { it.isNotEmpty() } ?: "/",
                        style = Typography.bodyLarge,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
                if (currentFolder?.let { it.childFolders.size + it.childTracks.size > 0 } == true) {
                    LazyColumn(
                        state = lazyListState,
                        modifier =
                            Modifier.background(
                                    MaterialTheme.colorScheme.surfaceContainer
                                )
                                .fillMaxHeight(),
                    ) {
                        items(currentFolder.childFolders) { childFolder ->
                            UtilityListItem(
                                FilenameUtils.getName(childFolder),
                                lead = {
                                    Icon(
                                        Icons.Filled.Folder,
                                        null,
                                        modifier = Modifier.padding(end = 16.dp),
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        currentPath = childFolder
                                        lazyListState.requestScrollToItem(0)
                                    },
                            )
                        }
                        items(currentFolder.childTracks) { childTrack ->
                            UtilityListItem(
                                childTrack.fileName,
                                lead = {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        null,
                                        modifier = Modifier.padding(end = 16.dp),
                                    )
                                },
                            )
                        }
                    }
                } else {
                    EmptyListIndicator()
                }
            }
        }
    }
}
