package com.biaruuu.kiro.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.biaruuu.kiro.BuildConfig
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.google.firebase.firestore.FirebaseFirestore

/** One-tap "this episode doesn't play": goes to the developer's admin panel, or by email when signed out. */
object BrokenReport {
    class Info(
        val animeId: Int,
        val title: String,
        val episode: Int,
        val provider: String,
        val quality: String,
        val triedProviders: List<String>,
        val problem: String,
    )

    /** Returns true when the report was sent from here; false when the email app was opened instead. */
    fun send(c: Context, info: Info, onResult: (Boolean) -> Unit) {
        val state = AuthRepository.state.value
        if (state is AuthState.SignedIn) {
            val data = hashMapOf<String, Any>(
                "uid" to state.uid,
                "animeId" to info.animeId,
                "title" to info.title,
                "episode" to info.episode,
                "provider" to info.provider,
                "quality" to info.quality,
                "tried" to info.triedProviders.joinToString(", "),
                "problem" to info.problem,
                "version" to BuildConfig.VERSION_NAME,
                "device" to "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
                "createdAt" to System.currentTimeMillis(),
            )
            try {
                FirebaseFirestore.getInstance().collection("reports").add(data)
                    .addOnSuccessListener { onResult(true) }
                    .addOnFailureListener { emailFallback(c, info); onResult(false) }
                return
            } catch (_: Exception) {
            }
        }
        emailFallback(c, info)
        onResult(false)
    }

    private fun emailFallback(c: Context, info: Info) {
        val body = "Show: ${info.title} (id ${info.animeId})\nEpisode: ${info.episode}\nProvider: ${info.provider}\n" +
            "Quality: ${info.quality}\nTried: ${info.triedProviders.joinToString(", ")}\nProblem: ${info.problem}\n" +
            "Kiro ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:${Admin.EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, "Kiro: ${info.title} episode ${info.episode} doesn't play")
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            c.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
