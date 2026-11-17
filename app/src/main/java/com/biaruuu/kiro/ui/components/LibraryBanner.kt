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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.Designs
import com.biaruuu.kiro.data.NameStyles
import com.biaruuu.kiro.data.Profile

/**
 * The profile strip at the top of Your Library. It is always the same height; the picked library
 * effect plays behind it and the library decoration sits over it, with the avatar and name on top.
 */
@Composable
fun LibraryHeader(profile: Profile, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val remote by Designs.remote.collectAsState()
    val effect = remember(profile.libraryEffect, remote) { Designs.find(profile.libraryEffect) }
    val decoration = remember(profile.libraryDecoration, remote) { Designs.find(profile.libraryDecoration) }
    Box(modifier = modifier.fillMaxWidth().height(48.dp)) {
        if (effect != null) {
            // Soft tint only, so the header still reads as the flat dark strip and not as a picture.
            AnimatedAsset(effect, Modifier.fillMaxSize().alpha(0.32f), crop = true)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x66121214), Color.Transparent))))
        }
        if (decoration != null) AnimatedAsset(decoration, Modifier.fillMaxSize().alpha(0.7f), crop = true)
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DecoratedAvatar(profile.avatar, name, 30.dp, profile.decoration)
            Spacer(Modifier.width(12.dp))
            Text(
                name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = NameStyles.family(context, profile.nameFont),
            )
        }
    }
}
