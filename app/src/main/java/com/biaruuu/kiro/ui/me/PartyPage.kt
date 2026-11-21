package com.biaruuu.kiro.ui.me

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.DeepLink
import com.biaruuu.kiro.data.WatchParty
import com.biaruuu.kiro.ui.theme.KiroAccent

/** Join a friend's watch party with its 6 letter code. */
@Composable
fun PartyPage() {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val ready = code.length == 6 && !busy
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text("Join a watch party", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter the code from your friend. Their episode opens and stays in sync, live.",
                color = Color(0xFF9A9A9F), fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF18181B)).padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it.filter { ch -> ch.isLetterOrDigit() }.uppercase().take(6) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (code.isEmpty()) Text("ABC123", color = Color(0xFF3A3A3F), fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (busy) "Joining..." else "Join party",
                color = if (ready) Color.Black else Color(0xFF6A6A70), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(if (ready) Color.White else Color(0xFF222226))
                    .clickable(enabled = ready) {
                        busy = true
                        WatchParty.lookup(code) { r, err ->
                            busy = false
                            if (r == null) {
                                com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err ?: "Couldn't join", Toast.LENGTH_LONG).show()
                            } else if (r.animeId > 0) {
                                // The player opens on the host's episode and starts following.
                                DeepLink.request.value = "p:${r.animeId}:${r.episode}:$code"
                            } else {
                                // Nothing is playing yet: join the room and browse while you wait.
                                WatchParty.enter(context, code)
                                DeepLink.request.value = "s:partyroom"
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
            )
            Spacer(Modifier.height(12.dp))
            val inRoom by WatchParty.session.collectAsState()
            Text(
                if (inRoom != null) "Open my room" else "Create a room",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF222226))
                    .clickable {
                        if (inRoom != null) {
                            DeepLink.request.value = "s:partyroom"
                        } else {
                            WatchParty.host(context, 0, 0, "") { _, err ->
                                if (err != null) com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err, Toast.LENGTH_LONG).show()
                                else DeepLink.request.value = "s:partyroom"
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "A room has live chat and voice, and it stays open while you browse Kiro. To watch together, start any episode while you are in the room.",
                color = Color(0xFF6A6A70), fontSize = 12.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
