package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.ui.components.DialogBase

@Stable
class PreferencesTextInputDialog(
    val title: String,
    val placeholder: String,
    val allowEmpty: Boolean,
    val initialValue: String,
    val updatePreferences: (Preferences, String) -> Preferences,
) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        var textFieldValue by rememberSaveable { mutableStateOf(initialValue) }
        DialogBase(
            title = title,
            onConfirm = {
                viewModel.updatePreferences { updatePreferences(it, textFieldValue) }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
            confirmEnabled = allowEmpty || textFieldValue.isNotEmpty(),
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions {
                        if (allowEmpty || textFieldValue.isNotEmpty()) {
                            viewModel.updatePreferences { updatePreferences(it, textFieldValue) }
                            viewModel.uiManager.closeDialog()
                        }
                    },
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
