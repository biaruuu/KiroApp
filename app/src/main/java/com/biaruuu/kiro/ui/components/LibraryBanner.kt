package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.Designs
import com.biaruuu.kiro.data.NameStyles
import com.biaruuu.kiro.data.Profile

/**
 * The profile card at the top of Your Library: the picked library effect plays behind it, the
 * library decoration sits over it, and the avatar and name rest on the bottom edge.
 */
@Composable
fun LibraryBanner(profile: Profile, name: String, height: Dp = 120.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val remote by Designs.remote.collectAsState()
    val effect = androidx.compose.runtime.remember(profile.libraryEffect, remote) { Designs.find(profile.libraryEffect) }
    val decoration = androidx.compose.runtime.remember(profile.libraryDecoration, remote) { Designs.find(profile.libraryDecoration) }
    Box(modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1A1D))) {
        if (effect != null) AnimatedAsset(effect, Modifier.fillMaxSize(), crop = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        if (decoration != null) AnimatedAsset(decoration, Modifier.fillMaxSize())
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 12.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DecoratedAvatar(profile.avatar, name, 44.dp, profile.decoration)
            Spacer(Modifier.width(14.dp))
            Text(
                name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = NameStyles.family(context, profile.nameFont),
            )
        }
    }
}
