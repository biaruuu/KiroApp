package com.biaruuu.kiro.ui.legal

import com.biaruuu.kiro.ui.theme.KiroText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.biaruuu.kiro.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val UPDATED = "Last updated: November 16, 2026"
private const val CONTACT = "Questions? Use Contact us in the Me tab."

private val TermsSections = listOf(
    "1. Welcome to Kiro" to "These Terms of Use apply to your use of the Kiro app. By creating an account or using Kiro you agree to them. If you do not agree, please do not use the app.",
    "2. Who can use Kiro" to "You must be at least 13 years old, or the minimum age of digital consent in your country. If you are under 18, use Kiro with the permission of a parent or guardian.",
    "3. Your account" to "You can browse as a guest or sign up with an email address or a Google account. Email accounts must be verified. You are responsible for keeping your login details safe and for everything that happens under your account. Tell us if you think someone else is using it.",
    "4. Guest mode" to "Guests can look around the app, but nothing is saved: Favourites, ratings, Continue Watching and watched episodes need an account.",
    "5. What Kiro is" to "Kiro is an anime discovery and viewing app. Show information comes from public services such as AniList and TMDB, and trailers are played through YouTube. Kiro does not host or upload video files. Episodes are found through third-party sources, and their availability, quality and legality in your country are outside our control. You are responsible for making sure your use follows the laws where you live.",
    "6. Your profile" to "Your username, bio and profile photo are part of your account. Do not use names, text or pictures that are unlawful, hateful, harassing, sexually explicit, or that pretend to be someone else. We may ask you to change them or remove them.",
    "7. Acceptable use" to "Do not misuse Kiro. That includes trying to break or overload it, bypass its limits or security, scrape or copy its data in bulk, interfere with other people's accounts, or use it for anything unlawful.",
    "8. Intellectual property" to "Kiro's name, logo and design belong to its owner. Anime titles, artwork, trailers and other media belong to their respective owners and are shown for the purpose of identifying and describing the shows.",
    "9. Ending your use" to "You can stop using Kiro at any time. To delete your account and its saved data, open Me, Account Setting, then Delete account. We may suspend or remove accounts that break these terms.",
    "10. No warranty" to "Kiro is provided \"as is\" and \"as available\", without promises that it will always work, be error-free, or that any episode or trailer will be available.",
    "11. Limit of liability" to "To the extent the law allows, Kiro and its contributors are not liable for indirect or consequential losses, or for loss of data, arising from your use of the app.",
    "12. Changes" to "We may update these terms as Kiro changes. The date above shows the latest version. Using Kiro after a change means you accept the new terms.",
)

private val PrivacySections = listOf(
    "1. Overview" to "This policy explains what Kiro collects, why, and the choices you have. We keep it to what the app needs to work.",
    "2. What we collect" to "Account: your email address and, if you use Google sign-in, your Google account's basic profile. Passwords are handled by Firebase Authentication and are never visible to us.\n\nProfile: the username, bio and profile photo you choose.\n\nLibrary: your Favourites, ratings, Continue Watching positions and the episodes you have watched, which are saved to your account so they follow you between devices.\n\nActivity: while you are signed in and Kiro is open, whether you are online and the title and episode you are watching. Only the Kiro developer can see this.\n\nOn your device: settings such as download and playback options, recent searches, and the last login method.",
    "3. How we use it" to "To sign you in, show your profile, keep your library in sync in real time, and remember where you stopped watching. We do not sell your data and we do not show ads.",
    "4. Where it is stored" to "Account sign-in uses Firebase Authentication and your profile and library are stored in Cloud Firestore, both provided by Google. Data is stored under your account and the security rules only let you read and write your own data.",
    "5. Services Kiro connects to" to "To show anime information, Kiro requests data from AniList and TMDB. Trailers play through YouTube, and episodes are loaded from third-party streaming sources. These services receive normal connection details such as your IP address and are covered by their own privacy policies. Kiro does not include advertising or analytics tools.",
    "6. Guests" to "If you continue as a guest, your library is not saved and nothing about your viewing is sent to your account.",
    "7. Keeping and deleting data" to "We keep your profile and library while your account exists. When you choose Delete account in Account Setting, your profile and library are deleted and your sign-in is removed. Logging out clears the library from that device.",
    "8. Your choices" to "You can change your username, bio and photo at any time in Account Setting, turn off autoplay previews and Wi-Fi-only downloads in settings, clear recent searches, and delete your account.",
    "9. Security" to "Data is sent over encrypted connections and protected by Firebase security rules. No method is perfectly secure, so please use a strong, unique password.",
    "10. Children" to "Kiro is not meant for children under 13, and we do not knowingly collect their information. If you believe a child has made an account, contact us and we will remove it.",
    "11. Changes" to "If this policy changes, the date above will change too. Please check back from time to time.",
)

/** A scrolling legal page: a short intro, then numbered sections. */
@Composable
fun LegalContent(terms: Boolean, modifier: Modifier = Modifier) {
    val sections = if (terms) TermsSections else PrivacySections
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Text(UPDATED, color = Color(0xFF8E8E93), fontSize = 12.sp)
        for ((heading, body) in sections) {
            Spacer(Modifier.height(22.dp))
            Text(heading, color = KiroText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Color(0xFFB4B4BA), fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(26.dp))
        Text(CONTACT, color = Color(0xFF8E8E93), fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(32.dp))
    }
}
