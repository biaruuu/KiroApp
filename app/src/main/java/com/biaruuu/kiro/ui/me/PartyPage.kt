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
import androidx.compose.foundation.border
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.biaruuu.kiro.ui.auth.UnderlineField

@Composable
private fun PartyPageCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF18181B)).padding(18.dp),
        content = content,
    )
}

/** Create a watch party room, or join a friend's with its 6 letter code. */
@Composable
fun PartyPage() {
    val context = LocalContext.current
    val inRoom by WatchParty.session.collectAsState()
    var code by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val ready = code.length == 6 && !busy
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            Text("Watch party", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Watch together with live chat and voice. The room stays open while you browse Kiro.",
                color = Color(0xFF9A9A9F), fontSize = 13.sp, lineHeight = 19.sp,
            )
            Spacer(Modifier.height(20.dp))

            if (inRoom != null) {
                PartyPageCard {
                    Text("You are in a room", color = Color(0xFF9A9A9F), fontSize = 12.sp)
                    Text(inRoom!!.code, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 5.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Open room", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)
                            .clickable { DeepLink.request.value = "room" }.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            } else {
                PartyPageCard {
                    Text("Create a room", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    UnderlineField(
                        value = roomName, onChange = { if (it.length <= 40) roomName = it }, hint = "Room name (optional)",
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Done, onImeAction = {}, autoFocus = false,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (creating) "Creating..." else "Create room",
                        color = if (creating) Color(0xFF6A6A70) else Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(if (creating) Color(0xFF222226) else Color.White)
                            .clickable(enabled = !creating) {
                                creating = true
                                WatchParty.host(context, 0, 0, "", roomName) { _, err ->
                                    creating = false
                                    if (err != null) com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err, Toast.LENGTH_LONG).show()
                                    else DeepLink.request.value = "room"
                                }
                            }
                            .padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                PartyPageCard {
                    Text("Join with a code", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF121212))
                            .border(1.dp, Color(0xFF26262B), RoundedCornerShape(12.dp)).padding(vertical = 18.dp),
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
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (busy) "Joining..." else "Join room",
                        color = if (ready) Color.Black else Color(0xFF6A6A70), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(if (ready) Color.White else Color(0xFF222226))
                            .clickable(enabled = ready) {
                                busy = true
                                WatchParty.lookup(code) { r, err ->
                                    busy = false
                                    if (r == null) {
                                        com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err ?: "Couldn't join", Toast.LENGTH_LONG).show()
                                    } else {
                                        WatchParty.enter(context, code)
                                        DeepLink.request.value = "room"
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "In a room, the host picks what to watch. It plays inside the room page for everyone, and you can go fullscreen any time.",
                color = Color(0xFF6A6A70), fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
    }
}
