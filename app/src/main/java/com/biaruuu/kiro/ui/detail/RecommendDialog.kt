package com.biaruuu.kiro.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.biaruuu.kiro.ui.i18n.Text

/** "Recommend to a friend": an optional one-line note travels with the link. */
@Composable
fun RecommendDialog(title: String, onDismiss: () -> Unit, onSend: (note: String) -> Unit) {
    var note by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF18181B)).padding(20.dp),
        ) {
            Text("Recommend to a friend", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(title, color = Color(0xFF9A9AA2), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111114)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = note,
                    onValueChange = { note = it.replace("\n", " ").take(80) },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (note.isEmpty()) Text("Add a note (optional)", color = Color(0xFF5E5E63), fontSize = 14.sp)
                        inner()
                    },
                )
            }
            Text("${note.length}/80", color = Color(0xFF5E5E63), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Cancel", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(24.dp))
                        .clickable(onClick = onDismiss).padding(vertical = 12.dp),
                )
                Text(
                    "Send", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .clickable { onSend(note.trim()) }.padding(vertical = 12.dp),
                )
            }
        }
    }
}
