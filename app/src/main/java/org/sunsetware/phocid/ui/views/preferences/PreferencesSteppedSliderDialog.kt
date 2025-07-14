@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.SteppedSliderWithNumber

@Stable
class PreferencesSteppedSliderDialog(
    private val title: String,
    private val numberFormatter: (Float) -> String,
    private val initialValue: (Preferences) -> Float,
    private val defaultValue: Float,
    private val min: Float,
    private val max: Float,
    private val steps: Int,
    private val onSetValue: (Preferences, Float) -> Preferences,
) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        var value by remember { mutableFloatStateOf(initialValue(viewModel.preferences.value)) }
        DialogBase(
            title = title,
            onConfirm = {
                viewModel.updatePreferences { onSetValue(it, value) }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            SteppedSliderWithNumber(
                number = numberFormatter(value),
                onReset = { value = defaultValue },
                value = value,
                onValueChange = { value = it },
                steps = steps,
                valueRange = min..max,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
