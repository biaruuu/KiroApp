package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.ui.theme.KiroPurple

/** Asked every time someone taps an episode they have already finished. */
@Composable
fun RewatchDialog(onNo: () -> Unit, onYes: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = Color(0xFF1E1E22),
        title = { Text("Watch again?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text("Are you sure you want to rewatch this again?", color = Color(0xFF9A9A9F), fontSize = 15.sp) },
        confirmButton = { TextButton(onClick = onYes) { Text("Yes", color = KiroPurple, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onNo) { Text("No", color = Color.White) } },
    )
}

/** Small "Watched" pill for the corner of an episode thumbnail. */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.75f), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(com.biaruuu.kiro.R.drawable.fluent_checkmark_regular),
            contentDescription = null,
            tint = KiroPurple,
            modifier = Modifier.size(11.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(3.dp))
        Text("Watched", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
