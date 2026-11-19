package com.biaruuu.kiro.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.Admin
import com.biaruuu.kiro.data.Announcements
import com.biaruuu.kiro.data.UpdateChecker
import com.biaruuu.kiro.ui.auth.Avatar
import com.biaruuu.kiro.ui.theme.KiroPurple

private val Muted = Color(0xFF9A9A9F)
private val Card = Color(0xFF18181B)
private val VerifiedBlue = Color(0xFF1D9BF0)

/** One row of the list: a message from the developer, or a published version. */
private class Entry(val key: String, val time: Long, val announcement: Announcement?, val release: UpdateChecker.Release?)
private typealias Announcement = com.biaruuu.kiro.data.Announcement

/** Messages and new versions, newest first, each shown as coming from the verified developer. */
@Composable
fun NotificationsPage(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var releases by remember { mutableStateOf<List<UpdateChecker.Release>?>(null) }
    val announcements by Announcements.list.collectAsState()
    val publicCard by Announcements.admin.collectAsState()
    val authState by com.biaruuu.kiro.data.auth.AuthRepository.state.collectAsState()
    val myProfile by com.biaruuu.kiro.data.ProfileRepository.profile.collectAsState()
    // When the developer looks, their own live profile is used; everyone else reads the public card.
    val admin = if (Admin.isAdmin(authState) && myProfile.username.isNotBlank()) {
        com.biaruuu.kiro.data.AdminCard(myProfile.username, myProfile.bio, myProfile.avatar, Admin.EMAIL, myProfile.decoration, myProfile.nameFont)
    } else publicCard
    // What counts as new is decided before this visit clears it.
    val seenVersion = remember { UpdateChecker.seenVersion(context) }
    val seenAnnouncement = remember { context.getSharedPreferences("kiro_update", android.content.Context.MODE_PRIVATE).getLong("ann_seen", Long.MAX_VALUE) }
    val expanded = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val list = UpdateChecker.recent().take(2)
        releases = list
        list.firstOrNull()?.let { UpdateChecker.onLatest(context, it) }
        UpdateChecker.markSeen(context)
        Announcements.markSeen(context)
    }

    val rel = releases
    if (rel == null && announcements.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KiroPurple, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        return
    }
    val entries = remember(rel, announcements) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        (announcements.map { Entry("a:${it.id}", it.createdAt, it, null) } +
            rel.orEmpty().map { r -> Entry("r:${r.version}", runCatching { fmt.parse(r.published)?.time }.getOrNull() ?: 0L, null, r) })
            .sortedByDescending { it.time }
    }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No notifications right now. Check your connection and try again.", color = Muted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.key }) { e ->
            val a = e.announcement
            val r = e.release
            val newer = r != null && UpdateChecker.isNewer(r.version, UpdateChecker.currentVersion)
            val unread = if (a != null) a.createdAt > seenAnnouncement else r != null && UpdateChecker.isNewer(r.version, seenVersion)
            val open = a != null && e.key in expanded
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Card)
                    .clickable {
                        if (r != null) onOpen("release-${r.version}")
                        else if (e.key in expanded) expanded.remove(e.key) else expanded.add(e.key)
                    }
                    .padding(14.dp),
            ) {
                DeveloperHeader(admin, ago(e.time), unread)
                Spacer(Modifier.height(10.dp))
                if (a != null) {
                    if (a.title.isNotBlank()) {
                        Text(a.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(
                        a.body, color = Color(0xFFB4B4BA), fontSize = 13.sp, lineHeight = 19.sp,
                        maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    )
                } else if (r != null) {
                    Text(
                        if (newer) "Kiro ${r.version} is available" else "Kiro ${r.version}",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        preview(r.notes), color = Color(0xFFB4B4BA), fontSize = 13.sp, lineHeight = 18.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (newer) {
                        Spacer(Modifier.height(6.dp))
                        Text("Tap to update", color = KiroPurple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Avatar, name with the blue verified tick, a Developer badge and the developer's email. */
@Composable
private fun DeveloperHeader(card: com.biaruuu.kiro.data.AdminCard?, time: String, unread: Boolean) {
    val name = card?.username?.ifBlank { null } ?: "Developer"
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.biaruuu.kiro.ui.components.DecoratedAvatar(card?.avatar.orEmpty(), name, 40.dp, card?.decoration)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val context = LocalContext.current
                Text(
                    name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontFamily = com.biaruuu.kiro.data.NameStyles.family(context, card?.nameFont.orEmpty()),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(5.dp))
                VerifiedBadge()
                Spacer(Modifier.width(6.dp))
                Text(
                    "Dev",
                    color = KiroPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .border(1.dp, KiroPurple, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(Admin.EMAIL, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (unread) Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF4D4F)))
            if (time.isNotEmpty()) {
                Spacer(Modifier.height(if (unread) 4.dp else 0.dp))
                Text(time, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun VerifiedBadge(size: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(VerifiedBlue), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.fluent_checkmark_regular),
            contentDescription = "Verified",
            tint = Color.White,
            modifier = Modifier.size(size * 0.7f),
        )
    }
}

internal fun ago(time: Long): String {
    if (time <= 0L) return ""
    val s = (System.currentTimeMillis() - time) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        s < 86400 * 30 -> "${s / 86400} d ago"
        else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(time))
    }
}

/** The first bullet of the release notes, without markdown marks. */
private fun preview(notes: String): String {
    val line = notes.lines().map { it.trim() }.firstOrNull { it.startsWith("- ") } ?: return "See what's new in this version."
    return line.removePrefix("- ").replace("**", "").replace("`", "")
}
