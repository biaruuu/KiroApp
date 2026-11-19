package com.biaruuu.kiro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.ui.i18n.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far a streak has come: each tier has its own colours and a richer animation. */
class StreakTier(
    val index: Int,
    val minDays: Int,
    val name: String,
    val top: Color,
    val bottom: Color,
    val accent: Color,
) {
    companion object {
        val ALL = listOf(
            StreakTier(0, 0, "No streak", Color(0xFF6B6B72), Color(0xFF3A3A40), Color(0xFF8E8E93)),
            StreakTier(1, 1, "Spark", Color(0xFFFFC857), Color(0xFFFF8A00), Color(0xFFFFD98A)),
            StreakTier(2, 3, "Flame", Color(0xFFFFA24D), Color(0xFFFF4D00), Color(0xFFFFC38A)),
            StreakTier(3, 7, "Blaze", Color(0xFFFF7A6B), Color(0xFFE0131F), Color(0xFFFFB0A8)),
            StreakTier(4, 14, "Inferno", Color(0xFFFF6FB0), Color(0xFFD1146A), Color(0xFFFFB3D6)),
            StreakTier(5, 30, "Violet Fire", Color(0xFFBE8CFF), Color(0xFF6A2BE3), Color(0xFFE0CCFF)),
            StreakTier(6, 60, "Blue Flame", Color(0xFF7CEBFF), Color(0xFF1E6BFF), Color(0xFFC4F6FF)),
            StreakTier(7, 100, "Golden Blaze", Color(0xFFFFEA8A), Color(0xFFFF9F1C), Color(0xFFFFF3B8)),
            StreakTier(8, 200, "Legend", Color(0xFFFFE27A), Color(0xFFFF4DA6), Color(0xFFFFFFFF)),
        )

        fun of(days: Int): StreakTier = ALL.last { days >= it.minDays }
        fun next(days: Int): StreakTier? = ALL.firstOrNull { it.minDays > days }
    }
}

private fun flamePath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.50f, 0f)
    cubicTo(w * 0.86f, h * 0.30f, w * 0.98f, h * 0.55f, w * 0.82f, h * 0.80f)
    cubicTo(w * 0.72f, h * 0.96f, w * 0.28f, h * 0.99f, w * 0.18f, h * 0.80f)
    cubicTo(w * 0.06f, h * 0.58f, w * 0.20f, h * 0.40f, w * 0.33f, h * 0.30f)
    cubicTo(w * 0.36f, h * 0.42f, w * 0.43f, h * 0.45f, w * 0.47f, h * 0.40f)
    cubicTo(w * 0.42f, h * 0.24f, w * 0.44f, h * 0.10f, w * 0.50f, 0f)
    close()
}

private fun DrawScope.star(center: Offset, r: Float, color: Color, alpha: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(p, color.copy(alpha = alpha.coerceIn(0f, 1f)))
}

/**
 * The streak flame. It flickers; from the third tier embers rise, higher tiers add a pulsing
 * halo, orbiting sparkles and a shine sweep, and the top tier shimmers through every colour.
 * A streak that is not lit today (nothing watched yet) is shown dimmed.
 */
@Composable
fun StreakBadge(streak: Int, litToday: Boolean, size: Dp, modifier: Modifier = Modifier, showNumber: Boolean = true) {
    val tier = StreakTier.of(streak)
    val lit = streak > 0 && litToday
    val idle = streak > 0 && !litToday

    val t = rememberInfiniteTransition(label = "streak")
    val flicker by t.animateFloat(0.96f, 1.05f, infiniteRepeatable(tween(if (lit) 520 else 1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flicker")
    val sway by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(if (lit) 900 else 2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val glow by t.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val loop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "loop")
    val hueLoop by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "hue")

    // Pops in when it appears and counts up to the number of days.
    val pop = remember { Animatable(0.6f) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(streak) {
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(streak) {
        val anim = Animatable(0f)
        anim.animateTo(streak.toFloat(), tween(900, easing = FastOutSlowInEasing)) { shown = value.toInt() }
        shown = streak
    }

    val topColor: Color
    val bottomColor: Color
    if (tier.index == 8 && lit) {
        topColor = Color.hsv(hueLoop % 360f, 0.55f, 1f)
        bottomColor = Color.hsv((hueLoop + 70f) % 360f, 0.85f, 0.95f)
    } else {
        topColor = tier.top
        bottomColor = tier.bottom
    }
    val dim = if (idle) 0.45f else 1f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            val w = this.size.width
            val h = this.size.height
            val fw = w * 0.66f
            val fh = h * 0.78f
            val left = (w - fw) / 2f
            val top = h * 0.14f

            // Halo behind the flame.
            if (tier.index >= 3 && streak > 0) {
                val a = (if (lit) glow else 0.18f) * (if (tier.index >= 4) 0.85f else 0.55f)
                // Kept inside the canvas so it fades out instead of showing a square edge.
                drawCircle(
                    Brush.radialGradient(listOf(topColor.copy(alpha = a), Color.Transparent), center = Offset(w / 2, h * 0.55f), radius = w * 0.48f),
                    radius = w * 0.48f, center = Offset(w / 2, h * 0.55f),
                )
            }

            // Rising embers (tier 2 and up).
            if (lit && tier.index >= 2) {
                val n = if (tier.index >= 7) 12 else if (tier.index >= 5) 9 else if (tier.index >= 3) 7 else 5
                for (i in 0 until n) {
                    val p = (loop + i / n.toFloat()) % 1f
                    val x = w * (0.30f + 0.40f * ((i * 37 % 100) / 100f)) + sin((p * 2 * PI + i).toFloat()) * w * 0.04f
                    val y = h * (0.80f - 0.72f * p)
                    drawCircle(tier.accent.copy(alpha = (1f - p) * 0.9f), radius = w * (0.016f + 0.012f * ((i * 13 % 10) / 10f)), center = Offset(x, y))
                }
            }

            // The flame: outer body, then a lighter core.
            rotate(sway * (if (lit) 1f else 0.3f), pivot = Offset(w / 2f, h * 0.9f)) {
                scale(flicker, flicker, pivot = Offset(w / 2f, h * 0.92f)) {
                    val outer = flamePath(fw, fh)
                    translate(left, top) {
                        drawPath(
                            outer,
                            Brush.verticalGradient(
                                listOf(
                                    (if (idle || streak == 0) tier.top else topColor).copy(alpha = dim),
                                    (if (idle || streak == 0) tier.bottom else bottomColor).copy(alpha = dim),
                                ),
                                startY = 0f, endY = fh,
                            ),
                        )
                        scale(0.56f, 0.52f, pivot = Offset(fw / 2f, fh * 0.96f)) {
                            drawPath(
                                outer,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.92f * dim), tier.accent.copy(alpha = 0.85f * dim)),
                                    startY = 0f, endY = fh,
                                ),
                            )
                        }
                    }
                }
            }

            // Orbiting sparkles (tier 5 and up).
            if (lit && tier.index >= 5) {
                val count = if (tier.index >= 7) 4 else 3
                for (i in 0 until count) {
                    val ang = (loop + i / count.toFloat()) * 2f * PI.toFloat()
                    val cx = w / 2f + cos(ang) * w * 0.44f
                    val cy = h * 0.52f + sin(ang) * h * 0.34f
                    val pulse = 0.55f + 0.45f * sin((loop * 2f + i * 0.4f) * PI.toFloat() * 2f)
                    star(Offset(cx, cy), w * (0.07f + 0.03f * pulse), tier.accent, 0.5f + 0.5f * pulse)
                }
            }

            // Shine sweep across the flame (tier 7 and up).
            if (lit && tier.index >= 7) {
                val x = -w * 0.3f + (w * 1.6f) * ((loop * 1.4f) % 1f)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                        start = Offset(x, 0f), end = Offset(x + w * 0.25f, h * 0.4f),
                    ),
                    topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(fw, fh),
                )
            }
        }
        if (showNumber && streak > 0) {
            Text(
                "$shown",
                color = Color.White.copy(alpha = if (idle) 0.75f else 1f),
                fontWeight = FontWeight.Black,
                fontSize = (size.value * (if (shown >= 100) 0.27f else 0.34f)).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; translationY = this.size.height * 0.16f },
            )
        }
    }
}
