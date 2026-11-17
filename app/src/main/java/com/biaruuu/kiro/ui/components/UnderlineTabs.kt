package com.biaruuu.kiro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The tab strip used on Your Library: bold labels with a white line that slides under the chosen
 * one while the pager is dragged.
 */
@Composable
fun UnderlineTabs(labels: List<String>, pager: PagerState, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val lefts = remember(labels.size) { mutableStateListOf<Float>().apply { repeat(labels.size) { add(0f) } } }
    val widths = remember(labels.size) { mutableStateListOf<Float>().apply { repeat(labels.size) { add(0f) } } }
    var viewport by remember { mutableIntStateOf(0) }

    val cur = pager.currentPage.coerceIn(0, labels.lastIndex)
    val frac = pager.currentPageOffsetFraction
    val target = (if (frac > 0f) cur + 1 else if (frac < 0f) cur - 1 else cur).coerceIn(0, labels.lastIndex)
    val p = abs(frac).coerceIn(0f, 1f)
    val lineX = lerp(lefts[cur], lefts[target], p)
    val lineW = lerp(widths[cur], widths[target], p)

    LaunchedEffect(cur, viewport, widths[cur]) {
        if (viewport > 0 && widths[cur] > 0f) {
            scroll.animateScrollTo((lefts[cur] + widths[cur] / 2f - viewport / 2f).roundToInt().coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scroll),
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Spacer(Modifier.width(16.dp))
            labels.forEachIndexed { index, label ->
                val color by animateColorAsState(if (cur == index) Color.White else Color(0xFF8C8C91), label = "tab-color")
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            lefts[index] = it.positionInParent().x
                            widths[index] = it.size.width.toFloat()
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.width(if (index == labels.lastIndex) 16.dp else 24.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset(lineX.roundToInt(), 0) }
                .width(with(density) { lineW.toDp() })
                .height(2.dp)
                .background(Color.White),
        )
    }
}
