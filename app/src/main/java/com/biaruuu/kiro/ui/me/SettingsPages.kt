package com.biaruuu.kiro.ui.me

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.biaruuu.kiro.BuildConfig
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.AppSettings
import com.biaruuu.kiro.data.HttpClientProvider
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.ui.components.PageTopBar
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

private val Muted = Color(0xFF9A9A9F)
private val Hairline = Color(0xFF2B2B2F)
private val DisabledGray = Color(0xFF2E2E30)
private val DisabledText = Color(0xFF5E5E63)

/** A full-screen settings page opened from the Me tab. */
@Composable
fun SettingsPage(page: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title = when (page) {
        "account" -> "Account Setting"
        "download" -> "Download setting"
        "play" -> "Play Setting"
        "language" -> "Language"
        "license" -> "License"
        else -> "Version"
    }
    Box(modifier = modifier.background(KiroBackground), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PageTopBar(title, onBack)
            when (page) {
                "account" -> AccountPage(onBack)
                "download" -> DownloadPage()
                "play" -> PlayPage()
                "language" -> LanguagePage()
                "license" -> LicensePage()
                else -> VersionPage()
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = KiroPurple,
                checkedThumbColor = Color.White,
                checkedBorderColor = KiroPurple,
                uncheckedTrackColor = Color(0xFF3A3A3E),
                uncheckedThumbColor = Color(0xFFB4B4BA),
                uncheckedBorderColor = Color(0xFF3A3A3E),
            ),
        )
    }
}

@Composable
private fun ChevronRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Muted, fontSize = 16.sp)
        Icon(
            painter = painterResource(R.drawable.fluent_chevron_right_regular),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun PlayPage() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AppSettings.autoplayPreviews(context)) }
    ToggleRow("Autoplay previews", on) {
        on = it
        AppSettings.setAutoplayPreviews(context, it)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadPage() {
    val context = LocalContext.current
    var wifi by remember { mutableStateOf(AppSettings.downloadWifiOnly(context)) }
    var quality by remember { mutableStateOf(AppSettings.downloadQuality(context)) }
    var sheet by remember { mutableStateOf(false) }

    ToggleRow("Wi-Fi only", wifi) {
        wifi = it
        AppSettings.setDownloadWifiOnly(context, it)
    }
    ChevronRow("Video quality", quality) { sheet = true }
    ChevronRow("Delete all downloads") {
        Toast.makeText(context, "You have no downloads", Toast.LENGTH_SHORT).show()
    }

    if (sheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            sheetState = state,
            containerColor = Color(0xFF141414),
            scrimColor = Color.Black.copy(alpha = 0.72f),
            shape = RectangleShape,
            tonalElevation = 0.dp,
            dragHandle = null,
        ) {
            Column(modifier = Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                AppSettings.DOWNLOAD_QUALITIES.forEach { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                quality = q
                                AppSettings.setDownloadQuality(context, q)
                                scope.launch { state.hide() }.invokeOnCompletion { sheet = false }
                            }
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(44.dp)) {
                            if (q == quality) {
                                Icon(
                                    painter = painterResource(R.drawable.fluent_checkmark_regular),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        Text(q, color = Color.White, fontSize = 19.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePage() {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("English", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.fluent_checkmark_regular),
            contentDescription = null,
            tint = KiroPurple,
            modifier = Modifier.size(26.dp),
        )
    }
    Text("More languages are coming later.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun LicensePage() {
    val libs = listOf(
        "Jetpack Compose & AndroidX" to "Apache License 2.0",
        "Media3 / ExoPlayer" to "Apache License 2.0",
        "FFmpeg audio decoders (jellyfin media3-ffmpeg-decoder)" to "GPL / LGPL (FFmpeg)",
        "Coil" to "Apache License 2.0",
        "OkHttp" to "Apache License 2.0",
        "Firebase Authentication" to "Apache License 2.0",
        "Fluent UI System Icons" to "MIT License",
        "Anime data: AniList. Trailers and artwork: TMDB." to "Used under their API terms",
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Kiro is built with open-source software:", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        libs.forEach { (name, license) ->
            Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(license, color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "1.15.0" vs "1.14.2": true when [latest] is newer than [current]. */
private fun isNewer(latest: String, current: String): Boolean {
    val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

@Composable
private fun VersionPage() {
    val context = LocalContext.current
    val current = BuildConfig.VERSION_NAME.substringBefore('-')
    var latest by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf(REPO_URL + "/releases/latest") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/biaruuu/KiroApp/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                HttpClientProvider.get().newCall(req).execute().use { r ->
                    if (r.isSuccessful) {
                        val json = JSONObject(r.body?.string().orEmpty())
                        latest = json.optString("tag_name").removePrefix("v").ifEmpty { null }
                        val assets = json.optJSONArray("assets")
                        val apk = assets?.optJSONObject(0)?.optString("browser_download_url").orEmpty()
                        downloadUrl = apk.ifEmpty { json.optString("html_url", downloadUrl) }
                    }
                }
            } catch (_: Exception) {
                // Offline: the button just stays disabled.
            }
        }
    }

    val canUpdate = latest?.let { isNewer(it, current) } == true
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Kiro Android", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text("Latest Version ${latest ?: current}", color = Color(0xFFB4B4BA), fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text("Current Version $current", color = Color(0xFFB4B4BA), fontSize = 17.sp)
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (canUpdate) KiroPurple else DisabledGray)
                .clickable(enabled = canUpdate) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                    } catch (_: Exception) {
                        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Update Now", color = if (canUpdate) Color.White else DisabledText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AccountPage(onBack: () -> Unit) {
    val state by AuthRepository.state.collectAsState()
    val s = state as? AuthState.SignedIn
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(if (s?.google == true) R.drawable.google_g_logo else R.drawable.fluent_mail_filled),
                    contentDescription = null,
                    tint = if (s?.google == true) Color.Unspecified else Muted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text("Email", color = Muted, fontSize = 17.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(s?.email.orEmpty(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.padding(vertical = 24.dp).fillMaxWidth().height(1.dp).background(Hairline))
            ChevronRowPadless("Logout") { confirmLogout = true }
            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color(0xFFFF6B6B), fontSize = 15.sp)
            }
        }
        Text(
            "Delete account",
            color = Muted,
            fontSize = 17.sp,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { confirmDelete = true }
                .padding(vertical = 20.dp),
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            text = "Do you want to log out?",
            confirm = "Logout",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                AuthRepository.signOut()
                onBack()
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            text = "Delete your account? This can't be undone.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                AuthRepository.deleteAccount { err ->
                    if (err == null) onBack() else message = err
                }
            },
        )
    }
}

@Composable
private fun ChevronRowPadless(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.fluent_chevron_right_regular),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF141414))
                .padding(24.dp),
        ) {
            Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 22.dp, vertical = 14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    confirm,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(KiroPurple)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                )
            }
        }
    }
}
