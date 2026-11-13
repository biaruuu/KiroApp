package com.biaruuu.kiro.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.R
import com.biaruuu.kiro.core.NativeCore
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.theme.Dimens
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.LocalDimens
import com.biaruuu.kiro.ui.theme.WindowClass
import kotlinx.coroutines.delay

/** First sentence of the description, capped so it fits on one or two lines. */
private fun tagline(description: String?): String {
    val text = NativeCore.cleanDescription(description).replace('\n', ' ').trim()
    if (text.isEmpty()) return ""
    val end = text.indexOfFirst { it == '.' || it == '!' || it == '?' }
    val sentence = if (end in 20..140) text.substring(0, end + 1) else text
    return if (sentence.length > 140) sentence.take(137).trimEnd() + "..." else sentence
}

/** "8.7 · TV · 2026 · Action, Fantasy" */
private fun meta(anime: Anime): String {
    val parts = listOfNotNull(
        NativeCore.scoreLabel(anime.averageScore),
        anime.format?.replace('_', ' '),
        anime.seasonYear.takeIf { it > 0 }?.toString(),
        anime.genres.take(2).joinToString(", ").ifEmpty { null },
    )
    return parts.joinToString("  ·  ")
}

@Composable
fun HeroCarousel(
    heroes: List<Anime>,
    onPlay: (Anime) -> Unit,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDimens.current
    val wide = dims.windowClass != WindowClass.Compact
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val pagerState = rememberPagerState(pageCount = { heroes.size })

    // Auto-advance. Keyed on the current page so a manual swipe restarts the timer.
    LaunchedEffect(pagerState.currentPage, heroes.size) {
        if (heroes.size > 1) {
            delay(5000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % heroes.size)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Height follows the width, but never takes more than most of the screen
        // (matters in landscape, where a wide hero would otherwise overflow).
        val height = minOf(maxWidth / dims.heroAspect, (screenHeightDp * 0.88f).dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                HeroPage(anime = heroes[page], wide = wide, dims = dims, onPlay = onPlay)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = dims.gutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "KIRO",
                    color = Color.White,
                    fontSize = dims.wordmark,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                IconButton(onClick = onNotifications) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bell),
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(heroes.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    anime: Anime,
    wide: Boolean,
    dims: Dimens,
    onPlay: (Anime) -> Unit,
) {
    val line = remember(anime.id) { tagline(anime.description) }
    val details = remember(anime.id) { meta(anime) }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            // Wide screens prefer AniList's landscape banner; phones use the portrait cover.
            model = if (wide) (anime.bannerUrl ?: anime.heroUrl) else anime.heroUrl,
            contentDescription = anime.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (wide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to KiroBackground.copy(alpha = 0.96f),
                            0.45f to KiroBackground.copy(alpha = 0.78f),
                            1.0f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.20f to Color.Transparent,
                            0.80f to Color.Transparent,
                            1.0f to KiroBackground,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.52f)
                    .padding(start = dims.gutter, end = 16.dp, top = 64.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = anime.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = dims.heroTitle,
                    lineHeight = dims.heroTitle * 1.15f,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = dims.heroTagline,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(18.dp))
                PlayButton(onClick = { onPlay(anime) })
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.50f),
                            0.22f to Color.Transparent,
                            0.50f to Color.Transparent,
                            0.85f to KiroBackground.copy(alpha = 0.92f),
                            1.0f to KiroBackground,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = anime.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = dims.heroTitle,
                    lineHeight = dims.heroTitle * 1.15f,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = dims.heroTagline,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(18.dp))
                PlayButton(onClick = { onPlay(anime) })
            }
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        modifier = Modifier
            .width(132.dp)
            .height(44.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Play", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
