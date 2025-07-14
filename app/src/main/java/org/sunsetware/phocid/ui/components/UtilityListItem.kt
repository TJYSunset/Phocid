package org.sunsetware.phocid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.ui.theme.Typography

@Composable
fun UtilityListHeader(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = Typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
inline fun UtilityListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    crossinline lead: @Composable () -> Unit = {},
    crossinline actions: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (subtitle != null) 72.dp else 56.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

@Composable
fun UtilityRadioButtonListItem(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .defaultMinSize(minHeight = 56.dp)
                .padding(end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        )
        Text(text = text, style = Typography.bodyLarge)
    }
}

@Composable
inline fun UtilityCheckBoxListItem(
    text: String,
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    enabled: Boolean = true,
    crossinline actions: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .let { if (enabled) it.clickable(onClick = { onCheckedChange(!checked) }) else it }
                .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            enabled = enabled,
        )
        Text(text = text, style = Typography.bodyLarge, modifier = textModifier.weight(1f))
        actions()
    }
}

@Composable
inline fun UtilitySwitchListItem(
    title: String,
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    crossinline lead: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = { onCheckedChange(!checked) })
                .defaultMinSize(minHeight = if (subtitle != null) 72.dp else 56.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lead()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = Typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked, { onCheckedChange(it) })
    }
}
