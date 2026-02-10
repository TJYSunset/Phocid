package org.sunsetware.phocid.ui.views

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase

@Stable
class ClearHistoryDialog : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        DialogBase(
            title = Strings[R.string.history_clear_dialog_title],
            onConfirm = {
                viewModel.clearHistory()
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
            confirmText = Strings[R.string.commons_clear],
        ) {
            Text(Strings[R.string.history_clear_dialog_body])
        }
    }
}
