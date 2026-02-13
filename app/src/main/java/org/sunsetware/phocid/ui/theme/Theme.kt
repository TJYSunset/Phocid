package org.sunsetware.phocid.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import org.sunsetware.phocid.data.CustomThemeColor
import org.sunsetware.phocid.data.ThemeColorSource

val LocalThemeAccent = compositionLocalOf { primary400 }
val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun PhocidTheme(
    themeColorSource: ThemeColorSource,
    customThemeColor: CustomThemeColor,
    overrideThemeColor: Color?,
    darkTheme: Boolean,
    pureBackgroundColor: Boolean,
    overrideStatusBarLightColor: Boolean?,
    densityMultiplier: Float,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        remember(
            themeColorSource,
            customThemeColor,
            overrideThemeColor,
            darkTheme,
            pureBackgroundColor,
        ) {
            if (overrideThemeColor != null) {
                    customColorScheme(overrideThemeColor, darkTheme)
                } else {
                    when (themeColorSource) {
                        ThemeColorSource.DEFAULT -> {
                            if (darkTheme) darkScheme else lightScheme
                        }
                        ThemeColorSource.MATERIAL_YOU -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (darkTheme) dynamicDarkColorScheme(context)
                                else dynamicLightColorScheme(context)
                            } else {
                                if (darkTheme) darkScheme else lightScheme
                            }
                        }
                        ThemeColorSource.CUSTOM -> {
                            customColorScheme(customThemeColor.toColor(0.6f), darkTheme)
                        }
                    }
                }
                .let { if (pureBackgroundColor) it.pureBackgroundColor() else it }
        }
    val accent =
        if (overrideThemeColor != null) {
            overrideThemeColor
        } else {
            when (themeColorSource) {
                ThemeColorSource.DEFAULT -> {
                    primary400
                }

                ThemeColorSource.MATERIAL_YOU -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        colorResource(android.R.color.system_accent1_400)
                    } else {
                        primary400
                    }
                }

                ThemeColorSource.CUSTOM -> {
                    customThemeColor.toColor(0.6f)
                }
            }
        }

    LaunchedEffect(darkTheme, overrideStatusBarLightColor) {
        val activity = context as Activity
        activity.window.insetsController?.setSystemBarsAppearance(
            if (overrideStatusBarLightColor ?: !darkTheme)
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )
        activity.window.insetsController?.setSystemBarsAppearance(
            if (!darkTheme) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides
            Density(density.density * densityMultiplier, density.fontScale * densityMultiplier)
    ) {
        CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
            CompositionLocalProvider(LocalThemeAccent provides accent) {
                // Prevent random bleeding behind navigation bars in landscape mode
                if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Box(
                        modifier =
                            Modifier.background(colorScheme.surfaceContainer)
                                .padding(WindowInsets.navigationBars.asPaddingValues())
                    ) {
                        Box(modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars)) {
                            MaterialTheme(
                                colorScheme = colorScheme,
                                typography = Typography,
                                content = content,
                            )
                        }
                    }
                } else {
                    MaterialTheme(
                        colorScheme = colorScheme,
                        typography = Typography,
                        content = content,
                    )
                }
            }
        }
    }
}

@Stable
fun customColorScheme(color: Color, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkScheme else lightScheme
    val primaryLch = color.toOklch()
    val primaryHue = primaryLch.h
    val secondaryHue = primaryHue + Math.PI.toFloat() * 2 / 8
    val tertiaryHue = primaryHue - Math.PI.toFloat() * 2 / 8

    val basePrimaryLch = base.primary.toOklch()
    val primaryChromaMultiplier = (primaryLch.c / basePrimaryLch.c).coerceIn(0f, 1f)
    val baseSecondaryLch = base.secondary.toOklch()
    val secondaryChromaMultiplier = (primaryLch.c / baseSecondaryLch.c).coerceIn(0f, 1f)
    val baseTertiaryLch = base.tertiary.toOklch()
    val tertiaryChromaMultiplier = (primaryLch.c / baseTertiaryLch.c).coerceIn(0f, 1f)

    return base.transform(
        primaryTransform = { color ->
            color
                .toOklch()
                .let { Oklch(it.l, it.c * primaryChromaMultiplier, primaryHue) }
                .toColor()
        },
        secondaryTransform = { color ->
            color
                .toOklch()
                .let { Oklch(it.l, it.c * secondaryChromaMultiplier, secondaryHue) }
                .toColor()
        },
        tertiaryTransform = { color ->
            color
                .toOklch()
                .let { Oklch(it.l, it.c * tertiaryChromaMultiplier, tertiaryHue) }
                .toColor()
        },
        backgroundTransform = { color ->
            color
                .toOklch()
                .let { Oklch(it.l, it.c * primaryChromaMultiplier, primaryHue) }
                .toColor()
        },
    )
}

// region Color scheme transform

@Stable
inline fun ColorScheme.transform(
    primaryTransform: (Color) -> Color,
    secondaryTransform: (Color) -> Color,
    tertiaryTransform: (Color) -> Color,
    backgroundTransform: (Color) -> Color,
): ColorScheme {
    return copy(
        primary = primaryTransform(primary),
        onPrimary = primaryTransform(onPrimary),
        primaryContainer = primaryTransform(primaryContainer),
        onPrimaryContainer = primaryTransform(onPrimaryContainer),
        primaryFixed = primaryTransform(primaryFixed),
        primaryFixedDim = primaryTransform(primaryFixedDim),
        onPrimaryFixed = primaryTransform(onPrimaryFixed),
        onPrimaryFixedVariant = primaryTransform(onPrimaryFixedVariant),
        secondary = secondaryTransform(secondary),
        onSecondary = secondaryTransform(onSecondary),
        secondaryContainer = secondaryTransform(secondaryContainer),
        onSecondaryContainer = secondaryTransform(onSecondaryContainer),
        secondaryFixed = secondaryTransform(secondaryFixed),
        secondaryFixedDim = secondaryTransform(secondaryFixedDim),
        onSecondaryFixed = secondaryTransform(onSecondaryFixed),
        onSecondaryFixedVariant = secondaryTransform(onSecondaryFixedVariant),
        tertiary = tertiaryTransform(tertiary),
        onTertiary = tertiaryTransform(onTertiary),
        tertiaryContainer = tertiaryTransform(tertiaryContainer),
        onTertiaryContainer = tertiaryTransform(onTertiaryContainer),
        tertiaryFixed = tertiaryTransform(tertiaryFixed),
        tertiaryFixedDim = tertiaryTransform(tertiaryFixedDim),
        onTertiaryFixed = tertiaryTransform(onTertiaryFixed),
        onTertiaryFixedVariant = tertiaryTransform(onTertiaryFixedVariant),
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = backgroundTransform(background),
        onBackground = primaryTransform(onBackground),
        surface = backgroundTransform(surface),
        onSurface = primaryTransform(onSurface),
        surfaceVariant = primaryTransform(surfaceVariant),
        onSurfaceVariant = primaryTransform(onSurfaceVariant),
        outline = primaryTransform(outline),
        outlineVariant = primaryTransform(outlineVariant),
        scrim = scrim,
        inverseSurface = primaryTransform(inverseSurface),
        inverseOnSurface = primaryTransform(inverseOnSurface),
        inversePrimary = primaryTransform(inversePrimary),
        surfaceDim = backgroundTransform(surfaceDim),
        surfaceBright = backgroundTransform(surfaceBright),
        surfaceContainerLowest = primaryTransform(surfaceContainerLowest),
        surfaceContainerLow = primaryTransform(surfaceContainerLow),
        surfaceContainer = primaryTransform(surfaceContainer),
        surfaceContainerHigh = primaryTransform(surfaceContainerHigh),
        surfaceContainerHighest = primaryTransform(surfaceContainerHighest),
        surfaceTint = primaryTransform(surfaceTint),
    )
}

@Stable
fun ColorScheme.pureBackgroundColor(): ColorScheme {
    return transform(
        primaryTransform = { it },
        secondaryTransform = { it },
        tertiaryTransform = { it },
        backgroundTransform = {
            if (it.luminance() < 0.5f) Color(0xff000000) else Color(0xffffffff)
        },
    )
}

// endregion

// region Theme builder

private val lightScheme =
    lightColorScheme(
        primary = primaryLight,
        onPrimary = onPrimaryLight,
        primaryContainer = primaryContainerLight,
        onPrimaryContainer = onPrimaryContainerLight,
        secondary = secondaryLight,
        onSecondary = onSecondaryLight,
        secondaryContainer = secondaryContainerLight,
        onSecondaryContainer = onSecondaryContainerLight,
        tertiary = tertiaryLight,
        onTertiary = onTertiaryLight,
        tertiaryContainer = tertiaryContainerLight,
        onTertiaryContainer = onTertiaryContainerLight,
        error = errorLight,
        onError = onErrorLight,
        errorContainer = errorContainerLight,
        onErrorContainer = onErrorContainerLight,
        background = backgroundLight,
        onBackground = onBackgroundLight,
        surface = surfaceLight,
        onSurface = onSurfaceLight,
        surfaceVariant = surfaceVariantLight,
        onSurfaceVariant = onSurfaceVariantLight,
        outline = outlineLight,
        outlineVariant = outlineVariantLight,
        scrim = scrimLight,
        inverseSurface = inverseSurfaceLight,
        inverseOnSurface = inverseOnSurfaceLight,
        inversePrimary = inversePrimaryLight,
        surfaceDim = surfaceDimLight,
        surfaceBright = surfaceBrightLight,
        surfaceContainerLowest = surfaceContainerLowestLight,
        surfaceContainerLow = surfaceContainerLowLight,
        surfaceContainer = surfaceContainerLight,
        surfaceContainerHigh = surfaceContainerHighLight,
        surfaceContainerHighest = surfaceContainerHighestLight,
    )

private val darkScheme =
    darkColorScheme(
        primary = primaryDark,
        onPrimary = onPrimaryDark,
        primaryContainer = primaryContainerDark,
        onPrimaryContainer = onPrimaryContainerDark,
        secondary = secondaryDark,
        onSecondary = onSecondaryDark,
        secondaryContainer = secondaryContainerDark,
        onSecondaryContainer = onSecondaryContainerDark,
        tertiary = tertiaryDark,
        onTertiary = onTertiaryDark,
        tertiaryContainer = tertiaryContainerDark,
        onTertiaryContainer = onTertiaryContainerDark,
        error = errorDark,
        onError = onErrorDark,
        errorContainer = errorContainerDark,
        onErrorContainer = onErrorContainerDark,
        background = backgroundDark,
        onBackground = onBackgroundDark,
        surface = surfaceDark,
        onSurface = onSurfaceDark,
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = onSurfaceVariantDark,
        outline = outlineDark,
        outlineVariant = outlineVariantDark,
        scrim = scrimDark,
        inverseSurface = inverseSurfaceDark,
        inverseOnSurface = inverseOnSurfaceDark,
        inversePrimary = inversePrimaryDark,
        surfaceDim = surfaceDimDark,
        surfaceBright = surfaceBrightDark,
        surfaceContainerLowest = surfaceContainerLowestDark,
        surfaceContainerLow = surfaceContainerLowDark,
        surfaceContainer = surfaceContainerDark,
        surfaceContainerHigh = surfaceContainerHighDark,
        surfaceContainerHighest = surfaceContainerHighestDark,
    )

private val mediumContrastLightColorScheme =
    lightColorScheme(
        primary = primaryLightMediumContrast,
        onPrimary = onPrimaryLightMediumContrast,
        primaryContainer = primaryContainerLightMediumContrast,
        onPrimaryContainer = onPrimaryContainerLightMediumContrast,
        secondary = secondaryLightMediumContrast,
        onSecondary = onSecondaryLightMediumContrast,
        secondaryContainer = secondaryContainerLightMediumContrast,
        onSecondaryContainer = onSecondaryContainerLightMediumContrast,
        tertiary = tertiaryLightMediumContrast,
        onTertiary = onTertiaryLightMediumContrast,
        tertiaryContainer = tertiaryContainerLightMediumContrast,
        onTertiaryContainer = onTertiaryContainerLightMediumContrast,
        error = errorLightMediumContrast,
        onError = onErrorLightMediumContrast,
        errorContainer = errorContainerLightMediumContrast,
        onErrorContainer = onErrorContainerLightMediumContrast,
        background = backgroundLightMediumContrast,
        onBackground = onBackgroundLightMediumContrast,
        surface = surfaceLightMediumContrast,
        onSurface = onSurfaceLightMediumContrast,
        surfaceVariant = surfaceVariantLightMediumContrast,
        onSurfaceVariant = onSurfaceVariantLightMediumContrast,
        outline = outlineLightMediumContrast,
        outlineVariant = outlineVariantLightMediumContrast,
        scrim = scrimLightMediumContrast,
        inverseSurface = inverseSurfaceLightMediumContrast,
        inverseOnSurface = inverseOnSurfaceLightMediumContrast,
        inversePrimary = inversePrimaryLightMediumContrast,
        surfaceDim = surfaceDimLightMediumContrast,
        surfaceBright = surfaceBrightLightMediumContrast,
        surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
        surfaceContainerLow = surfaceContainerLowLightMediumContrast,
        surfaceContainer = surfaceContainerLightMediumContrast,
        surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
        surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
    )

private val highContrastLightColorScheme =
    lightColorScheme(
        primary = primaryLightHighContrast,
        onPrimary = onPrimaryLightHighContrast,
        primaryContainer = primaryContainerLightHighContrast,
        onPrimaryContainer = onPrimaryContainerLightHighContrast,
        secondary = secondaryLightHighContrast,
        onSecondary = onSecondaryLightHighContrast,
        secondaryContainer = secondaryContainerLightHighContrast,
        onSecondaryContainer = onSecondaryContainerLightHighContrast,
        tertiary = tertiaryLightHighContrast,
        onTertiary = onTertiaryLightHighContrast,
        tertiaryContainer = tertiaryContainerLightHighContrast,
        onTertiaryContainer = onTertiaryContainerLightHighContrast,
        error = errorLightHighContrast,
        onError = onErrorLightHighContrast,
        errorContainer = errorContainerLightHighContrast,
        onErrorContainer = onErrorContainerLightHighContrast,
        background = backgroundLightHighContrast,
        onBackground = onBackgroundLightHighContrast,
        surface = surfaceLightHighContrast,
        onSurface = onSurfaceLightHighContrast,
        surfaceVariant = surfaceVariantLightHighContrast,
        onSurfaceVariant = onSurfaceVariantLightHighContrast,
        outline = outlineLightHighContrast,
        outlineVariant = outlineVariantLightHighContrast,
        scrim = scrimLightHighContrast,
        inverseSurface = inverseSurfaceLightHighContrast,
        inverseOnSurface = inverseOnSurfaceLightHighContrast,
        inversePrimary = inversePrimaryLightHighContrast,
        surfaceDim = surfaceDimLightHighContrast,
        surfaceBright = surfaceBrightLightHighContrast,
        surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
        surfaceContainerLow = surfaceContainerLowLightHighContrast,
        surfaceContainer = surfaceContainerLightHighContrast,
        surfaceContainerHigh = surfaceContainerHighLightHighContrast,
        surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
    )

private val mediumContrastDarkColorScheme =
    darkColorScheme(
        primary = primaryDarkMediumContrast,
        onPrimary = onPrimaryDarkMediumContrast,
        primaryContainer = primaryContainerDarkMediumContrast,
        onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
        secondary = secondaryDarkMediumContrast,
        onSecondary = onSecondaryDarkMediumContrast,
        secondaryContainer = secondaryContainerDarkMediumContrast,
        onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
        tertiary = tertiaryDarkMediumContrast,
        onTertiary = onTertiaryDarkMediumContrast,
        tertiaryContainer = tertiaryContainerDarkMediumContrast,
        onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
        error = errorDarkMediumContrast,
        onError = onErrorDarkMediumContrast,
        errorContainer = errorContainerDarkMediumContrast,
        onErrorContainer = onErrorContainerDarkMediumContrast,
        background = backgroundDarkMediumContrast,
        onBackground = onBackgroundDarkMediumContrast,
        surface = surfaceDarkMediumContrast,
        onSurface = onSurfaceDarkMediumContrast,
        surfaceVariant = surfaceVariantDarkMediumContrast,
        onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
        outline = outlineDarkMediumContrast,
        outlineVariant = outlineVariantDarkMediumContrast,
        scrim = scrimDarkMediumContrast,
        inverseSurface = inverseSurfaceDarkMediumContrast,
        inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
        inversePrimary = inversePrimaryDarkMediumContrast,
        surfaceDim = surfaceDimDarkMediumContrast,
        surfaceBright = surfaceBrightDarkMediumContrast,
        surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
        surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
        surfaceContainer = surfaceContainerDarkMediumContrast,
        surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
        surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
    )

private val highContrastDarkColorScheme =
    darkColorScheme(
        primary = primaryDarkHighContrast,
        onPrimary = onPrimaryDarkHighContrast,
        primaryContainer = primaryContainerDarkHighContrast,
        onPrimaryContainer = onPrimaryContainerDarkHighContrast,
        secondary = secondaryDarkHighContrast,
        onSecondary = onSecondaryDarkHighContrast,
        secondaryContainer = secondaryContainerDarkHighContrast,
        onSecondaryContainer = onSecondaryContainerDarkHighContrast,
        tertiary = tertiaryDarkHighContrast,
        onTertiary = onTertiaryDarkHighContrast,
        tertiaryContainer = tertiaryContainerDarkHighContrast,
        onTertiaryContainer = onTertiaryContainerDarkHighContrast,
        error = errorDarkHighContrast,
        onError = onErrorDarkHighContrast,
        errorContainer = errorContainerDarkHighContrast,
        onErrorContainer = onErrorContainerDarkHighContrast,
        background = backgroundDarkHighContrast,
        onBackground = onBackgroundDarkHighContrast,
        surface = surfaceDarkHighContrast,
        onSurface = onSurfaceDarkHighContrast,
        surfaceVariant = surfaceVariantDarkHighContrast,
        onSurfaceVariant = onSurfaceVariantDarkHighContrast,
        outline = outlineDarkHighContrast,
        outlineVariant = outlineVariantDarkHighContrast,
        scrim = scrimDarkHighContrast,
        inverseSurface = inverseSurfaceDarkHighContrast,
        inverseOnSurface = inverseOnSurfaceDarkHighContrast,
        inversePrimary = inversePrimaryDarkHighContrast,
        surfaceDim = surfaceDimDarkHighContrast,
        surfaceBright = surfaceBrightDarkHighContrast,
        surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
        surfaceContainerLow = surfaceContainerLowDarkHighContrast,
        surfaceContainer = surfaceContainerDarkHighContrast,
        surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
        surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
    )

// endregion
