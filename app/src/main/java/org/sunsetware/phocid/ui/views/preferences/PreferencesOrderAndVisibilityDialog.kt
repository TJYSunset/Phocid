@file:OptIn(ExperimentalFoundationApi::class)

package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.rememberReorderController
import org.sunsetware.phocid.ui.components.UtilityCheckBoxListItem
import org.sunsetware.phocid.utils.swap
import sh.calvin.reorderable.ReorderableItem

@Stable
class PreferencesOrderAndVisibilityDialog<T : Any>(
    val title: String,
    val itemName: (T) -> String,
    val value: (Preferences) -> List<Pair<T, Boolean>>,
    val onSetValue: (Preferences, List<Pair<T, Boolean>>) -> Preferences,
) : Dialog() {
    private val lazyListState = LazyListState()

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val items = remember(preferences) { value(preferences) }
        val reorderController =
            rememberReorderController(
                lazyListState = lazyListState,
                items = items,
                keySelector = { it.first },
                onCommitMove = { from, to ->
                    viewModel.updatePreferences { preferences ->
                        onSetValue(
                            preferences,
                            value(preferences).toMutableList().apply { add(to, removeAt(from)) },
                        )
                    }
                },
            )

        DialogBase(title = title, onConfirmOrDismiss = { viewModel.uiManager.closeDialog() }) {
            LazyColumn(state = lazyListState) {
                itemsIndexed(
                    reorderController.reorderingItems ?: items,
                    { _, (type, _) -> type },
                ) {
                    index,
                    (type, visibility) ->
                    ReorderableItem(reorderController.reorderableLazyListState, type) {
                        UtilityCheckBoxListItem(
                            text = itemName(type),
                            checked = visibility,
                            onCheckedChange = { newVisibility ->
                                viewModel.updatePreferences { preferences ->
                                    onSetValue(
                                        preferences,
                                        value(preferences).map {
                                            it.first to
                                                (if (it.first == type) newVisibility else it.second)
                                        },
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        viewModel.updatePreferences { preferences ->
                                            if (index > 0) {
                                                onSetValue(
                                                    preferences,
                                                    value(preferences).swap(index, index - 1),
                                                )
                                            } else preferences
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowUpward,
                                        contentDescription = Strings[R.string.list_move_up],
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.updatePreferences { preferences ->
                                            if (index < value(preferences).size - 1) {
                                                onSetValue(
                                                    preferences,
                                                    value(preferences).swap(index, index + 1),
                                                )
                                            } else preferences
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowDownward,
                                        contentDescription = Strings[R.string.list_move_down],
                                    )
                                }
                                Icon(
                                    Icons.Filled.DragHandle,
                                    null,
                                    modifier =
                                        Modifier.padding(horizontal = 12.dp)
                                            .draggableHandle(
                                                onDragStarted =
                                                    reorderController.onDragStarted,
                                                onDragStopped =
                                                    reorderController.onDragStopped,
                                            ),
                                )
                            },
                            modifier =
                                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }
        }
    }
}
