package org.sunsetware.phocid

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ibm.icu.number.Notation
import com.ibm.icu.number.NumberFormatter
import com.ibm.icu.number.Precision
import com.ibm.icu.util.MeasureUnit
import java.util.Locale
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.sunsetware.phocid.data.DarkThemePreference
import org.sunsetware.phocid.data.ShapePreference
import org.sunsetware.phocid.data.WidgetLayout
import org.sunsetware.phocid.globals.GlobalData
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.SelectBox
import org.sunsetware.phocid.ui.components.UtilityListHeader
import org.sunsetware.phocid.ui.components.UtilityListItem
import org.sunsetware.phocid.ui.components.UtilitySwitchListItem
import org.sunsetware.phocid.ui.theme.PhocidTheme
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.utils.roundToIntOrZero

class WidgetConfigureActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        while (!GlobalData.initialized.get()) {
            Thread.sleep(50)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false

        super.onCreate(savedInstanceState)

        val preferencesFlow = GlobalData.preferences

        setContent {
            val preferences by preferencesFlow.collectAsStateWithLifecycle()
            var artworkResolutionLimit by rememberSaveable {
                mutableIntStateOf(preferences.widgetArtworkResolutionLimit)
            }

            PhocidTheme(
                themeColorSource = preferences.themeColorSource,
                customThemeColor = preferences.customThemeColor,
                overrideThemeColor = null,
                darkTheme = preferences.darkTheme.boolean ?: isSystemInDarkTheme(),
                pureBackgroundColor = preferences.pureBackgroundColor,
                overrideStatusBarLightColor = null,
                densityMultiplier = preferences.densityMultiplier,
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(Strings[R.string.preferences_widget_settings]) },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        setResult(RESULT_CANCELED)
                                        finish()
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = Strings[R.string.commons_close],
                                    )
                                }
                            },
                        )
                    }
                ) { scaffoldPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column {
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            ) {
                                UtilityListHeader(Strings[R.string.preferences_widget_color])
                                UtilitySwitchListItem(
                                    title = Strings[R.string.preferences_widget_artwork_background],
                                    checked = preferences.widgetArtworkBackground,
                                    onCheckedChange = { checked ->
                                        preferencesFlow.update {
                                            it.copy(widgetArtworkBackground = checked)
                                        }
                                    },
                                )
                                UtilitySwitchListItem(
                                    title = Strings[R.string.preferences_widget_accent_background],
                                    checked = preferences.widgetAccentBackground,
                                    onCheckedChange = { checked ->
                                        preferencesFlow.update {
                                            it.copy(widgetAccentBackground = checked)
                                        }
                                    },
                                )
                                UtilityListItem(
                                    title = Strings[R.string.preferences_widget_dark_theme],
                                    actions = {
                                        SelectBox(
                                            items =
                                                DarkThemePreference.entries.map {
                                                    Strings[it.stringId]
                                                },
                                            activeIndex =
                                                DarkThemePreference.entries.indexOf(
                                                    preferences.widgetDarkTheme
                                                ),
                                            onSetActiveIndex = { index ->
                                                preferencesFlow.update {
                                                    it.copy(
                                                        widgetDarkTheme =
                                                            DarkThemePreference.entries[index]
                                                    )
                                                }
                                            },
                                            modifier = Modifier.padding(start = 16.dp).weight(1f),
                                        )
                                    },
                                )

                                UtilityListHeader(
                                    Strings[R.string.preferences_widget_artwork_resolution_limit]
                                )
                                Text(
                                    NumberFormatter.withLocale(Locale.getDefault())
                                        .notation(Notation.simple())
                                        .precision(Precision.integer())
                                        .unit(MeasureUnit.PIXEL)
                                        .format(artworkResolutionLimit)
                                        .toString(),
                                    modifier =
                                        Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                                )
                                Slider(
                                    value = artworkResolutionLimit.toFloat(),
                                    valueRange = 100f..5000f,
                                    steps = (5000 - 100) / 100 - 1,
                                    onValueChange = {
                                        artworkResolutionLimit =
                                            it.roundToIntOrZero().coerceIn(100, 5000)
                                    },
                                    onValueChangeFinished = {
                                        preferencesFlow.update {
                                            it.copy(
                                                widgetArtworkResolutionLimit =
                                                    artworkResolutionLimit
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    track = { SliderDefaults.Track(it, drawTick = { _, _ -> }) },
                                )
                                Text(
                                    Strings[
                                        R.string
                                            .preferences_widget_artwork_resolution_limit_subtitle],
                                    style = Typography.labelSmall,
                                    modifier =
                                        Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                                )

                                UtilityListHeader(Strings[R.string.preferences_widget_layout])
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                    for (layout in WidgetLayout.entries) {
                                        val selected = preferences.widgetLayout == layout
                                        Box(
                                            modifier =
                                                Modifier.padding(horizontal = 24.dp)
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme
                                                            .surfaceContainerHigh,
                                                        ShapePreference.ROUNDED_SQUARE.cardShape,
                                                    )
                                                    .clickable(
                                                        onClick = {
                                                            preferencesFlow.update {
                                                                it.copy(widgetLayout = layout)
                                                            }
                                                        }
                                                    )
                                        ) {
                                            Image(
                                                painterResource(layout.previewId),
                                                Strings[layout.stringId],
                                                colorFilter =
                                                    ColorFilter.tint(
                                                        if (selected)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme
                                                                .onSurfaceVariant
                                                    ),
                                                modifier =
                                                    Modifier.align(Alignment.Center)
                                                        .padding(24.dp)
                                                        .size(150.dp, 120.dp),
                                            )
                                            if (selected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    Strings[R.string.commons_selected],
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier =
                                                        Modifier.align(Alignment.BottomEnd)
                                                            .padding(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                onClick = {
                                    val appWidgetId =
                                        intent
                                            ?.extras
                                            ?.getInt(
                                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                AppWidgetManager.INVALID_APPWIDGET_ID,
                                            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
                                    val resultValue =
                                        Intent()
                                            .putExtra(
                                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                appWidgetId,
                                            )
                                    runBlocking {
                                        MainAppWidget().updateAll(this@WidgetConfigureActivity)
                                    }
                                    setResult(RESULT_OK, resultValue)
                                    finish()
                                },
                            ) {
                                Text(Strings[R.string.commons_ok])
                            }
                        }
                    }
                }
            }
        }
    }
}
