package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.theme.KiroAccent
import com.biaruuu.kiro.ui.theme.KiroSurface
import com.biaruuu.kiro.ui.theme.LocalDimens

private fun parseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Poster shape used everywhere: slightly wider than 2:3, as in the reference. */
const val PosterAspect = 0.705f

/** Short tag shown in the poster corner, like the "ORIGINAL" tag in the design. */
private fun statusTag(status: String?): String? = when (status) {
    "RELEASING" -> "AIRING"
    "NOT_YET_RELEASED" -> "UPCOMING"
    else -> null
}

/** Portrait poster with the title underneath, used in rows and in the search grid. */
@Composable
fun PosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 2,
    tagAtBottom: Boolean = false,
    /** Slimmer look used by the search grid: flatter poster, 13 sp title. */
    compact: Boolean = false,
) {
    val placeholder = remember(anime.id) { parseColor(anime.coverColor) ?: KiroSurface }
    val tag = remember(anime.id) { statusTag(anime.status) }

    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (compact) 0.707f else PosterAspect)
                .clip(RoundedCornerShape(12.dp))
                .background(placeholder.copy(alpha = 0.3f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (tag != null) {
                // Small pill like the reference. Home rows pin it to the top-right corner,
                // the search grid floats it at the bottom-right.
                val pillShape = RoundedCornerShape(50)
                Text(
                    text = tag,
                    color = Color.White,
                    fontSize = if (compact) 10.sp else 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(if (tagAtBottom) Alignment.BottomEnd else Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(pillShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = if (compact) 13.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            lineHeight = if (compact) 18.sp else 21.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A titled horizontal row of poster cards. Card size follows the screen width. */
@Composable
fun AnimeRow(
    title: String,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDimens.current
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontSize = dims.sectionTitle,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = dims.gutter),
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(dims.cardSpacing),
        ) {
            items(items, key = { it.id }, contentType = { "poster" }) { anime ->
                PosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    compact = true,
                    modifier = Modifier.width(dims.cardWidth),
                )
            }
        }
    }
}

/** "Top 10" row: a big outlined number beside each poster, sized from the screen width. */
@Composable
fun RankedRow(
    title: String,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = com.biaruuu.kiro.ui.theme.rememberUi()
    val posterWidth = ui.dp(122f)
    val posterHeight = posterWidth * 1.5f
    val stroke = with(androidx.compose.ui.platform.LocalDensity.current) { 1.6.dp.toPx() }
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = ui.sp(19f),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = ui.dp(20f)),
        )
        Spacer(Modifier.height(ui.dp(14f)))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ui.dp(16f)),
            horizontalArrangement = Arrangement.spacedBy(ui.dp(4f)),
        ) {
            itemsIndexed(items, key = { _, anime -> anime.id }, contentType = { _, _ -> "ranked" }) { index, anime ->
                val rank = index + 1
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.height(posterHeight), contentAlignment = Alignment.Center) {
                        Text(
                            text = "$rank",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF8E8E93),
                                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = stroke,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                ),
                                letterSpacing = if (rank >= 10) ui.sp(-14f) else ui.sp(0f),
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                            ),
                            fontSize = ui.sp(172f),
                            lineHeight = ui.sp(172f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Column(modifier = Modifier.width(posterWidth).clickable { onAnimeClick(anime) }) {
                        AsyncImage(
                            model = anime.posterUrl,
                            contentDescription = anime.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(posterHeight)
                                .clip(RoundedCornerShape(ui.dp(10f)))
                                .background(Color(0xFF1C1C1F)),
                        )
                        Text(
                            text = anime.displayTitle,
                            color = Color(0xFF8E8E93),
                            fontSize = ui.sp(12f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = ui.dp(8f)),
                        )
                    }
                }
            }
        }
    }
}
