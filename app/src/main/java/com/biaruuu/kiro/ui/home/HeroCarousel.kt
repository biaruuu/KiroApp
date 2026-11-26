package com.biaruuu.kiro.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.biaruuu.kiro.ui.theme.pressScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    onSchedule: () -> Unit = {},
) {
    val dims = LocalDimens.current
    val ui = com.biaruuu.kiro.ui.theme.rememberUi()
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
        val height = if (wide) minOf(maxWidth / dims.heroAspect + statusBar, (screenHeightDp * 0.88f).dp)
        else minOf(maxWidth * 1.36f, (screenHeightDp * 0.66f).dp)

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
                    .padding(horizontal = ui.dp(20f), vertical = ui.dp(10f)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(ui.dp(44f))
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1F).copy(alpha = 0.8f))
                        .clickable(onClick = onSchedule),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fluent_calendar_regular),
                        contentDescription = "Schedule",
                        tint = Color.White,
                        modifier = Modifier.size(ui.dp(20f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = ui.dp(58f), height = ui.dp(44f))
                        .clip(RoundedCornerShape(ui.dp(14f)))
                        .background(Color(0xFF1C1C1F).copy(alpha = 0.8f))
                        .clickable(onClick = onNotifications),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fluent_alert_regular),
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(ui.dp(20f)),
                    )
                    if (hasUnread) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = ui.dp(9f), end = ui.dp(16f))
                                .size(ui.dp(8f))
                                .clip(CircleShape)
                                .background(Color(0xFFFF4D4F)),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(if (wide) Alignment.BottomStart else Alignment.BottomCenter)
                    .padding(bottom = ui.dp(18f), start = if (wide) dims.gutter else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(ui.dp(6f)),
            ) {
                repeat(heroes.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .height(ui.dp(5f))
                            .width(if (selected) ui.dp(16f) else ui.dp(5f))
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

/** Phone: full-bleed cover art, a fade into the page, then logo, genres, blurb and two buttons. */
@Composable
private fun PhoneHeroPage(
    anime: Anime,
    dims: Dimens,
    onPlay: (Anime) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ui = com.biaruuu.kiro.ui.theme.rememberUi()
    var inList by remember(anime.id) { mutableStateOf(com.biaruuu.kiro.data.MyListStore.isInList(context, anime.id)) }
    val line = remember(anime.id) {
        (anime.genres.take(3) + listOfNotNull(anime.seasonYear.takeIf { it > 0 }?.toString()))
            .joinToString("  •  ")
    }
    val blurb = remember(anime.id) { NativeCore.cleanDescription(anime.description).replace('\n', ' ').trim() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        com.biaruuu.kiro.ui.components.HdArtwork(anime, Modifier.fillMaxSize(), anime.displayTitle)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.55f),
                        0.65f to Color.Black.copy(alpha = 0.93f),
                        1f to Color.Black,
                    ),
                ),
        )
        val meta by rememberTmdbMeta(anime)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = ui.dp(20f))
                .padding(bottom = ui.dp(46f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TitleLogo(
                logoUrl = meta?.logoUrl,
                contentDescription = anime.displayTitle,
                alignment = Alignment.Center,
                maxHeight = ui.dp(80f),
                modifier = Modifier.fillMaxWidth(0.62f),
            ) {
                Text(
                    text = anime.displayTitle,
                    style = TextStyle(shadow = TextShadow),
                    fontSize = ui.sp(26f),
                    lineHeight = ui.sp(32f),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(ui.dp(12f)))
                Text(
                    text = line,
                    color = Color(0xFFC9C9D0),
                    fontSize = ui.sp(13f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (blurb.isNotEmpty()) {
                Spacer(Modifier.height(ui.dp(10f)))
                Text(
                    text = blurb,
                    color = Color(0xFFA6A6AE),
                    fontSize = ui.sp(12.5f),
                    lineHeight = ui.sp(18f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(ui.dp(22f)))
            Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(10f)), modifier = Modifier.fillMaxWidth()) {
                HeroButton(
                    label = "View episodes",
                    icon = R.drawable.fluent_play_filled,
                    filled = true,
                    onClick = { onPlay(anime) },
                    ui = ui,
                    modifier = Modifier.weight(1.27f),
                )
                HeroButton(
                    label = "Favourites",
                    icon = if (inList) R.drawable.fluent_checkmark_regular else R.drawable.fluent_add_regular,
                    filled = false,
                    onClick = {
                        if (!com.biaruuu.kiro.data.CloudSync.allowed(context)) {
                            com.biaruuu.kiro.data.CloudSync.requireLogin(context)
                        } else {
                            inList = com.biaruuu.kiro.data.MyListStore.toggle(context, anime)
                            com.biaruuu.kiro.ui.i18n.I18nToast.make(
                                context,
                                if (inList) "Added to Favourites" else "Removed from Favourites",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    ui = ui,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: Int,
    filled: Boolean,
    onClick: () -> Unit,
    ui: com.biaruuu.kiro.ui.theme.Ui,
    modifier: Modifier = Modifier,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(source)
            .height(ui.dp(44f))
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color(0xFF3C3C41).copy(alpha = 0.7f))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (filled) Color.Black else Color.White,
            modifier = Modifier.size(ui.dp(18f)),
        )
        Spacer(Modifier.width(ui.dp(7f)))
        Text(
            label,
            color = if (filled) Color.Black else Color.White,
            fontSize = ui.sp(15f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Tablet / landscape: solid text panel on the left, artwork on the right. No gradients. */
@Composable
private fun WideHeroPage(
    anime: Anime,
    dims: Dimens,
    onPlay: (Anime) -> Unit,
) {
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
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        modifier = Modifier
            .width(108.dp)
            .height(38.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.fluent_play_filled),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("Play", fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}
