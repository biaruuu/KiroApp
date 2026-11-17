package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.AniListRepository
import com.biaruuu.kiro.data.TmdbMeta

/** Title logo, score and age rating for [anime]; null until TMDB answers (or when it has none). */
@Composable
fun rememberTmdbMeta(anime: Anime): State<TmdbMeta?> =
    produceState<TmdbMeta?>(initialValue = AniListRepository.cachedMeta(anime.id), anime.id) {
        if (value == null) {
            value = try {
                AniListRepository.tmdbMeta(anime)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

/**
 * The anime's own title artwork (transparent logo from TMDB). [fallback] is shown while it loads
 * or when TMDB has no logo, so there is never an empty gap.
 */
@Composable
fun TitleLogo(
    logoUrl: String?,
    contentDescription: String,
    alignment: Alignment = Alignment.CenterStart,
    maxHeight: androidx.compose.ui.unit.Dp = 72.dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    if (logoUrl.isNullOrEmpty()) {
        fallback()
        return
    }
    AsyncImage(
        model = logoUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        alignment = alignment,
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
    )
}

/** The gold "IMDB 8.5" tile and the white "PARENTAL GUIDANCE U/A 13+" tile. */
@Composable
fun RatingBadges(meta: TmdbMeta?, modifier: Modifier = Modifier) {
    if (meta == null) return
    val score = meta.ratingLabel
    val cert = meta.certification
    if (score.isEmpty() && cert.isNullOrEmpty()) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (score.isNotEmpty()) {
            Badge(top = "IMDB", bottom = score, topBg = Color(0xFFB8860B), topFg = Color.Black, bottomBg = Color(0xFFF5C518))
            Spacer(Modifier.width(10.dp))
        }
        if (!cert.isNullOrEmpty()) {
            Badge(top = "PARENTAL GUIDANCE", bottom = cert, topBg = Color(0xFF111111), topFg = Color.White, bottomBg = Color(0xFFF2F2F2))
        }
    }
}

@Composable
private fun Badge(top: String, bottom: String, topBg: Color, topFg: Color, bottomBg: Color) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(bottomBg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.background(topBg), contentAlignment = Alignment.Center) {
            Text(
                top,
                color = topFg,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Text(
            bottom,
            color = Color.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}


/**
 * Full-bleed artwork for a hero. AniList's cover is shown straight away and is swapped for the
 * full-resolution TMDB poster as soon as one is found, so the picture ends up sharp on any screen.
 */
@androidx.compose.runtime.Composable
fun HdArtwork(anime: Anime, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, contentDescription: String? = null) {
    val meta by rememberTmdbMeta(anime)
    coil.compose.AsyncImage(
        model = meta?.posterUrl ?: anime.heroUrl,
        contentDescription = contentDescription,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier,
    )
}
