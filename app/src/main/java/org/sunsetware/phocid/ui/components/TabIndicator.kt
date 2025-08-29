@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import org.sunsetware.phocid.utils.coerceInOrMin

@Composable
fun TabIndicatorScope.TabIndicator(pagerState: PagerState) {
    val total = abs(pagerState.targetPage - pagerState.currentPage)
    val traveled = abs(pagerState.currentPageOffsetFraction)
    val morph = (traveled / total).takeIf { it.isFinite() } ?: 0f
    Box(
        modifier =
            Modifier.height(3.dp)
                .tabIndicatorLayout { measurable, constraints, tabPositions ->
                    val settledPosition =
                        tabPositions[pagerState.currentPage.coerceInOrMin(0, tabPositions.size - 1)]
                    val targetPosition =
                        tabPositions[pagerState.targetPage.coerceInOrMin(0, tabPositions.size - 1)]
                    val width =
                        (lerp(settledPosition.contentWidth, targetPosition.contentWidth, morph) -
                                4.dp)
                            .roundToPx()
                    val placeable =
                        measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(
                            lerp(settledPosition.left, targetPosition.left, morph).roundToPx(),
                            constraints.maxHeight - placeable.height,
                        )
                    }
                }
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(3.dp, 3.dp, 0.dp, 0.dp),
                )
    )
}
