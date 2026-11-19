package com.biaruuu.kiro.ui.me

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.BuildConfig
import com.biaruuu.kiro.R
import com.biaruuu.kiro.data.ProfileRepository
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.ui.auth.Avatar
import com.biaruuu.kiro.data.auth.AuthState
import com.biaruuu.kiro.ui.theme.KiroPurple

private val Muted = Color(0xFF8E8E93)
private val Hairline = Color(0xFF2B2B2F)

/** The Me tab: account header and the settings list. [onOpen] pushes a settings page. */
@Composable
fun MeScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier, hasUnread: Boolean = false) {
    val context = LocalContext.current
    val configured = remember { AuthRepository.isConfigured(context) }
    val state by AuthRepository.state.collectAsState()
    val signedIn = state as? AuthState.SignedIn
    val profile by ProfileRepository.profile.collectAsState()
    val shownName = profile.username.ifBlank { signedIn?.name.orEmpty() }

    Box(modifier = modifier.statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            if (signedIn != null) {
                ProfileCard(
                    profile = profile,
                    name = shownName,
                    onEdit = { onOpen("account") },
                    onBadges = { onOpen("achievements") },
                    onStats = { onOpen("stats") },
                )
            } else {
            // Header: avatar + greeting. Tapping it opens the account page (or the login screen).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when {
                            signedIn != null -> onOpen("account")
                            configured -> AuthRepository.requestLogin()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.biaruuu.kiro.ui.components.DecoratedAvatar(profile.avatar, shownName.ifBlank { "K" }, 52.dp, profile.decoration)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (signedIn != null) "Hello, $shownName!" else "Hello, guest!",
                        fontFamily = com.biaruuu.kiro.data.NameStyles.family(context, profile.nameFont),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            signedIn != null -> signedIn.email
                            configured -> "Log in to sync your account"
                            else -> "Accounts aren't set up in this build"
                        },
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (signedIn != null || configured) Chevron()
            }
            }

            if (com.biaruuu.kiro.data.Admin.isAdmin(state)) {
                Spacer(Modifier.height(20.dp))
                SettingsRow(R.drawable.fluent_shield_regular, "Admin panel") { onOpen("admin") }
            }
            Spacer(Modifier.height(32.dp))
            Text("Settings", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            if (signedIn != null) SettingsRow(R.drawable.fluent_sparkle_regular, "Customize profile") { onOpen("customize") }
            SettingsRow(R.drawable.fluent_play_circle_regular, "Watch stats") { onOpen("stats") }
            SettingsRow(R.drawable.fluent_trophy_regular, "Achievements") { onOpen("achievements") }
            SettingsRow(R.drawable.fluent_alert_regular, "Notification", badge = hasUnread) { onOpen("notifications") }
            SettingsRow(R.drawable.fluent_calendar_regular, "Notification control") { onOpen("alerts") }
            SettingsRow(R.drawable.fluent_arrow_download_regular, "Storage") { onOpen("storage") }
            SettingsRow(R.drawable.fluent_inbox_regular, "Backup and restore") { onOpen("backup") }
            SettingsRow(R.drawable.fluent_people_regular, "Watch party") { onOpen("party") }
            SettingsRow(R.drawable.fluent_arrow_download_regular, "Download") { onOpen("download") }
            SettingsRow(R.drawable.fluent_play_circle_regular, "Play") { onOpen("play") }
            SettingsRow(R.drawable.fluent_language_regular, "Language") { onOpen("language") }

            Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Hairline))

            SettingsRow(R.drawable.fluent_help_circle_regular, "Help Center") { onOpen("help") }
            SettingsRow(R.drawable.fluent_headset_regular, "Contact us") { onOpen("contact") }
            SettingsRow(R.drawable.fluent_terms_regular, "Terms of Use") { onOpen("terms") }
            SettingsRow(R.drawable.fluent_shield_regular, "Privacy Policy") { onOpen("privacy") }
            SettingsRow(R.drawable.fluent_phone_regular, "Version", value = BuildConfig.VERSION_NAME.substringBefore('-')) {
                onOpen("version")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Chevron() {
    Icon(
        painter = painterResource(R.drawable.fluent_chevron_right_regular),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(28.dp),
    )
}

@Composable
private fun SettingsRow(@DrawableRes icon: Int, label: String, value: String? = null, badge: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.size(4.dp))
        }
        if (badge) {
            Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFFF4D4F)))
            Spacer(Modifier.size(6.dp))
        }
        Chevron()
    }
}
