package com.biaruuu.kiro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.biaruuu.kiro.R

/** The one accent: white for active states and progress, dark ink on top of it. */
val KiroAccent = Color.White
val KiroOnAccent = Color.Black
val KiroBackground = Color(0xFF121212)
val KiroSurface = Color(0xFF1E1E22)
val KiroSurfaceHigh = Color(0xFF2A2A30)
val KiroOnSurfaceMuted = Color(0xFF9A9AA5)

private val KiroColors = darkColorScheme(
    primary = KiroAccent,
    onPrimary = KiroOnAccent,
    background = KiroBackground,
    onBackground = Color.White,
    surface = KiroBackground,
    onSurface = Color.White,
    surfaceVariant = KiroSurface,
    onSurfaceVariant = KiroOnSurfaceMuted,
    secondaryContainer = KiroSurfaceHigh,
    onSecondaryContainer = Color.White,
    error = Color(0xFFFF6B6B),
)

/**
 * Netflix Sans, bundled in res/font. The family ships Light, Regular, Medium and Bold, so Black
 * (used by the KIRO wordmark) is mapped to the Bold file.
 */
val AppFont = FontFamily(
    Font(R.font.netflix_sans_light, FontWeight.Light),
    Font(R.font.netflix_sans_regular, FontWeight.Normal),
    // Minimal look: "Medium" renders as Regular, and every bold weight renders as Medium.
    Font(R.font.netflix_sans_regular, FontWeight.Medium),
    Font(R.font.netflix_sans_medium, FontWeight.SemiBold),
    Font(R.font.netflix_sans_medium, FontWeight.Bold),
    Font(R.font.netflix_sans_medium, FontWeight.ExtraBold),
    Font(R.font.netflix_sans_bold, FontWeight.Black),
)

private fun Typography.withFont(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

private val KiroTypography = Typography().withFont(AppFont)

@Composable
fun KiroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KiroColors,
        typography = KiroTypography,
    ) {
        // Makes plain Text() calls (no explicit style) use the app font too.
        ProvideTextStyle(KiroTypography.bodyMedium, content)
    }
}
