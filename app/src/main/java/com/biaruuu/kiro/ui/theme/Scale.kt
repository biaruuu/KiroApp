package com.biaruuu.kiro.ui.theme

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Sizes drawn on a 414-wide design, scaled to the real screen width so Home looks the same on a
 * small phone and a large one. Text ignores the system font size so the layout stays put.
 */
@Immutable
class Ui(val u: Float, private val fontScale: Float) {
    fun dp(pt: Float): Dp = (pt * u).dp
    fun sp(pt: Float): TextUnit = androidx.compose.ui.unit.TextUnit(pt * u / fontScale, androidx.compose.ui.unit.TextUnitType.Sp)
}

@Composable
fun rememberUi(): Ui {
    val width = LocalConfiguration.current.screenWidthDp
    val fontScale = LocalDensity.current.fontScale
    return remember(width, fontScale) { Ui((width / 414f).coerceIn(0.78f, 1.3f), fontScale) }
}

/** Presses shrink the element slightly, so every chip and button answers the touch. Place it first in the chain. */
fun androidx.compose.ui.Modifier.pressScale(
    source: androidx.compose.foundation.interaction.InteractionSource,
    pressedScale: Float = 0.95f,
): androidx.compose.ui.Modifier = androidx.compose.ui.composed {
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) pressedScale else 1f,
        androidx.compose.animation.core.tween(110),
        label = "press",
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
}
