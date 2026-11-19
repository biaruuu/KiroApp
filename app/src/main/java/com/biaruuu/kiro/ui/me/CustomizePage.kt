package com.biaruuu.kiro.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Design
import com.biaruuu.kiro.data.Designs
import com.biaruuu.kiro.data.NameStyles
import com.biaruuu.kiro.data.ProfileRepository
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.ui.components.AnimatedAsset
import com.biaruuu.kiro.ui.components.DecoratedAvatar
import com.biaruuu.kiro.ui.theme.KiroPurple

private val Muted = Color(0xFF9A9A9F)
private val Card = Color(0xFF1A1A1D)

/** Pick an avatar decoration, library effect, library decoration and name font. A live preview is at the top; every pick saves at once. */
@Composable
fun CustomizePage() {
    val context = LocalContext.current
    val state by AuthRepository.state.collectAsState()
    val profile by ProfileRepository.profile.collectAsState()
    val remote by Designs.remote.collectAsState()
    val signedIn = state as? AuthState.SignedIn
    if (signedIn == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Log in to customize your profile.", color = Muted, fontSize = 14.sp)
        }
        return
    }
    val name = profile.username.ifBlank { signedIn.name }
    val decorations = remember(remote) { Designs.all(Designs.DECORATION) }
    val wv by com.biaruuu.kiro.data.WatchedStore.version.collectAsState()
    val sv by com.biaruuu.kiro.data.WatchStats.version.collectAsState()
    val cv by com.biaruuu.kiro.data.CloudSync.version.collectAsState()
    val myLevel = remember(wv, sv, cv) { com.biaruuu.kiro.data.WatchStats.levelNow(context) }
    val fonts = NameStyles.options()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // The card you are building, exactly as it appears at the top of Your Library.
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF121214))) {
            com.biaruuu.kiro.ui.components.LibraryHeader(profile = profile, name = name)
        }

        Section("Avatar Decoration")
        Grid(
            items = listOf<Design?>(null) + decorations,
            selectedId = profile.decoration,
            onPick = { id ->
                val d = Designs.find(id)
                val need = if (d == null) 1 else Designs.requiredLevel(d)
                // Decorations are earned with level; one you already wear stays selectable.
                if (d != null && need > myLevel && id != profile.decoration) {
                    com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Reach level $need to unlock this (you're level $myLevel).", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    ProfileRepository.save(decoration = id)
                }
            },
            tile = { d ->
                if (d == null) {
                    NoneTile()
                } else {
                    val need = Designs.requiredLevel(d)
                    val locked = need > myLevel && d.id != profile.decoration
                    TileWithLabel(if (locked) "Level $need" else d.name) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(Modifier.alpha(if (locked) 0.3f else 1f)) { DecoratedAvatar(profile.avatar, name, 46.dp, d.id) }
                            if (locked) Icon(painterResource(R.drawable.fluent_lock_closed_regular), null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            },
        )
        Spacer(Modifier.height(2.dp))
        Text("Level $myLevel · earn XP by watching to unlock more decorations. See Watch stats.", color = Muted, fontSize = 12.sp)

        Section("Display Name Style")
        for (font in fonts) {
            val on = profile.nameFont == font
            Row(
                modifier = Modifier
                    .fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { ProfileRepository.save(nameFont = font) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = NameStyles.family(context, font), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(NameStyles.label(font), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.size(10.dp))
                Box(Modifier.size(22.dp)) {
                    if (on) Icon(painterResource(R.drawable.fluent_checkmark_regular), null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(10.dp))
}

/** Rows of three tiles; [selectedId] "" is the "None" tile. */
@Composable
private fun Grid(items: List<Design?>, selectedId: String, onPick: (String) -> Unit, tile: @Composable (Design?) -> Unit) {
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { d ->
                val on = (d?.id ?: "") == selectedId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Card)
                        .border(2.dp, if (on) KiroPurple else Color(0xFF2A2A2D), RoundedCornerShape(10.dp))
                        .clickable { onPick(d?.id ?: "") }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) { tile(d) }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NoneTile() {
    Text("None", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
}

@Composable
private fun TileWithLabel(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        content()
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFD0D0D4), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
