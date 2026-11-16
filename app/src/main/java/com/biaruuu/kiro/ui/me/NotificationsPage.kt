package com.biaruuu.kiro.ui.me

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.UpdateChecker
import com.biaruuu.kiro.ui.theme.KiroPurple

private val Muted = Color(0xFF9A9A9F)
private val Card = Color(0xFF1A1A1D)

/** Every published version as a notification; tapping one opens its full notes. */
@Composable
fun NotificationsPage(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var releases by remember { mutableStateOf<List<UpdateChecker.Release>?>(null) }
    // What counts as new is decided before this visit clears it.
    val seenBefore = remember { UpdateChecker.seenVersion(context) }

    LaunchedEffect(Unit) {
        val list = UpdateChecker.recent()
        releases = list
        list.firstOrNull()?.let { UpdateChecker.onLatest(context, it) }
        UpdateChecker.markSeen(context)
    }

    val list = releases
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KiroPurple, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No notifications right now. Check your connection and try again.", color = Muted, fontSize = 14.sp)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.version }) { r ->
                val newer = UpdateChecker.isNewer(r.version, UpdateChecker.currentVersion)
                val unread = UpdateChecker.isNewer(r.version, seenBefore)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(Card)
                        .clickable { onOpen("release-${r.version}") }
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape)
                            .background(if (unread) Color(0xFFFF4D4F) else Color.Transparent),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (newer) "Kiro ${r.version} is available" else "Kiro ${r.version}",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            preview(r.notes),
                            color = Color(0xFFB4B4BA), fontSize = 13.sp, lineHeight = 18.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(r.published.takeIf { it.isNotEmpty() }, if (newer) "Tap to update" else null).joinToString("  ·  "),
                            color = if (newer) KiroPurple else Muted, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

/** The first bullet of the release notes, without markdown marks. */
private fun preview(notes: String): String {
    val line = notes.lines().map { it.trim() }.firstOrNull { it.startsWith("- ") } ?: return "See what's new in this version."
    return line.removePrefix("- ").replace("**", "").replace("`", "")
}
