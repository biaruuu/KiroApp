package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.R
import com.biaruuu.kiro.core.NativeCore
import com.biaruuu.kiro.data.Anime
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

/** Portrait poster with the title underneath, used in rows and in the search grid. */
@Composable
fun PosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = remember(anime.id) { parseColor(anime.coverColor) ?: KiroSurface }
    val score = remember(anime.id) { NativeCore.scoreLabel(anime.averageScore) }

    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholder.copy(alpha = 0.35f)),
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (score != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star),
                        contentDescription = null,
                        tint = Color(0xFFFFC53D),
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = score,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = anime.displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = dims.gutter),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = dims.gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { anime ->
                PosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    modifier = Modifier.width(dims.cardWidth),
                )
            }
        }
    }
}
