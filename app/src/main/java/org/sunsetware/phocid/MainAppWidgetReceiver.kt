package org.sunsetware.phocid

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.layoutDirection
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentWidth
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.WidgetLayout
import org.sunsetware.phocid.data.getArtworkColor
import org.sunsetware.phocid.data.loadArtwork
import org.sunsetware.phocid.globals.GlobalData
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.theme.VARIANT_ALPHA
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.onPrimaryDark
import org.sunsetware.phocid.ui.theme.onPrimaryLight
import org.sunsetware.phocid.ui.theme.onSurfaceDark
import org.sunsetware.phocid.ui.theme.onSurfaceLight
import org.sunsetware.phocid.ui.theme.primaryDark
import org.sunsetware.phocid.ui.theme.primaryLight
import org.sunsetware.phocid.ui.theme.surfaceDark
import org.sunsetware.phocid.ui.theme.surfaceLight
import org.sunsetware.phocid.ui.theme.toGlanceStyle
import org.sunsetware.phocid.utils.combine

class MainAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MainAppWidget()
}

class MainAppWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        while (!GlobalData.initialized.get()) {
            delay(1)
        }
        val coroutineScope = CoroutineScope(coroutineContext + Dispatchers.IO)
        val trackAndArtworkState =
            GlobalData.libraryIndex.combine(
                coroutineScope,
                GlobalData.playerState,
                GlobalData.preferences,
            ) { libraryIndex, playerState, preferences ->
                val track =
                    libraryIndex.tracks[
                            playerState.actualPlayQueue.getOrNull(playerState.currentIndex),
                        ]
                track to
                    track?.let {
                        loadArtwork(
                            context,
                            it.id,
                            it.path,
                            preferences.highResArtworkPreference.player,
                            preferences.widgetArtworkResolutionLimit,
                            preferences.widgetLayout.standaloneArtwork,
                        )
                    }
            }

        provideContent {
            val resources = LocalContext.current.resources

            val preferences by GlobalData.preferences.collectAsState()
            val playerTransientState by GlobalData.playerTransientState.collectAsState()

            val isDarkTheme =
                preferences.widgetDarkTheme.boolean
                    ?: (context.resources.configuration.uiMode.and(
                        Configuration.UI_MODE_NIGHT_MASK
                    ) == Configuration.UI_MODE_NIGHT_YES)
            val colorPreference = isDarkTheme to preferences.widgetAccentBackground

            val trackAndArtwork by trackAndArtworkState.collectAsState()
            val track = trackAndArtwork.first
            val artwork = trackAndArtwork.second

            val artworkColor =
                track?.getArtworkColor(preferences.artworkColorPreference)?.takeIf {
                    preferences.widgetArtworkBackground
                }
            val backgroundColor =
                artworkColor?.let {
                    if (preferences.widgetLayout.standaloneArtwork) it
                    else lerp(it, Color.Black, 0.4f)
                }
                    ?: when (colorPreference) {
                        false to false -> surfaceLight
                        false to true -> primaryLight
                        true to false -> surfaceDark
                        true to true -> primaryDark
                        else -> throw Error()
                    }
            val contentColor =
                artworkColor?.let {
                    if (preferences.widgetLayout.standaloneArtwork) it.contentColor()
                    else lerp(it, Color.White, 0.9f)
                }
                    ?: when (colorPreference) {
                        false to false -> onSurfaceLight
                        false to true -> onPrimaryLight
                        true to false -> onSurfaceDark
                        true to true -> onPrimaryDark
                        else -> throw Error()
                    }
            val contentColorVariant = contentColor.copy(alpha = VARIANT_ALPHA)

            val backgroundRadius =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Dp(
                        resources.getDimension(
                            android.R.dimen.system_app_widget_background_radius
                        ) / resources.displayMetrics.density
                    )
                } else {
                    0.dp
                }

            GlanceTheme {
                Box(
                    modifier =
                        GlanceModifier.cornerRadius(backgroundRadius)
                            .let {
                                if (
                                    artwork != null &&
                                        preferences.widgetArtworkBackground &&
                                        !preferences.widgetLayout.standaloneArtwork
                                ) {
                                    it.background(
                                        ImageProvider(artwork),
                                        contentScale = ContentScale.Crop,
                                        colorFilter =
                                            ColorFilter.tint(
                                                @SuppressLint("RestrictedApi")
                                                ColorProvider(backgroundColor.copy(alpha = 0.6f))
                                            ),
                                    )
                                } else {
                                    it.background(
                                        @SuppressLint("RestrictedApi")
                                        ColorProvider(backgroundColor)
                                    )
                                }
                            }
                            .clickable(
                                actionStartActivity(Intent(context, MainActivity::class.java))
                            )
                ) {
                    when (preferences.widgetLayout) {
                        WidgetLayout.SMALL -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = GlanceModifier.fillMaxSize(),
                            ) {
                                Controls(context, playerTransientState.isPlaying, contentColor)
                            }
                        }
                        WidgetLayout.MEDIUM -> {
                            Row(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    GlanceModifier.padding(start = 24.dp, end = 12.dp).fillMaxSize(),
                            ) {
                                Box(modifier = GlanceModifier.defaultWeight()) {
                                    TrackInfo(track, contentColor, contentColorVariant)
                                }
                                Controls(context, playerTransientState.isPlaying, contentColor)
                            }
                        }
                        WidgetLayout.LARGE -> {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = GlanceModifier.padding(horizontal = 12.dp).fillMaxSize(),
                            ) {
                                TrackInfo(
                                    track,
                                    contentColor,
                                    contentColorVariant,
                                    modifier = GlanceModifier.padding(horizontal = 14.dp),
                                )
                                Controls(
                                    context,
                                    playerTransientState.isPlaying,
                                    contentColor,
                                    modifier = GlanceModifier.padding(bottom = (-14).dp),
                                )
                            }
                        }
                        WidgetLayout.EXTRA_LARGE -> {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalAlignment = Alignment.Bottom,
                                modifier = GlanceModifier.padding(12.dp).fillMaxSize(),
                            ) {
                                Spacer(modifier = GlanceModifier.height(14.dp))
                                TrackInfo(
                                    track,
                                    contentColor,
                                    contentColorVariant,
                                    modifier = GlanceModifier.padding(horizontal = 14.dp),
                                )
                                Controls(context, playerTransientState.isPlaying, contentColor)
                            }
                        }
                        WidgetLayout.SIDE_ARTWORK -> {
                            Row {
                                ArtworkImage(
                                    artwork,
                                    backgroundColor,
                                    isDarkTheme,
                                    modifier = GlanceModifier.fillMaxHeight().wrapContentWidth(),
                                )
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        GlanceModifier.padding(horizontal = 12.dp).fillMaxHeight(),
                                ) {
                                    TrackInfo(
                                        track,
                                        contentColor,
                                        contentColorVariant,
                                        modifier =
                                            GlanceModifier.padding(start = 14.dp, end = 14.dp),
                                    )
                                    Controls(
                                        context,
                                        playerTransientState.isPlaying,
                                        contentColor,
                                        spread = true,
                                        modifier =
                                            GlanceModifier.fillMaxWidth().padding(bottom = (-14).dp),
                                    )
                                }
                            }
                        }
                        WidgetLayout.SIDE_ARTWORK_LARGE -> {
                            Row {
                                ArtworkImage(
                                    artwork,
                                    backgroundColor,
                                    isDarkTheme,
                                    modifier = GlanceModifier.fillMaxHeight().wrapContentWidth(),
                                )
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = GlanceModifier.fillMaxHeight(),
                                ) {
                                    TrackInfo(
                                        track,
                                        contentColor,
                                        contentColorVariant,
                                        modifier =
                                            GlanceModifier.padding(start = 14.dp, end = 14.dp),
                                    )
                                    Controls(
                                        context,
                                        playerTransientState.isPlaying,
                                        contentColor,
                                        spread = true,
                                        modifier =
                                            GlanceModifier.fillMaxWidth().padding(bottom = (-14).dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TrackInfo(
        currentTrack: Track?,
        contentColor: Color,
        contentColorVariant: Color,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Column(modifier) {
            Text(
                currentTrack?.displayTitle ?: Strings[R.string.app_name],
                style =
                    Typography.bodyLarge
                        .toGlanceStyle()
                        .copy(
                            fontWeight = FontWeight.Medium,
                            color = @SuppressLint("RestrictedApi") ColorProvider(contentColor),
                        ),
                maxLines = 1,
            )
            Text(
                currentTrack?.displayArtistWithAlbum ?: "",
                style =
                    Typography.bodySmall
                        .toGlanceStyle()
                        .copy(
                            color =
                                @SuppressLint("RestrictedApi") ColorProvider(contentColorVariant)
                        ),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun Controls(
        context: Context,
        isPlaying: Boolean,
        contentColor: Color,
        modifier: GlanceModifier = GlanceModifier,
        spread: Boolean = false,
    ) {
        @Composable
        fun Previous() {
            IconButton(
                R.drawable.player_previous,
                Strings[R.string.player_previous],
                contentColor,
                GlanceModifier.clickable {
                    withController(context) {
                        it.seekToPrevious()
                        it.play()
                    }
                },
            )
        }
        @Composable
        fun Next() {
            IconButton(
                R.drawable.player_next,
                Strings[R.string.player_next],
                contentColor,
                GlanceModifier.clickable {
                    withController(context) {
                        it.seekToNext()
                        it.play()
                    }
                },
            )
        }

        val ltr = Locale.getDefault().layoutDirection != View.LAYOUT_DIRECTION_RTL

        Row(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            if (ltr) Previous() else Next()
            if (spread) {
                Box(modifier = GlanceModifier.defaultWeight()) {}
            }
            IconButton(
                if (isPlaying) R.drawable.player_pause else R.drawable.player_play,
                if (isPlaying) Strings[R.string.player_pause] else Strings[R.string.player_play],
                contentColor,
                GlanceModifier.clickable {
                    withController(context) { if (it.isPlaying) it.pause() else it.play() }
                },
            )
            if (spread) {
                Box(modifier = GlanceModifier.defaultWeight()) {}
            }
            if (ltr) Next() else Previous()
        }
    }

    @Composable
    private fun IconButton(
        @DrawableRes resId: Int,
        description: String,
        tint: Color,
        onClick: GlanceModifier,
    ) {
        Box(
            modifier =
                GlanceModifier.semantics { contentDescription = description }
                    .size(48.dp)
                    .cornerRadius(24.dp)
                    .padding(12.dp)
                    .then(onClick)
        ) {
            // For whatever reason, `Image` refuses to work
            Box(
                modifier =
                    GlanceModifier.background(
                            ImageProvider(resId),
                            colorFilter =
                                ColorFilter.tint(@SuppressLint("RestrictedApi") ColorProvider(tint)),
                        )
                        .size(24.dp)
            ) {}
        }
    }

    @Composable
    private fun ArtworkImage(
        artwork: Bitmap?,
        color: Color,
        darkTheme: Boolean,
        modifier: GlanceModifier,
    ) {
        if (artwork != null) {
            Image(
                ImageProvider(artwork),
                null,
                contentScale = ContentScale.Fit,
                modifier = modifier,
            )
        } else {
            Box {
                Image(
                    ImageProvider(R.drawable.widget_artwork_placeholder_background),
                    null,
                    contentScale = ContentScale.Fit,
                    colorFilter =
                        ColorFilter.tint(
                            @SuppressLint("RestrictedApi")
                            ColorProvider(
                                if (darkTheme) lerp(color, Color.Black, 0.4f)
                                else lerp(color, Color.White, 0.9f)
                            )
                        ),
                    modifier = modifier,
                )
                Image(
                    ImageProvider(R.drawable.widget_artwork_placeholder_foreground),
                    null,
                    contentScale = ContentScale.Fit,
                    colorFilter =
                        ColorFilter.tint(@SuppressLint("RestrictedApi") ColorProvider(color)),
                    modifier = modifier,
                )
            }
        }
    }

    private inline fun withController(
        context: Context,
        crossinline action: (MediaController) -> Unit,
    ) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            { action(controllerFuture.get()) },
            MoreExecutors.directExecutor(),
        )
    }
}
