package org.sunsetware.phocid.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.getArtworkColor
import org.sunsetware.phocid.data.loadArtwork
import org.sunsetware.phocid.ui.theme.LocalDarkTheme
import org.sunsetware.phocid.ui.theme.emphasizedStandard
import org.sunsetware.phocid.utils.AsyncCache
import org.sunsetware.phocid.utils.Boxed

@Immutable
sealed class Artwork {
    abstract fun getColor(artworkColorPreference: ArtworkColorPreference): Color

    @Immutable
    data class Track(val track: org.sunsetware.phocid.data.Track) : Artwork() {
        override fun getColor(artworkColorPreference: ArtworkColorPreference): Color {
            return track.getArtworkColor(artworkColorPreference)
        }
    }

    @Immutable
    data class Icon(val icon: ImageVector, val color: Color) : Artwork() {
        override fun getColor(artworkColorPreference: ArtworkColorPreference): Color {
            return color
        }
    }
}

typealias ArtworkCache = AsyncCache<Track, Boxed<Bitmap?>>

@Composable
fun ArtworkImage(
    artwork: Artwork,
    artworkColorPreference: ArtworkColorPreference,
    shape: Shape,
    highRes: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    highResCache: ArtworkCache? = null,
    suppressLoading: Boolean = false,
) {
    val context = LocalContext.current
    val darkTheme = LocalDarkTheme.current
    val requestKey =
        remember(artwork, highRes) {
            (artwork as? Artwork.Track)?.track?.let {
                "${it.id}|${it.path}|${it.version}|highRes=$highRes"
            }
        }
    var image by
        remember(artwork, highRes) {
            mutableStateOf(
                if (highRes && highResCache != null && artwork is Artwork.Track) {
                    highResCache.get(artwork.track)?.value?.asImageBitmap()
                        ?: requestKey?.let { ArtworkMemoryCache.get(it)?.asImageBitmap() }
                } else {
                    requestKey?.let { ArtworkMemoryCache.get(it)?.asImageBitmap() }
                }
            )
        }
    val icon =
        when (artwork) {
            is Artwork.Track -> Icons.Outlined.MusicNote
            is Artwork.Icon -> artwork.icon
        }
    val imageVisibility = remember(artwork, highRes) { Animatable(if (image == null) 0f else 1f) }

    LaunchedEffect(artwork, highRes, suppressLoading) {
        if (!suppressLoading && artwork is Artwork.Track && image == null && requestKey != null) {
            val loaded =
                withContext(Dispatchers.IO) {
                    ArtworkMemoryCache.getOrPut(requestKey) {
                        if (highRes && highResCache != null) {
                            highResCache
                                .getOrPut(artwork.track) {
                                    Boxed(
                                        loadArtwork(
                                            context,
                                            artwork.track.id,
                                            artwork.track.path,
                                            true,
                                        )
                                    )
                                }
                                .value
                        } else {
                            loadArtwork(context, artwork.track.id, artwork.track.path, highRes)
                        }
                    }
                }
            image = loaded?.asImageBitmap()
            if (image != null) {
                imageVisibility.animateTo(1f, emphasizedStandard())
            }
        }
    }

    Box(modifier = modifier.clip(shape)) {
        if (imageVisibility.value < 1) {
            val color =
                remember(artwork, artworkColorPreference) {
                    artwork.getColor(artworkColorPreference)
                }
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            if (darkTheme) lerp(color, Color.Black, 0.4f)
                            else lerp(color, Color.White, 0.9f)
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.fillMaxSize(0.5f))
            }
        }
        if (image != null) {
            Image(
                painter = BitmapPainter(image!!),
                contentDescription = null,
                alpha = imageVisibility.value,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
