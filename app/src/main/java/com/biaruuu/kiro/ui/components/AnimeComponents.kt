package com.biaruuu.kiro.ui.components

import com.biaruuu.kiro.ui.theme.KiroText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                .clip(RoundedCornerShape(10.dp))
                .background(KiroSurface)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
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
                    color = KiroText,
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
        Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = if (compact) 12.5.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            lineHeight = if (compact) 17.sp else 21.sp,
            fontWeight = FontWeight.Medium,
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
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
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
 * "Top 10" row: the same quiet poster row as the others, with a small rank pill in the corner of
 * each poster instead of big outlined numerals.
 */
@Composable
fun RankedRow(
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
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
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
            itemsIndexed(items, key = { _, anime -> anime.id }, contentType = { _, _ -> "ranked" }) { index, anime ->
                Box(modifier = Modifier.width(dims.cardWidth)) {
                    PosterCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime) },
                        titleMaxLines = 1,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${index + 1}",
                        color = KiroText,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
