package com.biaruuu.kiro.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.ui.components.rememberTmdbMeta
import com.biaruuu.kiro.ui.components.TitleLogo
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import com.biaruuu.kiro.R
import com.biaruuu.kiro.core.NativeCore
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.ui.theme.Dimens
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.LocalDimens
import com.biaruuu.kiro.ui.theme.WindowClass
import kotlinx.coroutines.delay

private const val AUTO_ADVANCE_MS = 5000L

/** Soft shadow so white text stays readable on any artwork without a gradient overlay. */
private val TextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 2f),
    blurRadius = 10f,
)

/** First sentence of the description, capped so it fits on one or two lines. */
private fun tagline(description: String?): String {
    val text = NativeCore.cleanDescription(description).replace('\n', ' ').trim()
    if (text.isEmpty()) return ""
    val end = text.indexOfFirst { it == '.' || it == '!' || it == '?' }
    val sentence = if (end in 20..140) text.substring(0, end + 1) else text
    return if (sentence.length > 140) sentence.take(137).trimEnd() + "..." else sentence
}

/** "8.7  ·  TV  ·  2026  ·  Action, Fantasy" */
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
    hasUnread: Boolean = false,
) {
    val dims = LocalDimens.current
    val wide = dims.windowClass != WindowClass.Compact
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pagerState = rememberPagerState(pageCount = { heroes.size })

    // Auto-advance. Keyed on the current page so a manual swipe restarts the timer, and it
    // never fights a swipe that is still in progress.
    LaunchedEffect(pagerState.currentPage, heroes.size) {
        if (heroes.size > 1) {
            delay(AUTO_ADVANCE_MS)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(
                    page = (pagerState.currentPage + 1) % heroes.size,
                    animationSpec = tween(durationMillis = 600),
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // The hero runs behind the status bar, so add it on top of the design height. Never
        // take more than most of the screen (matters in landscape).
        val height = minOf(maxWidth / dims.heroAspect + statusBar, (screenHeightDp * 0.88f).dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1, // preload the next banner so swipes never show a blank page
                key = { heroes[it].id },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (wide) {
                    WideHeroPage(anime = heroes[page], dims = dims, onPlay = onPlay)
                } else {
                    PhoneHeroPage(anime = heroes[page], dims = dims, onPlay = onPlay)
                }
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
                    style = TextStyle(shadow = TextShadow),
                    color = Color.White,
                    fontSize = dims.wordmark,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                IconButton(onClick = onNotifications) {
                    // Plain white bell like the reference. A faint offset copy underneath keeps
                    // it readable on bright artwork without putting a dark disc behind it.
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.fluent_alert_regular),
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(26.dp)
                                .offset(y = 1.dp),
                        )
                        Icon(
                            painter = painterResource(R.drawable.fluent_alert_regular),
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                        if (hasUnread) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 1.dp, y = (-1).dp)
                                    .size(10.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFFFF4D4F)),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                    .padding(bottom = 14.dp, start = if (wide) dims.gutter else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(heroes.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

/** Phone: full-bleed cover art with a flat dim layer and centred text. No gradients. */
@Composable
private fun PhoneHeroPage(
    anime: Anime,
    dims: Dimens,
    onPlay: (Anime) -> Unit,
) {
    val line = remember(anime.id) { tagline(anime.description) }

    Box(modifier = Modifier.fillMaxSize().background(KiroBackground)) {
        com.biaruuu.kiro.ui.components.HdArtwork(anime, Modifier.fillMaxSize(), anime.displayTitle)
        // One flat, even dim over the artwork keeps the white title readable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f)),
        )
        // Soft fade behind the title, tagline and Play button into the page background.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to KiroBackground.copy(alpha = 0.55f),
                        1f to KiroBackground,
                    ),
                ),
        )
        val meta by rememberTmdbMeta(anime)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TitleLogo(
                logoUrl = meta?.logoUrl,
                contentDescription = anime.displayTitle,
                alignment = Alignment.Center,
                maxHeight = 96.dp,
                modifier = Modifier.fillMaxWidth(0.72f),
            ) {
                Text(
                    text = anime.displayTitle,
                    style = TextStyle(shadow = TextShadow),
                    fontSize = dims.heroTitle,
                    lineHeight = dims.heroTitle * 1.3f,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = line,
                    style = TextStyle(shadow = TextShadow),
                    fontSize = dims.heroTagline,
                    color = Color.White,
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

/** Tablet / landscape: solid text panel on the left, artwork on the right. No gradients. */
@Composable
private fun WideHeroPage(
    anime: Anime,
    dims: Dimens,
    onPlay: (Anime) -> Unit,
) {
    val line = remember(anime.id) { tagline(anime.description) }
    val details = remember(anime.id) { meta(anime) }

    Row(modifier = Modifier.fillMaxSize().background(KiroBackground)) {
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight()
                .padding(start = dims.gutter, end = 24.dp, top = 64.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = anime.displayTitle,
                fontSize = dims.heroTitle,
                lineHeight = dims.heroTitle * 1.2f,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = line,
                    fontSize = dims.heroTagline,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))
            PlayButton(onClick = { onPlay(anime) })
        }
        com.biaruuu.kiro.ui.components.HdArtwork(
            anime,
            Modifier.weight(0.54f).fillMaxHeight(),
            anime.displayTitle,
        )
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        modifier = Modifier
            .width(116.dp)
            .height(40.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.fluent_play_filled),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Play", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
