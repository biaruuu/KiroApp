package com.biaruuu.kiro.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

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

private const val ROBOTO_ASSET = "fonts/Roboto-Variable.ttf"

/**
 * Roboto (Google Fonts) is bundled as a variable font in assets/fonts. If the file is
 * not present we fall back to the system default font, which is Roboto on stock Android.
 */
@OptIn(ExperimentalTextApi::class)
private fun loadRoboto(assets: AssetManager): FontFamily {
    val present = try {
        assets.list("fonts")?.contains("Roboto-Variable.ttf") == true
    } catch (_: Exception) {
        false
    }
    if (!present) return FontFamily.Default

    return try {
        FontFamily(
            listOf(400, 500, 700, 900).map { weight ->
                Font(
                    path = ROBOTO_ASSET,
                    assetManager = assets,
                    weight = FontWeight(weight),
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
                )
            },
        )
    } catch (_: Exception) {
        FontFamily.Default
    }
}

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

@Composable
fun KiroTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember {
        Typography().withFont(loadRoboto(context.assets))
    }
    MaterialTheme(
        colorScheme = KiroColors,
        typography = typography,
        content = content,
    )
}
