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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.mutableIntStateOf
import com.biaruuu.kiro.ui.party.PtButton
import com.biaruuu.kiro.ui.party.PtSegmented
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.input.ImeAction

/** A rounded, filled text field in the same quiet style as the rest of the party screens. */
@Composable
private fun PartyField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, big: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1F)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(
                color = Color.White, fontSize = if (big) 26.sp else 16.sp, fontWeight = if (big) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = if (big) 6.sp else 0.sp, textAlign = if (big) TextAlign.Center else TextAlign.Start,
            ),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (big) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, color = Color(0xFF55555C), fontSize = if (big) 26.sp else 16.sp, letterSpacing = if (big) 6.sp else 0.sp)
                    inner()
                }
            },
        )
    }
}

/** Create a watch party room, or join a friend's with its 6 letter code: one page, two tabs. */
@Composable
fun PartyPage() {
    val context = LocalContext.current
    val inRoom by WatchParty.session.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val waiting by WatchParty.waiting.collectAsState()
    var pendingOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(inRoom?.code, pendingOpen) {
        if (pendingOpen && inRoom != null) {
            pendingOpen = false
            DeepLink.request.value = "room"
        }
    }
    if (waiting != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { WatchParty.cancelWaiting() }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1B1B1F)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(16.dp))
                Text("Waiting for the host", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("You will join as soon as the host accepts your request.", color = Color(0xFF9A9AA2), fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                PtButton("Cancel", Modifier.fillMaxWidth()) { WatchParty.cancelWaiting() }
            }
        }
    }
    val ready = code.length == 6 && !busy
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            Text("Watch party", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Watch together with live chat and voice. The room stays open while you browse Kiro.",
                color = Color(0xFF9A9AA2), fontSize = 13.sp, lineHeight = 19.sp,
            )
            Spacer(Modifier.height(22.dp))

            if (inRoom != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF1B1B1F)).padding(20.dp)) {
                    Text("You are in a room", color = Color(0xFF9A9AA2), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(inRoom!!.code, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
                    Spacer(Modifier.height(16.dp))
                    PtButton("Open room", Modifier.fillMaxWidth(), filled = true) { DeepLink.request.value = "room" }
                }
            } else {
                PtSegmented(listOf("Create", "Join"), tab, { tab = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                if (tab == 0) {
                    Text("Name your room", color = Color(0xFF9A9AA2), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    PartyField(roomName, { if (it.length <= 40) roomName = it }, "Friday anime night")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "You are the host: you pick what to watch, and you decide who can chat, talk and join.",
                        color = Color(0xFF6A6A72), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    PtButton(if (creating) "Creating..." else "Create room", Modifier.fillMaxWidth(), filled = true, enabled = !creating) {
                        if (roomName.isBlank()) {
                            com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Give your room a name first", Toast.LENGTH_SHORT).show()
                            return@PtButton
                        }
                        creating = true
                        WatchParty.host(context, 0, 0, "", roomName) { _, err ->
                            creating = false
                            if (err != null) com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err, Toast.LENGTH_LONG).show()
                            else DeepLink.request.value = "room"
                        }
                    }
                } else {
                    Text("Room code", color = Color(0xFF9A9AA2), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    PartyField(
                        code, { code = it.filter { ch -> ch.isLetterOrDigit() }.uppercase().take(6) }, "ABC123", big = true,
                        capitalization = KeyboardCapitalization.Characters,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Ask the host for the 6 letter code. Some rooms need the host to accept you first.",
                        color = Color(0xFF6A6A72), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    PtButton(if (busy) "Joining..." else "Join room", Modifier.fillMaxWidth(), filled = true, enabled = ready) {
                        busy = true
                        WatchParty.lookup(code) { r, err ->
                            busy = false
                            if (r == null) {
                                com.biaruuu.kiro.ui.i18n.I18nToast.make(context, err ?: "Couldn't join", Toast.LENGTH_LONG).show()
                            } else {
                                pendingOpen = true
                                WatchParty.enter(context, code)
                            }
                        }
                    }
                }
            }
        }
    }
}
