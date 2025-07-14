package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.sunsetware.phocid.data.CustomThemeColor
import org.sunsetware.phocid.data.ThemeColorSource
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.SelectBox
import org.sunsetware.phocid.ui.components.SteppedSliderWithNumber
import org.sunsetware.phocid.ui.components.UtilityListHeader
import org.sunsetware.phocid.ui.components.UtilitySwitchListItem
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.roundToIntOrZero

@Stable
class PreferencesThemeColorDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        var chromaPercentage by rememberSaveable {
            mutableIntStateOf(preferences.customThemeColor.chromaPercentage)
        }
        var hueDegrees by rememberSaveable {
            mutableIntStateOf(preferences.customThemeColor.hueDegrees)
        }
        val previewColor =
            remember(chromaPercentage, hueDegrees) {
                CustomThemeColor(chromaPercentage, hueDegrees).toColor(0.6f)
            }

        DialogBase(
            title = Strings[R.string.preferences_theme_color],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectBox(
                    items = ThemeColorSource.entries.map { Strings[it.stringId] },
                    activeIndex = ThemeColorSource.entries.indexOf(preferences.themeColorSource),
                    onSetActiveIndex = { index ->
                        viewModel.updatePreferences {
                            it.copy(themeColorSource = ThemeColorSource.entries[index])
                        }
                    },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                AnimatedVisibility(preferences.themeColorSource == ThemeColorSource.CUSTOM) {
                    Column {
                        Box(
                            modifier =
                                Modifier.padding(24.dp)
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .background(previewColor)
                        )

                        UtilityListHeader(Strings[R.string.preferences_theme_color_chroma])
                        SteppedSliderWithNumber(
                            number =
                                Strings[R.string.preferences_theme_color_chroma_number].icuFormat(
                                    chromaPercentage
                                ),
                            value = chromaPercentage.toFloat(),
                            onValueChange = { chromaPercentage = it.roundToIntOrZero() },
                            onValueChangeFinished = {
                                viewModel.updatePreferences {
                                    it.copy(
                                        customThemeColor =
                                            CustomThemeColor(chromaPercentage, hueDegrees)
                                    )
                                }
                            },
                            steps = 100 - 1,
                            valueRange = 0f..100f,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        UtilityListHeader(Strings[R.string.preferences_theme_color_hue])
                        SteppedSliderWithNumber(
                            number =
                                Strings[R.string.preferences_theme_color_hue_number].icuFormat(
                                    hueDegrees
                                ),
                            value = hueDegrees.toFloat(),
                            onValueChange = { hueDegrees = it.roundToIntOrZero() },
                            onValueChangeFinished = {
                                viewModel.updatePreferences {
                                    it.copy(
                                        customThemeColor =
                                            CustomThemeColor(chromaPercentage, hueDegrees)
                                    )
                                }
                            },
                            steps = 359 - 1,
                            valueRange = 0f..359f,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }
                UtilitySwitchListItem(
                    title = Strings[R.string.preferences_colored_global_theme],
                    checked = preferences.coloredGlobalTheme,
                    onCheckedChange = { checked ->
                        viewModel.updatePreferences { it.copy(coloredGlobalTheme = checked) }
                    },
                )
            }
        }
    }
}
