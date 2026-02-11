package org.sunsetware.phocid.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.data.HistoryClearRange
import org.sunsetware.phocid.data.cutoffMillis
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.SelectBox
import org.sunsetware.phocid.utils.icuFormat

@Stable
class ClearHistoryDialog : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val historyEntries by viewModel.historyEntries.collectAsStateWithLifecycle()
        val ranges = remember { HistoryClearRange.entries }
        val rangeLabels = remember {
            ranges.map { Strings[it.stringId] }
        }
        var activeRangeIndex by rememberSaveable { mutableIntStateOf(0) }
        val activeRange = ranges[activeRangeIndex]
        val now = System.currentTimeMillis()
        val cutoff = activeRange.cutoffMillis(now)
        val removeCount =
            if (cutoff == null) {
                historyEntries.size
            } else {
                historyEntries.count { it.timestamp >= cutoff }
            }
        DialogBase(
            title = Strings[R.string.history_clear_dialog_title],
            onConfirm = {
                viewModel.clearHistory(activeRange)
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
            confirmText = Strings[R.string.commons_clear],
            confirmEnabled = removeCount > 0,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(Strings[R.string.history_clear_dialog_body])
                SelectBox(
                    items = rangeLabels,
                    activeIndex = activeRangeIndex,
                    onSetActiveIndex = { activeRangeIndex = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(Strings[R.string.history_clear_dialog_count].icuFormat(removeCount))
            }
        }
    }
}
