package com.biaruuu.kiro.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.biaruuu.kiro.ui.components.MessageState

@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    MessageState(
        title = title,
        subtitle = "Coming in a future update.",
        modifier = modifier.statusBarsPadding(),
    )
}
