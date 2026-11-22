package com.biaruuu.kiro.ui.party

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.WatchParty
import com.biaruuu.kiro.ui.components.DecoratedAvatar
import com.biaruuu.kiro.ui.i18n.Text

/** One small set of colours for the whole watch party, so every screen of it looks the same. */
internal val PtBg = Color(0xFF121212)
internal val PtSurface = Color(0xFF18181B)
internal val PtRaised = Color(0xFF222226)
internal val PtLine = Color(0xFF26262B)
internal val PtMuted = Color(0xFF9A9AA2)
internal val PtFaint = Color(0xFF6A6A72)
internal val PtRed = Color(0xFFFF6B6B)
internal val PtGreen = Color(0xFF3DDC84)
internal val PtBlue = Color(0xFF0A84FF)
internal val PtBubble = Color(0xFF2A2A2E)
internal val PtMine = Color(0xFF2E2E35)
internal val PtOther = Color(0xFF1C1C20)

/** A pill button: [filled] is the one main action on a screen, [danger] is red text. */
@Composable
internal fun PtButton(
    label: String, modifier: Modifier = Modifier, filled: Boolean = false, danger: Boolean = false, enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = when {
            !enabled -> PtFaint
            filled -> Color.Black
            danger -> PtRed
            else -> Color.White
        },
        fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(22.dp))
            .background(if (!enabled) PtRaised else if (filled) Color.White else PtRaised)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

/** A round icon-only button with a comfortable 44dp tap area. */
@Composable
internal fun PtIconButton(icon: Int, description: String, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Icon(
        painterResource(icon), contentDescription = description, tint = tint,
        modifier = modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).padding(11.dp),
    )
}

/** The first few people in the room as overlapping pictures, then "+n". Tap for the member list. */
@Composable
internal fun PtAvatarStack(members: List<WatchParty.Member>, modifier: Modifier = Modifier, max: Int = 3, onClick: () -> Unit) {
    if (members.isEmpty()) return
    val shown = members.take(max)
    val extra = members.size - shown.size
    val size = 28
    val step = 19
    val slots = shown.size + if (extra > 0) 1 else 0
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp)
            .width(((slots - 1) * step + size).dp + 8.dp),
    ) {
        shown.forEachIndexed { i, m ->
            Box(
                Modifier.offset(x = (i * step).dp).size(size.dp).clip(CircleShape).background(PtBg).border(2.dp, PtBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) { DecoratedAvatar(m.avatar, m.name, (size - 4).dp, "") }
        }
        if (extra > 0) {
            Box(
                Modifier.offset(x = (shown.size * step).dp).size(size.dp).clip(CircleShape).background(PtRaised).border(2.dp, PtBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("+$extra", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** A small uppercase label that starts a group of rows. */
@Composable
internal fun PtSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = PtFaint, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, modifier = modifier)
}

@Composable
internal fun PtDot(color: Color, size: Int = 7) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
internal fun PtGap(w: Int) { Spacer(Modifier.width(w.dp)) }

internal fun Modifier.ptCard(radius: Int = 18): Modifier =
    this.clip(RoundedCornerShape(radius.dp)).background(PtSurface)
