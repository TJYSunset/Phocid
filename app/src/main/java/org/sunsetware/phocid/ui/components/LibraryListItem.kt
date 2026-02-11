package org.sunsetware.phocid.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.R
import org.sunsetware.phocid.globals.Strings
import org.sunsetware.phocid.ui.theme.AnimatedContentEnter
import org.sunsetware.phocid.ui.theme.AnimatedContentExit
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.contentColorVariant
import org.sunsetware.phocid.ui.theme.emphasizedStandard

@Composable
inline fun LibraryListItemHorizontal(
    title: String,
    subtitle: String,
    crossinline lead: @Composable () -> Unit,
    crossinline actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 72.dp,
    deemphasized: Boolean = false,
    marquee: Boolean = false,
    selected: Boolean = false,
    highlighted: Boolean = false,
    bordered: Boolean = false,
    dragIndicator: Boolean = false,
) {
    val layoutDirection = LocalLayoutDirection.current
    val primaryAlpha by animateFloatAsState(if (deemphasized) INACTIVE_ALPHA else 1f)
    val backgroundAlpha by animateFloatAsState(if (selected || highlighted) 1f else 0f)
    val supportingContentColor = contentColorVariant()
    Row(
        modifier =
            modifier
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = backgroundAlpha)
                )
                .let { if (bordered) it.border(4.dp, MaterialTheme.colorScheme.secondary) else it }
                .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides supportingContentColor) {
            Box(modifier = Modifier.padding(end = 16.dp).size(40.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    dragIndicator,
                    enter = fadeIn(emphasizedStandard()),
                    exit = fadeOut(emphasizedStandard()),
                    modifier =
                        Modifier.align(Alignment.CenterStart).graphicsLayer {
                            translationX =
                                -28.dp.toPx() *
                                    (if (layoutDirection == LayoutDirection.Ltr) 1 else -1)
                        },
                ) {
                    Icon(Icons.Filled.DragHandle, null)
                }
                AnimatedContent(
                    targetState = selected,
                    modifier = Modifier.align(Alignment.Center).alpha(primaryAlpha),
                ) { animatedSelected ->
                    if (animatedSelected) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription =
                                    Strings[R.string.list_multi_select_item_selected],
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    } else {
                        lead()
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier =
                    Modifier.alpha(primaryAlpha).run { if (marquee) basicMarquee() else this },
            ) {
                SingleLineText(
                    text = it,
                    style = Typography.bodyLarge,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Don't apply marquee to subtitle, as it rarely exceeds the maximum length, and will
            // become out-of-sync with the title
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.alpha(primaryAlpha),
            ) {
                SingleLineText(
                    text = it,
                    style = Typography.bodySmall,
                    color = supportingContentColor,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        CompositionLocalProvider(LocalContentColor provides supportingContentColor) {
            Row { actions() }
        }
    }
}

@Composable
inline fun LibraryListItemCard(
    title: String,
    subtitle: String,
    color: Color,
    shape: Shape,
    crossinline image: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val contentColor = color.contentColor()
    Card(
        shape = shape,
        colors = CardColors(color, contentColor, color, contentColor),
        modifier = modifier,
    ) {
        val secondaryColor = contentColorVariant()
        Column {
            Box(
                modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true),
                contentAlignment = Alignment.Center,
            ) {
                image()
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 1 - INACTIVE_ALPHA))
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = AnimatedContentEnter,
                    exit = AnimatedContentExit,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = Strings[R.string.list_multi_select_item_selected],
                        modifier = Modifier.size(48.dp),
                        tint = Color.White,
                    )
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                SingleLineText(
                    text = title,
                    style = Typography.bodyLarge,
                    overflow = TextOverflow.Ellipsis,
                )
                SingleLineText(
                    text = subtitle,
                    style = Typography.bodySmall,
                    color = secondaryColor,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
inline fun LibraryListItemCompactCard(
    title: String,
    subtitle: String,
    shape: Shape,
    crossinline image: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryColor = contentColorVariant()
    Card(shape = shape, modifier = modifier) {
        image()
        Column(modifier = Modifier.padding(16.dp)) {
            SingleLineText(
                text = title,
                style = Typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
            )
            SingleLineText(
                text = subtitle,
                style = Typography.bodySmall,
                color = secondaryColor,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LibraryListHeader(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = Typography.labelMedium)
    }
}
