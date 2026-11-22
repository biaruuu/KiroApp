package com.biaruuu.kiro.ui.party

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vanniktech.emoji.EmojiEditText
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.EmojiTextView
import com.vanniktech.emoji.ios.IosEmojiProvider

/** Called once from the Application: every emoji drawn by the views below uses Apple's artwork. */
fun installIosEmoji() {
    EmojiManager.install(IosEmojiProvider())
}

/** Text that draws its emoji as iOS images, whatever the phone's own emoji font looks like. */
@Composable
fun EmojiText(
    text: String,
    color: Color,
    fontSize: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    bold: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            EmojiTextView(ctx).apply {
                includeFontPadding = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { v ->
            v.setTextColor(color.toArgb())
            v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)
            v.maxLines = maxLines
            if (maxLines != Int.MAX_VALUE) v.ellipsize = android.text.TextUtils.TruncateAt.END
            v.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            if (v.text.toString() != text) v.text = text
        },
    )
}

/**
 * The chat input. Emoji typed from the phone's keyboard or picked from our grid are turned into
 * iOS images straight away. [onReady] hands back the field so the grid can insert at the cursor.
 */
@Composable
fun EmojiInput(
    hint: String,
    textColor: Color,
    hintColor: Color,
    modifier: Modifier = Modifier,
    onReady: (EditText) -> Unit,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val sendNow by rememberUpdatedState(onSend)
    val changed by rememberUpdatedState(onChange)
    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            EmojiEditText(ctx).apply {
                this.hint = hint
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                // A real touch target: tapping anywhere in the bar focuses it and opens the keyboard.
                minimumHeight = (44 * ctx.resources.displayMetrics.density).toInt()
                gravity = android.view.Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                maxLines = 4
                setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                imeOptions = EditorInfo.IME_ACTION_SEND
                filters = arrayOf(android.text.InputFilter.LengthFilter(500))
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEND) { sendNow(); true } else false
                }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) { changed(s?.toString().orEmpty()) }
                })
                onReady(this)
            }
        },
        update = { v ->
            v.setTextColor(textColor.toArgb())
            v.setHintTextColor(hintColor.toArgb())
            v.textSize = 15f
        },
    )
}

private val emojiGroups: List<Pair<String, List<String>>> = listOf(
    "😀" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😋","😛","😜","🤪","😝","🤗","🤭","🤔","🤨","😐","😑","😶","😏","😒","🙄","😬","😮‍💨","😴","😪","🤤","😷","🤒","🤕","🤢","🤮","🥵","🥶","🥴","😵","🤯","🤠","🥳","😎","🤓","😕","😟","🙁","😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","🤡","👻","👽","🤖"),
    "👍" to listOf("👍","👎","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","👇","☝️","👋","🤚","🖐️","✋","🖖","👏","🙌","🤲","🤝","🙏","💪","🫶","🫡","🤳","✍️","💅","👀","👁️","👅","👄","🧠"),
    "❤️" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🔥","💕","💞","💓","💗","💖","💘","💝","💟","♥️","💋","💯","💢","💥","💫","💦","💨","🔥","✨","⭐","🌟","⚡","🎉","🎊","🎈","🎁"),
    "🐶" to listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧","🐦","🦆","🦉","🐺","🐴","🦄","🐝","🦋","🐌","🐞","🐢","🐍","🐙","🦀","🐠","🐬","🐳","🦈","🌸","🌹","🌻","🌈","☀️","🌙","❄️","🌊"),
    "🍔" to listOf("🍎","🍊","🍋","🍌","🍉","🍇","🍓","🍒","🍑","🥭","🍍","🥥","🥑","🍅","🥕","🌽","🍞","🧀","🍗","🍖","🍔","🍟","🍕","🌭","🌮","🍜","🍝","🍣","🍙","🍚","🍰","🎂","🍩","🍪","🍫","🍬","🍭","🍿","☕","🍵","🥤","🍺","🍷","🍹"),
    "⚽" to listOf("⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏓","🥊","🎮","🕹️","🎲","🎯","🎬","🎤","🎧","🎸","🎹","🥁","🎨","📚","📷","📱","💻","🎥","📺","🚗","✈️","🚀","🏠","💰","💎","🔔","🎵","🎶"),
    "🔣" to listOf("✅","❌","⭕","❗","❓","‼️","⚠️","🚫","💤","➕","➖","➡️","⬅️","⬆️","⬇️","🔁","🔄","▶️","⏸️","⏩","⏪","🔊","🔇","🆗","🆕","🔝","🆒","💬","💭","🗯️","🔴","🟢","🔵","⚪","⚫"),
)

/** A picker grid of emoji (shown as iOS images) with a row of category tabs, for the chat. */
@Composable
fun EmojiPickerPanel(height: Dp, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var group by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth().height(height).background(Color(0xFF18181B))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            emojiGroups.forEachIndexed { i, g ->
                Box(
                    Modifier.weight(1f).height(42.dp).clickable { group = i },
                    contentAlignment = Alignment.Center,
                ) {
                    EmojiText(g.first, Color.White, 20f, Modifier.padding(bottom = 2.dp))
                    if (i == group) Box(Modifier.align(Alignment.BottomCenter).size(width = 22.dp, height = 2.dp).background(Color.White))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF26262B)))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        ) {
            items(emojiGroups[group].second) { e ->
                Box(Modifier.height(44.dp).clickable { onPick(e) }, contentAlignment = Alignment.Center) {
                    EmojiText(e, Color.White, 26f)
                }
            }
        }
    }
}
