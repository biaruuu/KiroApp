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

val KiroPurple = Color(0xFF7B6CF6)
val KiroBackground = Color(0xFF121212)
val KiroSurface = Color(0xFF1E1E22)
val KiroSurfaceHigh = Color(0xFF2A2A30)
val KiroOnSurfaceMuted = Color(0xFF9A9AA5)

private val KiroColors = darkColorScheme(
    primary = KiroPurple,
    onPrimary = Color.White,
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

/** Roboto (Google Fonts), bundled in res/font. */
val Roboto = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold),
    Font(R.font.roboto_black, FontWeight.Black),
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

private val KiroTypography = Typography().withFont(Roboto)

@Composable
fun KiroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KiroColors,
        typography = KiroTypography,
    ) {
        // Makes plain Text() calls (no explicit style) use Roboto too.
        ProvideTextStyle(KiroTypography.bodyMedium, content)
    }
}
