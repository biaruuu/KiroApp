package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
                .clip(RoundedCornerShape(8.dp))
                .background(placeholder.copy(alpha = 0.3f)),
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
                val pillShape = if (tagAtBottom) RoundedCornerShape(4.dp) else RoundedCornerShape(4.dp)
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
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = if (compact) 13.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onBackground,
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
        Spacer(Modifier.height(12.dp))
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

/**
 * "Top 10" row: a big outlined rank number sits to the left of each poster, like the
 * Trending Now row in the reference. Titles are one line so every number lines up.
 */
@Composable
fun RankedRow(
    title: String,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDimens.current
    val density = LocalDensity.current

    // Numeral size follows the poster so the pair scales together on every screen.
    val numberSize = with(density) { (dims.cardWidth * 0.62f).toSp() }
    val singleDigitWidth = dims.cardWidth * 0.36f
    val strokeWidth = with(density) { 2.5.dp.toPx() }

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
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(dims.cardSpacing + 8.dp),
        ) {
            itemsIndexed(items, key = { _, anime -> anime.id }, contentType = { _, _ -> "ranked" }) { index, anime ->
                val rank = index + 1
                val numberWidth = if (rank >= 10) singleDigitWidth * 1.8f else singleDigitWidth
                Box(modifier = Modifier.width(numberWidth + dims.cardWidth)) {
                    PosterCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime) },
                        titleMaxLines = 1,
                        compact = true,
                        modifier = Modifier
                            .padding(start = numberWidth)
                            .width(dims.cardWidth),
                    )
                    Text(
                        text = rank.toString(),
                        style = TextStyle(
                            drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round),
                            fontStyle = FontStyle.Italic,
                        ),
                        color = Color.White,
                        fontSize = numberSize,
                        lineHeight = numberSize,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(y = 14.dp),
                    )
                }
            }
        }
    }
}
