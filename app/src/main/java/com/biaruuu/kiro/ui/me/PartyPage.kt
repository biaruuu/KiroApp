package com.biaruuu.kiro.ui.me

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
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
import com.biaruuu.kiro.ui.theme.KiroPurple

/** Join a friend's watch party with its 6 letter code. */
@Composable
fun PartyPage() {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text("Join a watch party", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Type the code your friend shares. Their episode opens and follows the host: play, pause, seeking and next episodes.",
            color = Color(0xFF9A9A9F), fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF18181B)).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = code,
                onValueChange = { code = it.filter { ch -> ch.isLetterOrDigit() }.uppercase().take(6) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(KiroPurple),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                decorationBox = { inner ->
                    if (code.isEmpty()) Text("ABC123", color = Color(0xFF4A4A50), fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (busy) "Joining..." else "Join party",
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(if (code.length == 6) KiroPurple else Color(0xFF2E2E30))
                .clickable(enabled = code.length == 6 && !busy) {
                    busy = true
                    WatchParty.lookup(code) { r, err ->
                        busy = false
                        if (r == null) {
                            com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err ?: "Couldn't join", Toast.LENGTH_LONG).show()
                        } else {
                            // The player opens on the host's episode and starts following.
                            DeepLink.request.value = "p:${r.animeId}:${r.episode}:$code"
                        }
                    }
                }
                .padding(vertical = 14.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "To host: open any episode, tap the three dots, then Watch party > Start.",
            color = Color(0xFF9A9A9F), fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}
