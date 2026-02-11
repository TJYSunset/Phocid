package org.sunsetware.phocid.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@Immutable
class ReorderController<T>(
    val reorderableLazyListState: ReorderableLazyListState,
    val reorderingItems: List<T>?,
    val onDragStarted: (startedPosition: Offset) -> Unit,
    val onDragStopped: () -> Unit,
)

@Composable
fun <T, K> rememberReorderController(
    lazyListState: LazyListState,
    items: List<T>,
    keySelector: (T) -> K,
    onCommitMove: (from: Int, to: Int) -> Unit,
): ReorderController<T> {
    val view = LocalView.current
    var reorderingItems by remember { mutableStateOf(null as List<T>?) }
    var reorderInfo by remember { mutableStateOf(null as Pair<Int, Int>?) }
    val currentItems by rememberUpdatedState(items)
    val currentKeySelector by rememberUpdatedState(keySelector)
    val currentOnCommitMove by rememberUpdatedState(onCommitMove)

    val reorderableLazyListState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            ViewCompat.performHapticFeedback(
                view,
                HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK,
            )
            val fromIndex =
                reorderInfo?.first
                    ?: currentItems.indexOfFirst { currentKeySelector(it) == from.key }
            reorderInfo = fromIndex to to.index
            reorderingItems =
                reorderingItems?.toMutableList()?.apply { add(to.index, removeAt(from.index)) }
        }

    LaunchedEffect(items) { reorderingItems = null }

    val onDragStarted = { _: Offset ->
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.DRAG_START)
        reorderInfo = null
        reorderingItems = currentItems
    }

    val onDragStopped = {
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.GESTURE_END)
        reorderInfo?.let { (from, to) -> currentOnCommitMove(from, to) }
        reorderInfo = null
        reorderingItems = null
    }

    return ReorderController(reorderableLazyListState, reorderingItems, onDragStarted, onDragStopped)
}
