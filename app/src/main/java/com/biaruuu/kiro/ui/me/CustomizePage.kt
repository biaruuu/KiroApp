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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Pick an avatar decoration, profile effect, nameplate and name font. A live preview is at the top; every pick saves at once. */
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
    val effects = remember(remote) { Designs.all(Designs.EFFECT) }
    val nameplates = remember(remote) { Designs.all(Designs.NAMEPLATE) }
    val fonts = remember { NameStyles.options(context) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // ---- Preview ----
        val effect = Designs.find(profile.effect)
        val plate = Designs.find(profile.nameplate)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card)) {
            if (effect != null) AnimatedAsset(effect, Modifier.matchParentSizeCompat(), crop = true)
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Box(Modifier.padding(8.dp)) { DecoratedAvatar(profile.avatar, name, 76.dp, profile.decoration) }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E0E10)), contentAlignment = Alignment.CenterStart) {
                    if (plate != null) AnimatedAsset(plate, Modifier.fillMaxSize(), crop = true)
                    Text(
                        name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontFamily = NameStyles.family(context, profile.nameFont),
                        style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 2f), 6f)),
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(signedIn.email, color = Muted, fontSize = 12.sp)
            }
        }

        Section("Avatar Decoration")
        Grid(listOf<Design?>(null) + decorations, profile.decoration) { ProfileRepository.save(decoration = it) }
        { d -> if (d == null) NoneTile() else TileWithLabel(d.name) { DecoratedAvatar(profile.avatar, name, 46.dp, d.id) } }

        Section("Profile Effect")
        Grid(listOf<Design?>(null) + effects, profile.effect) { ProfileRepository.save(effect = it) }
        { d -> if (d == null) NoneTile() else TileWithLabel(d.name) { AnimatedAsset(d, Modifier.size(52.dp)) } }

        Section("Nameplate")
        Grid(listOf<Design?>(null) + nameplates, profile.nameplate) { ProfileRepository.save(nameplate = it) }
        { d ->
            if (d == null) NoneTile() else TileWithLabel(d.name) {
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(6.dp))) { AnimatedAsset(d, Modifier.fillMaxSize(), crop = true) }
            }
        }
        if (effects.isEmpty() && nameplates.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("More effects and nameplates are coming soon.", color = Muted, fontSize = 12.sp)
        }

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

// A Box has no matchParentSize outside its scope here, so the effect fills the card through a sized column instead.
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().height(210.dp)

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
