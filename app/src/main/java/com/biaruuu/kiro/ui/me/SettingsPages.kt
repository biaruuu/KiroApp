package com.biaruuu.kiro.ui.me

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.biaruuu.kiro.ui.i18n.Text
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.scale
import com.biaruuu.kiro.BuildConfig
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.AppSettings
import com.biaruuu.kiro.data.HttpClientProvider
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.ProfileRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.ui.auth.AvatarEditable
import com.biaruuu.kiro.ui.auth.CheckBox
import com.biaruuu.kiro.ui.auth.PrimaryButton
import com.biaruuu.kiro.ui.auth.UnderlineField
import com.biaruuu.kiro.ui.auth.rememberAvatarFlow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.biaruuu.kiro.ui.components.PageTopBar
import com.biaruuu.kiro.ui.theme.KiroBackground
import com.biaruuu.kiro.ui.theme.KiroAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

private val Muted = Color(0xFF9A9A9F)
private val Hairline = Color(0xFF222226)
private val DisabledGray = Color(0xFF2E2E30)
private val DisabledText = Color(0xFF5E5E63)

/** A full-screen settings page opened from the Me tab. */
@Composable
fun SettingsPage(page: String, onBack: () -> Unit, modifier: Modifier = Modifier, onOpen: (String) -> Unit = {}) {
    var sub by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page == "account" && sub != null) { sub = null }
    val title = when (page) {
        "account" -> when (sub) { "username" -> "Username"; "bio" -> "Bio"; else -> "Account Setting" }
        "download" -> "Download setting"
        "play" -> "Play Setting"
        "language" -> "Language"
        "help" -> "Help Center"
        "contact" -> "Contact us"
        "whatsnew-latest", "whatsnew-current" -> "What's new"
        "terms" -> "Terms of Use"
        "privacy" -> "Privacy Policy"
        "notifications" -> "Notifications"
        "admin" -> "Admin panel"
        "customize" -> "Customize profile"
        "achievements" -> "Achievements"
        "alerts" -> "Notification control"
        "storage" -> "Storage"
        "backup" -> "Backup and restore"
        "stats" -> "Watch stats"
        "party" -> "Watch party"
        else -> if (page.startsWith("release-")) "What's new" else "Version"
    }
    Box(modifier = modifier.background(KiroBackground), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PageTopBar(title, if (page == "account" && sub != null) ({ sub = null }) else onBack)
            when (page) {
                "account" -> AccountPage(onBack, sub) { sub = it }
                "download" -> DownloadPage()
                "play" -> PlayPage()
                "language" -> LanguagePage()
                "help" -> HelpPage()
                "contact" -> ContactPage(onBack)
                "whatsnew-latest" -> WhatsNewPage(latest = true, onBack = onBack)
                "whatsnew-current" -> WhatsNewPage(latest = false, onBack = onBack)
                "terms" -> com.biaruuu.kiro.ui.legal.LegalContent(terms = true)
                "privacy" -> com.biaruuu.kiro.ui.legal.LegalContent(terms = false)
                "notifications" -> NotificationsPage(onOpen)
                "admin" -> AdminPage()
                "customize" -> CustomizePage()
                "achievements" -> AchievementsPage()
                "alerts" -> AlertsPage()
                "storage" -> StoragePage()
                "backup" -> BackupPage()
                "stats" -> WatchStatsPage()
                "party" -> PartyPage()
                else -> if (page.startsWith("release-")) WhatsNewPage(latest = false, onBack = onBack, version = page.removePrefix("release-")) else VersionPage()
            }
        }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            modifier = Modifier.scale(0.92f),
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = KiroAccent,
                checkedThumbColor = Color(0xFF111111),
                checkedBorderColor = KiroAccent,
                uncheckedTrackColor = Color(0xFF3A3A3E),
                uncheckedThumbColor = Color(0xFFB4B4BA),
                uncheckedBorderColor = Color(0xFF3A3A3E),
            ),
        )
    }
}

@Composable
internal fun ChevronRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(45.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Muted, fontSize = 13.sp)
        Icon(
            painter = painterResource(R.drawable.fluent_chevron_right_regular),
            contentDescription = null,
            tint = Color(0xFF5E5E63),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PlayPage() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AppSettings.autoplayPreviews(context)) }
    Spacer(Modifier.height(2.dp))
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
    var limit by remember { mutableStateOf(AppSettings.downloadLimitGb(context)) }
    var limitSheet by remember { mutableStateOf(false) }
    val downloads by com.biaruuu.kiro.data.Downloads.items.collectAsState()
    val usedBytes = downloads.sumOf { it.bytes }

    Spacer(Modifier.height(10.dp))
    ToggleRow("Wi-Fi only", wifi) {
        wifi = it
        AppSettings.setDownloadWifiOnly(context, it)
        com.biaruuu.kiro.data.Downloads.applyRequirements(context)
    }
    ChevronRow("Video quality", quality) { sheet = true }
    ChevronRow("Storage limit", if (limit == 0) "No limit" else "$limit GB") { limitSheet = true }
    Text(
        "Downloads use ${formatBytes(usedBytes)}" + if (limit > 0) " of $limit GB" else "",
        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    ChevronRow("Delete all downloads") {
        if (com.biaruuu.kiro.data.Downloads.items.value.isEmpty()) {
            com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "You have no downloads", Toast.LENGTH_SHORT).show()
        } else {
            com.biaruuu.kiro.data.Downloads.removeAll(context)
            com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "All downloads deleted", Toast.LENGTH_SHORT).show()
        }
    }

    if (limitSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { limitSheet = false },
            sheetState = state,
            containerColor = Color(0xFF18181B),
            scrimColor = Color.Black.copy(alpha = 0.72f),
            shape = RectangleShape,
            tonalElevation = 0.dp,
            dragHandle = null,
        ) {
            Column(modifier = Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                AppSettings.DOWNLOAD_LIMITS_GB.forEach { gb ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                limit = gb
                                AppSettings.setDownloadLimitGb(context, gb)
                                scope.launch { state.hide() }.invokeOnCompletion { limitSheet = false }
                            }
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(44.dp)) {
                            if (gb == limit) {
                                Icon(painterResource(R.drawable.fluent_checkmark_regular), null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }
                        Text(if (gb == 0) "No limit" else "$gb GB", color = Color.White, fontSize = 19.sp)
                    }
                }
            }
        }
    }

    if (sheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            sheetState = state,
            containerColor = Color(0xFF18181B),
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

internal fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
    b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.0f MB", b / (1024.0 * 1024.0))
    else -> "0 MB"
}

@Composable
private fun LanguagePage() {
    val context = LocalContext.current
    val current = com.biaruuu.kiro.ui.i18n.I18n.lang
    listOf(
        com.biaruuu.kiro.ui.i18n.I18n.EN to "English",
        com.biaruuu.kiro.ui.i18n.I18n.FIL to "Filipino",
    ).forEach { (code, label) ->
        Row(
            modifier = Modifier.fillMaxWidth().height(45.dp)
                .clickable { com.biaruuu.kiro.ui.i18n.I18n.set(context, code) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text(label, color = Color.White, fontSize = 14.sp)
            if (code == com.biaruuu.kiro.ui.i18n.I18n.FIL) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    androidx.compose.material3.Text("BETA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.weight(1f))
            if (current == code) Icon(
                painter = painterResource(R.drawable.fluent_checkmark_regular),
                contentDescription = null,
                tint = KiroAccent,
                modifier = Modifier.size(26.dp),
            )
        }
    }
    Text("Filipino is in beta: some text may still show in English.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(Modifier.height(6.dp))
    Text("More languages are coming later.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
}


@Composable
private fun AccountPage(onBack: () -> Unit, sub: String?, openSub: (String?) -> Unit) {
    val profile by ProfileRepository.profile.collectAsState()
    when (sub) {
        "username" -> {
            EditUsername(profile.username) { openSub(null) }
            return
        }
        "bio" -> {
            EditBio(profile.bio) { openSub(null) }
            return
        }
    }
    val pickPhoto = rememberAvatarFlow { ProfileRepository.save(avatar = it) }
    val state by AuthRepository.state.collectAsState()
    val s = state as? AuthState.SignedIn
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AvatarEditable(profile.avatar, profile.username.ifBlank { s?.name.orEmpty() }, 96.dp, onClick = pickPhoto)
            }
            Spacer(Modifier.height(20.dp))
            ValueRow("Username", profile.username.ifBlank { s?.name.orEmpty() }) { openSub("username") }
            ValueRow("Bio", profile.bio.ifBlank { "Add a bio" }, placeholder = profile.bio.isBlank()) { openSub("bio") }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(if (s?.google == true) R.drawable.google_g_logo else R.drawable.fluent_mail_filled),
                    contentDescription = null,
                    tint = if (s?.google == true) Color.Unspecified else Muted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text("Email", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(s?.email.orEmpty(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Spacer(Modifier.height(12.dp))
            ChevronRowPadless("Logout") { confirmLogout = true }
            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }
        }
        Text(
            "Delete account",
            color = Muted,
            fontSize = 14.sp,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { confirmDelete = true }
                .padding(vertical = 18.dp),
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
private fun ValueRow(label: String, value: String, placeholder: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(45.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.size(16.dp))
        Text(
            value,
            color = if (placeholder) DisabledText else Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.fluent_chevron_right_regular),
            contentDescription = null,
            tint = Color(0xFF5E5E63),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun EditUsername(current: String, done: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    val ok = ProfileRepository.validUsername(name)
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            UnderlineField(
                value = name,
                onChange = { if (it.length <= ProfileRepository.MAX_USERNAME) name = it.trim() },
                hint = "Username",
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (ok) {
                        ProfileRepository.save(username = name)
                        done()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            CheckBox("3-20 characters: letters, numbers, _ or .", ok)
        }
        PrimaryButton(
            label = "Save",
            enabled = ok && name.trim() != current,
            busy = false,
            onClick = {
                ProfileRepository.save(username = name)
                done()
            },
        )
    }
}

@Composable
private fun EditBio(current: String, done: () -> Unit) {
    var bio by remember { mutableStateOf(current) }
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            UnderlineField(
                value = bio,
                onChange = { if (it.length <= ProfileRepository.MAX_BIO) bio = it },
                hint = "Bio",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
                onImeAction = {},
                singleLine = false,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${bio.length} / ${ProfileRepository.MAX_BIO}",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryButton(
            label = "Save",
            enabled = bio.trim() != current,
            busy = false,
            onClick = {
                ProfileRepository.save(bio = bio)
                done()
            },
        )
    }
}

@Composable
private fun ChevronRowPadless(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(45.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.fluent_chevron_right_regular),
            contentDescription = null,
            tint = Color(0xFF5E5E63),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF18181B))
                .padding(24.dp),
        ) {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp)
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 20.dp, vertical = 14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    confirm,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KiroAccent)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}
