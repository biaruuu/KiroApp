package com.biaruuu.kiro.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState

/** The Me tab: who is signed in, and a way to sign out. */
@Composable
fun MeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configured = remember { AuthRepository.isConfigured(context) }
    val state by AuthRepository.state.collectAsState()

    Column(
        modifier = modifier.statusBarsPadding().padding(24.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val signedIn = state as? AuthState.SignedIn
        if (configured && signedIn != null) {
            if (signedIn.name.isNotEmpty()) {
                Text(signedIn.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
            }
            Text(signedIn.email, color = Color(0xFF9A9AA5), fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Text(
                "Sign out",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E22), RoundedCornerShape(6.dp))
                    .clickable { AuthRepository.signOut() }
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            Text("Me", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (configured) "Signed out." else "Accounts aren't set up in this build.",
                color = Color(0xFF9A9AA5),
                fontSize = 15.sp,
            )
        }
    }
}
