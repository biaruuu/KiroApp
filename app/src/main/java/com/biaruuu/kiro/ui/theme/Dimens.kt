package com.biaruuu.kiro.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material window width classes: phones, small tablets / landscape phones, large tablets. */
enum class WindowClass { Compact, Medium, Expanded }

/**
 * Every size that changes with the screen lives here so the screens themselves stay free of
 * magic numbers. Built from the current window width by [dimensFor].
 */
@Immutable
data class Dimens(
    val windowClass: WindowClass,
    val gutter: Dp,
    val rowSpacing: Dp,
    val cardSpacing: Dp,
    val cardWidth: Dp,
    val gridMinCell: Dp,
    val heroAspect: Float,
    val heroTitle: TextUnit,
    val heroTagline: TextUnit,
    val sectionTitle: TextUnit,
    val wordmark: TextUnit,
    val searchMaxWidth: Dp,
    val navRailWidth: Dp,
)

fun windowClassFor(widthDp: Int): WindowClass = when {
    widthDp < 600 -> WindowClass.Compact
    widthDp < 840 -> WindowClass.Medium
    else -> WindowClass.Expanded
}

fun dimensFor(widthDp: Int): Dimens {
    val windowClass = windowClassFor(widthDp)
    return when (windowClass) {
        WindowClass.Compact -> {
            val gutter = 16
            // Show about 2.6 cards so the next one peeks in and hints at scrolling.
            Dimens(
                windowClass = windowClass,
                gutter = gutter.dp,
                rowSpacing = 28.dp,
                cardSpacing = 8.dp,
                // About 2.7 cards fit, so the third peeks in and hints that the row scrolls.
                cardWidth = ((widthDp - gutter * 1.5f) / 2.7f).coerceIn(104f, 160f).dp,
                gridMinCell = 108.dp,
                heroAspect = 0.68f,
                heroTitle = 26.sp,
                heroTagline = 15.sp,
                sectionTitle = 22.sp,
                wordmark = 26.sp,
                searchMaxWidth = 720.dp,
                navRailWidth = 0.dp,
            )
        }

        WindowClass.Medium -> {
            val gutter = 24
            Dimens(
                windowClass = windowClass,
                gutter = gutter.dp,
                rowSpacing = 32.dp,
                cardSpacing = 10.dp,
                cardWidth = ((widthDp - gutter) / 4.4f).coerceIn(130f, 190f).dp,
                gridMinCell = 128.dp,
                heroAspect = 2.0f,
                heroTitle = 36.sp,
                heroTagline = 18.sp,
                sectionTitle = 24.sp,
                wordmark = 30.sp,
                searchMaxWidth = 720.dp,
                navRailWidth = 88.dp,
            )
        }

        WindowClass.Expanded -> {
            val gutter = 32
            Dimens(
                windowClass = windowClass,
                gutter = gutter.dp,
                rowSpacing = 36.dp,
                cardSpacing = 12.dp,
                cardWidth = ((widthDp - gutter - 80) / 6.3f).coerceIn(150f, 210f).dp,
                gridMinCell = 148.dp,
                heroAspect = 2.5f,
                heroTitle = 46.sp,
                heroTagline = 20.sp,
                sectionTitle = 26.sp,
                wordmark = 32.sp,
                searchMaxWidth = 800.dp,
                navRailWidth = 96.dp,
            )
        }
    }
}

val LocalDimens = staticCompositionLocalOf { dimensFor(360) }
