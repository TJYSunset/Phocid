@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.R
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.ui.theme.emphasizedStandard

@Immutable
sealed class PlayerScreenTopBar {
    @Composable
    abstract fun Compose(
        containerColor: Color,
        contentColor: Color,
        lyricsViewVisibility: Boolean,
        lyricsAutoScrollButtonVisibility: Boolean,
        lyricsButtonEnabled: Boolean,
        overlayVisibility: Boolean,
        onBack: () -> Unit,
        onEnableLyricsViewAutoScroll: () -> Unit,
        onToggleLyricsView: () -> Unit,
    )
}

@Immutable
object PlayerScreenTopBarDefaultOverlay : PlayerScreenTopBar() {
    @Composable
    override fun Compose(
        containerColor: Color,
        contentColor: Color,
        lyricsViewVisibility: Boolean,
        lyricsAutoScrollButtonVisibility: Boolean,
        lyricsButtonEnabled: Boolean,
        overlayVisibility: Boolean,
        onBack: () -> Unit,
        onEnableLyricsViewAutoScroll: () -> Unit,
        onToggleLyricsView: () -> Unit,
    ) {
        AnimatedVisibility(
            overlayVisibility,
            enter = fadeIn(emphasizedStandard()),
            exit = fadeOut(emphasizedStandard()),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height((48 + 8 * 2).dp)) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = INACTIVE_ALPHA),
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = Strings[R.string.commons_back],
                    )
                }
                Row(modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp)) {
                    AnimatedVisibility(
                        lyricsAutoScrollButtonVisibility,
                        enter = fadeIn(emphasizedStandard()),
                        exit = fadeOut(emphasizedStandard()),
                    ) {
                        FilledTonalIconButton(
                            onClick = onEnableLyricsViewAutoScroll,
                            colors =
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = containerColor,
                                    contentColor = contentColor,
                                ),
                        ) {
                            Icon(
                                Icons.Outlined.VerticalAlignCenter,
                                contentDescription = Strings[R.string.player_lyrics_auto_scroll],
                            )
                        }
                    }
                    AnimatedVisibility(
                        lyricsButtonEnabled,
                        enter = fadeIn(emphasizedStandard()),
                        exit = fadeOut(emphasizedStandard()),
                    ) {
                        FilledTonalIconButton(
                            onClick = onToggleLyricsView,
                            colors =
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = containerColor,
                                    contentColor = contentColor,
                                ),
                        ) {
                            AnimatedContent(lyricsViewVisibility) {
                                if (it) {
                                    Icon(
                                        Icons.Outlined.Image,
                                        contentDescription = Strings[R.string.player_close_lyrics],
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Subtitles,
                                        contentDescription = Strings[R.string.player_lyrics],
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Immutable
object PlayerScreenTopBarDefaultStandalone : PlayerScreenTopBar() {
    @Composable
    override fun Compose(
        containerColor: Color,
        contentColor: Color,
        lyricsViewVisibility: Boolean,
        lyricsAutoScrollButtonVisibility: Boolean,
        lyricsButtonEnabled: Boolean,
        overlayVisibility: Boolean,
        onBack: () -> Unit,
        onEnableLyricsViewAutoScroll: () -> Unit,
        onToggleLyricsView: () -> Unit,
    ) {
        // Hack to remove animation delay
        // https://stackoverflow.com/q/77928923
        key(containerColor) {
            // Explicit height to prevent incorrect measured height in outer layout
            // Setting the height as a modifier on TopAppBar will result in displaced title
            Box(modifier = Modifier.height(64.dp)) {
                TopAppBar(
                    title = { SingleLineText(Strings[R.string.player_now_playing]) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Strings[R.string.commons_back],
                            )
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            lyricsAutoScrollButtonVisibility,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(onClick = onEnableLyricsViewAutoScroll) {
                                Icon(
                                    Icons.Outlined.VerticalAlignCenter,
                                    contentDescription = Strings[R.string.player_lyrics_auto_scroll],
                                )
                            }
                        }
                        IconButton(enabled = lyricsButtonEnabled, onClick = onToggleLyricsView) {
                            AnimatedContent(lyricsViewVisibility) {
                                if (it) {
                                    Icon(
                                        Icons.Outlined.Image,
                                        contentDescription = Strings[R.string.player_close_lyrics],
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Subtitles,
                                        contentDescription = Strings[R.string.player_lyrics],
                                    )
                                }
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = containerColor,
                            scrolledContainerColor = containerColor,
                            navigationIconContentColor = contentColor,
                            titleContentColor = contentColor,
                            actionIconContentColor = contentColor,
                        ),
                )
            }
        }
    }
}
