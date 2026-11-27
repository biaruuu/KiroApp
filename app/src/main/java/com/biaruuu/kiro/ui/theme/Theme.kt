package com.biaruuu.kiro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.R

/** The one accent: white for active states and progress, dark ink on top of it. */
/** Primary text and labels: crisp white on black. */
val KiroText = Color(0xFFFFFFFF)
val KiroAccent = KiroText
val KiroOnAccent = Color.Black
val KiroBackground = Color(0xFF000000)
val KiroSurface = Color(0xFF1C1C1E)
val KiroSurfaceHigh = Color(0xFF2C2C2E)
/** Secondary labels: #EBEBF5 at 60%. */
val KiroOnSurfaceMuted = Color(0x99EBEBF5)
/** Hairlines and card outlines. */
val KiroOutline = Color(0xFF38383A)

private val KiroColors = darkColorScheme(
    primary = KiroAccent,
    onPrimary = KiroOnAccent,
    background = KiroBackground,
    onBackground = KiroText,
    surface = KiroBackground,
    onSurface = KiroText,
    surfaceVariant = KiroSurface,
    onSurfaceVariant = KiroOnSurfaceMuted,
    secondaryContainer = KiroSurfaceHigh,
    onSecondaryContainer = KiroText,
    outline = KiroOutline,
    outlineVariant = KiroOutline,
    error = Color(0xFFFF6B6B),
)

/** One soft radius scale for every card, sheet, dialog and field. */
private val KiroShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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

/** Calmer type scale: tighter headings, roomier body, small caps-free labels. */
private val KiroTypography = Typography().withFont(AppFont).let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = t.bodySmall.copy(lineHeight = 16.sp, letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(letterSpacing = 0.1.sp),
        labelMedium = t.labelMedium.copy(letterSpacing = 0.2.sp),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.3.sp),
    )
}

@Composable
fun KiroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KiroColors,
        typography = KiroTypography,
        shapes = KiroShapes,
    ) {
        // Makes plain Text() calls (no explicit style) use the app font too.
        ProvideTextStyle(KiroTypography.bodyMedium, content)
    }
}
