package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.theme.pressScale
import com.biaruuu.kiro.ui.theme.rememberUi
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One row of dark pill chips (the picked one is white) that scrolls sideways. The picked chip centres
 * itself, and round "<" ">" buttons appear on their own when more chips are off screen.
 */
@Composable
fun ChipRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = rememberUi()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lefts = remember(labels.size) { mutableStateListOf<Float>().apply { repeat(labels.size) { add(0f) } } }
    val widths = remember(labels.size) { mutableStateListOf<Float>().apply { repeat(labels.size) { add(0f) } } }
    var viewport by remember { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0))

    LaunchedEffect(index, viewport, widths.getOrNull(index)) {
        if (viewport > 0 && (widths.getOrNull(index) ?: 0f) > 0f) {
            scroll.animateScrollTo((lefts[index] + widths[index] / 2f - viewport / 2f).roundToInt().coerceAtLeast(0))
        }
    }
    val canLeft = scroll.value > 8
    val canRight = scroll.value < scroll.maxValue - 8

    @Composable
    fun Arrow(left: Boolean) {
        val source = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .padding(horizontal = ui.dp(8f))
                .pressScale(source)
                .size(ui.dp(36f))
                .clip(CircleShape)
                .background(Color(0xFF232326))
                .clickable(interactionSource = source, indication = null) {
                    scope.launch {
                        val step = (viewport * 0.6f).roundToInt()
                        scroll.animateScrollTo((scroll.value + if (left) -step else step).coerceIn(0, scroll.maxValue))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.fluent_chevron_down_regular),
                contentDescription = if (left) "Scroll left" else "Scroll right",
                tint = Color.White,
                modifier = Modifier.size(ui.dp(20f)).rotate(if (left) 90f else -90f),
            )
        }
    }

    Box(modifier = modifier.fillMaxWidth().height(ui.dp(52f)).onSizeChanged { viewport = it.width }) {
        Row(
            modifier = Modifier.fillMaxSize().horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(ui.dp(16f)))
            labels.forEachIndexed { i, label ->
                val picked = i == index
                val source = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned {
                            if (i < lefts.size) {
                                lefts[i] = it.positionInParent().x
                                widths[i] = it.size.width.toFloat()
                            }
                        }
                        .pressScale(source)
                        .height(ui.dp(40f))
                        .clip(RoundedCornerShape(50))
                        .background(if (picked) Color.White else Color(0xFF1A1A1C))
                        .clickable(interactionSource = source, indication = null) { onSelect(i) }
                        .padding(horizontal = ui.dp(22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (picked) Color.Black else Color(0xFFC4C4CA),
                        fontSize = ui.sp(15f),
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(if (i == labels.lastIndex) ui.dp(16f) else ui.dp(8f)))
            }
        }
        if (canLeft) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent))
                    .padding(end = ui.dp(26f)),
                contentAlignment = Alignment.CenterStart,
            ) { Arrow(left = true) }
        }
        if (canRight) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(0f to Color.Transparent, 0.45f to Color.Black, 1f to Color.Black))
                    .padding(start = ui.dp(26f)),
                contentAlignment = Alignment.CenterEnd,
            ) { Arrow(left = false) }
        }
    }
}
