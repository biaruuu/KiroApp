package com.biaruuu.kiro.ui.party

import com.biaruuu.kiro.ui.theme.KiroText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
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
internal val PtBg = Color(0xFF000000)
internal val PtSurface = Color(0xFF1B1B1F)
internal val PtRaised = Color(0xFF26262B)
internal val PtLine = Color(0xFF232328)
internal val PtMuted = Color(0xFF9A9AA2)
internal val PtFaint = Color(0xFF6A6A72)
internal val PtRed = Color(0xFFFF6B6B)
internal val PtGreen = Color(0xFF3DDC84)
internal val PtBlue = Color(0xFF0A84FF)
internal val PtBubble = Color(0xFF2A2A2E)
internal val PtMine = Color(0xFF2E2E35)
internal val PtOther = Color(0xFF1C1C20)

/**
 * The one button style of the watch party: a soft rounded rectangle, 44dp tall.
 * [filled] is the main action (white), [danger] is a quiet red one, the rest are calm grey.
 */
@Composable
internal fun PtButton(
    label: String, modifier: Modifier = Modifier, filled: Boolean = false, danger: Boolean = false, enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        !enabled -> PtRaised.copy(alpha = 0.6f)
        filled -> Color.White
        danger -> PtRed.copy(alpha = 0.14f)
        else -> PtRaised
    }
    Box(
        modifier.heightIn(min = 44.dp).clip(shape).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> PtFaint
                filled -> Color.Black
                danger -> PtRed
                else -> Color.White
            },
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
        )
    }
}

/** Two or three choices in one rounded track; the chosen one is lifted. Used for the Chat / Voice switch. */
@Composable
internal fun PtSegmented(
    labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, badge: (Int) -> String? = { null },
) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(PtSurface).padding(3.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (on) PtRaised else Color.Transparent).clickable { onSelect(i) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text(label, color = if (on) Color.White else PtMuted, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                badge(i)?.let {
                    Spacer(Modifier.width(6.dp))
                    PtDot(PtGreen, 6)
                    Spacer(Modifier.width(3.dp))
                    Text(it, color = PtGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
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
            ) { Text("+$extra", color = KiroText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
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

internal fun Modifier.ptCard(radius: Int = 16): Modifier =
    this.clip(RoundedCornerShape(radius.dp)).background(PtSurface)
