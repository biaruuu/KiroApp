package com.biaruuu.kiro.ui.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Interface language. Text goes through [tr]; English passes through untouched. */
object I18n {
    const val EN = "en"
    const val FIL = "fil"
    private const val FILE = "kiro_user_data"

    var lang by mutableStateOf(EN)
        private set

    fun init(c: Context) {
        loaded = true
        lang = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("lang", EN) ?: EN
    }

    private var loaded = false
    /** Loads the saved language once, for code that runs before the UI (workers, toasts). */
    fun ensure(c: Context) { if (!loaded) { loaded = true; init(c) } }

    fun set(c: Context, code: String) {
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("lang", code).apply()
        lang = code
    }

    private val patterns: List<Pair<Regex, (MatchResult) -> String>> by lazy {
        fun p(re: String, f: (MatchResult) -> String) = Regex(re) to f
        listOf(
            p("^Episode (\\S+) is out now$") { "Lumabas na ang Episode ${it.groupValues[1]}" },
            p("^Episode (\\S+) · (.+)$") { "Episode ${it.groupValues[1]} · ${it.groupValues[2]}" },
            p("^Episode (\\d+)$") { "Episode ${it.groupValues[1]}" },
            p("^Delete episode (\\S+)\\?$") { "Burahin ang episode ${it.groupValues[1]}?" },
            p("^Delete (.+)\\?$") { "Burahin ang ${it.groupValues[1]}?" },
            p("^Deleted (\\d+) episodes?$") { "Nabura ang ${it.groupValues[1]} episode" },
            p("^Removes (\\d+) downloaded episodes?\\.?(.*)$") { "Aalisin ang ${it.groupValues[1]} na-download na episode.${it.groupValues[2]}" },
            p("^Downloading (\\d+)% · (.+)$") { "Dina-download ${it.groupValues[1]}% · ${it.groupValues[2]}" },
            p("^Downloading · (.+)$") { "Dina-download · ${it.groupValues[1]}" },
            p("^Ready to watch · (.+)$") { "Handa nang panoorin · ${it.groupValues[1]}" },
            p("^Downloads use (.+)$") { "Gumagamit ng ${it.groupValues[1]} ang mga download" },
            p("^Kiro is using (.+)$") { "Gumagamit ang Kiro ng ${it.groupValues[1]}" },
            p("^Freed (.+)$") { "Nagbakante ng ${it.groupValues[1]}" },
            p("^Hello, (.+)!$") { "Kumusta, ${it.groupValues[1]}!" },
            p("^Kiro (\\S+) is available$") { "Available na ang Kiro ${it.groupValues[1]}" },
            p("^Current Version (.+)$") { "Kasalukuyang Bersyon ${it.groupValues[1]}" },
            p("^Latest Version (.+)$") { "Pinakabagong Bersyon ${it.groupValues[1]}" },
            p("^Version (.+)$") { "Bersyon ${it.groupValues[1]}" },
            p("^Last seen (.+)$") { "Huling nakita ${it.groupValues[1]}" },
            p("^Resend email \\((.+)\\)$") { "Ipadala muli ang email (${it.groupValues[1]})" },
            p("^Sending as (.+)$") { "Ipapadala bilang ${it.groupValues[1]}" },
            p("^Achievement unlocked: (.+)$") { "Na-unlock ang achievement: ${it.groupValues[1]}" },
            p("^Couldn't play this video \\((.+)\\)$") { "Hindi ma-play ang video na ito (${it.groupValues[1]})" },
            p("^Couldn't publish: (.+)$") { "Hindi na-publish: ${it.groupValues[1]}" },
            p("^Couldn't save: (.+)$") { "Hindi na-save: ${it.groupValues[1]}" },
            p("^Watching: (.+)$") { "Pinapanood: ${it.groupValues[1]}" },
            p("^Next Episode(.*)$") { "Susunod na Episode${it.groupValues[1]}" },
            p("^Skipping (.+)$") { "Nilalaktawan ang ${it.groupValues[1]}" },
            p("^Tried: (.+)$") { "Sinubukan: ${it.groupValues[1]}" },
            p("^Provider: (.+)$") { "Provider: ${it.groupValues[1]}" },
            p("^Watch (.+) with me on Kiro! Open Me > Watch party and enter the code (.+)$") {
                "Panoorin ang ${it.groupValues[1]} kasama ako sa Kiro! Buksan ang Ako > Watch party at ilagay ang code ${it.groupValues[2]}"
            },
            p("^(\\d+) episodes?$") { "${it.groupValues[1]} episode" },
            p("^In release order · (\\d+) entries$") { "Ayon sa petsa ng labas · ${it.groupValues[1]} entry" },
            p("^(\\d+) watched$") { "${it.groupValues[1]} napanood" },
            p("^(.+) · (\\d+) episodes?$") { "${I18n.tr(it.groupValues[1])} · ${it.groupValues[2]} episode" },
            p("^(\\d+) shows?$") { "${it.groupValues[1]} palabas" },
            p("^(\\d+)m ago$") { "${it.groupValues[1]}m ang nakalipas" },
            p("^(\\d+)h ago$") { "${it.groupValues[1]}h ang nakalipas" },
            p("^(\\d+)d ago$") { "${it.groupValues[1]}d ang nakalipas" },
            p("^just now$") { "ngayon lang" },
            p("^No alerts from (.+) to (.+)\\. Episodes that air meanwhile are announced afterwards\\.$") {
                "Walang alerto mula ${it.groupValues[1]} hanggang ${it.groupValues[2]}. Iaanunsyo pagkatapos ang mga episode na ipapalabas sa pagitan."
            },
        )
    }

    /** Translates [s]. Unknown text falls back to English, so nothing ever breaks. */
    fun tr(s: String): String {
        if (lang != FIL || s.isEmpty()) return s
        FIL_EXACT[s]?.let { return it }
        FIL_ORDER[s]?.let { return it }
        for ((re, f) in patterns) {
            val m = re.matchEntire(s) ?: continue
            return f(m)
        }
        return s
    }
}

/** Toast that is translated into the chosen language. */
object I18nToast {
    fun make(context: Context, text: CharSequence, duration: Int): android.widget.Toast {
        I18n.ensure(context)
        return android.widget.Toast.makeText(context, I18n.tr(text.toString()), duration)
    }
}
