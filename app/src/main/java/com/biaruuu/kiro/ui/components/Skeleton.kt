package com.biaruuu.kiro.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.biaruuu.kiro.ui.theme.LocalDimens

/** One shared, flat pulse (no gradient): placeholders breathe between two greys. */
@Composable
private fun pulse(): State<Float> =
    rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse",
    )

/** A grey block that pulses. The alpha is read while drawing, so it never recomposes. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    val alpha = pulse()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .drawBehind { drawRect(Color(0xFF2A2A30), alpha = alpha.value) },
    )
}

/** Home while it loads: hero, then a few poster rows. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val dims = LocalDimens.current
    Column(modifier = modifier.fillMaxSize()) {
        SkeletonBox(Modifier.fillMaxWidth().height(380.dp), radius = 0.dp)
        Spacer(Modifier.height(20.dp))
        repeat(2) {
            SkeletonBox(Modifier.padding(horizontal = dims.gutter).width(150.dp).height(20.dp))
            Spacer(Modifier.height(12.dp))
            PosterRowSkeleton()
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
fun PosterRowSkeleton(count: Int = 6) {
    val dims = LocalDimens.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.cardSpacing),
        userScrollEnabled = false,
    ) {
        items((0 until count).toList()) {
            Column(Modifier.width(dims.cardWidth)) {
                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Spacer(Modifier.height(8.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.8f).height(12.dp), radius = 4.dp)
            }
        }
    }
}

/** Search results while they load: the same 3-column grid of posters. */
@Composable
fun SearchSkeleton(columns: Int, modifier: Modifier = Modifier) {
    val dims = LocalDimens.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dims.gutter),
        horizontalArrangement = Arrangement.spacedBy(dims.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        items((0 until columns * 4).toList()) {
            Column {
                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Spacer(Modifier.height(8.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.85f).height(12.dp), radius = 4.dp)
            }
        }
    }
}

/** Details page while the anime loads: trailer area, title, buttons and a few episode rows. */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 0.dp)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.6f).height(26.dp))
            Spacer(Modifier.height(10.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(14.dp), radius = 4.dp)
            Spacer(Modifier.height(18.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(46.dp))
            Spacer(Modifier.height(20.dp))
            repeat(3) { EpisodeSkeletonRow(); Spacer(Modifier.height(14.dp)) }
        }
    }
}

/** A single episode row placeholder (thumbnail + two lines). */
@Composable
fun EpisodeSkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        SkeletonBox(Modifier.width(132.dp).aspectRatio(16f / 9f), radius = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp), radius = 4.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(11.dp), radius = 4.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.55f).height(11.dp), radius = 4.dp)
        }
    }
}
