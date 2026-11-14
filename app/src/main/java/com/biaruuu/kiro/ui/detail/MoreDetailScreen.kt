package com.biaruuu.kiro.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.biaruuu.kiro.R
import com.biaruuu.kiro.ui.components.ErrorState
import com.biaruuu.kiro.ui.theme.KiroPurple

private val Bg = Color(0xFF121212)
private val DividerColor = Color(0xFF333335)
private val MutedText = Color(0xFF8E8E93)

/** The full "Detail" page opened from the "More" link. */
@Composable
fun MoreDetailScreen(animeId: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: DetailViewModel = viewModel(key = "detail-$animeId", factory = DetailViewModel.Factory(animeId))
    val state by vm.state.collectAsState()
    val anime = state.base
    val detail = state.detail

    Column(modifier = modifier.fillMaxSize().background(Bg)) {
        // Top bar
        Row(
            modifier = Modifier.statusBarsPadding().fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.fluent_chevron_left_regular),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                "Detail",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))

        when {
            anime == null && state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            anime == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KiroPurple)
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .width(104.dp)
                                .aspectRatio(0.705f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2A2A2C)),
                        ) {
                            AsyncImage(
                                model = anime.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                anime.displayTitle,
                                color = Color.White,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                metaLine(anime, detail),
                                color = MutedText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionTitle("Synopsis")
                    Text(
                        synopsis(anime).ifEmpty { "No synopsis available." },
                        color = Color(0xFFD0D0D4),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )

                    val cast = detail?.cast.orEmpty()
                    if (cast.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Cast")
                        Text(
                            cast.joinToString(", "),
                            color = Color(0xFFD0D0D4),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )
                    }

                    val credits = detail?.let(::creditRows).orEmpty()
                    if (credits.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Credit")
                        credits.forEach { (label, names) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    label,
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(130.dp),
                                )
                                Text(
                                    names,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (detail == null && state.loading) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = KiroPurple, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Text("Source: AniList", color = MutedText, fontSize = 12.sp)
                    Spacer(Modifier.navigationBarsPadding().height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}
