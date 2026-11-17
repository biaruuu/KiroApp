package com.biaruuu.kiro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A spinning loading ring. It is driven by the frame clock directly, so it keeps turning even when
 * the phone has animations turned off or scaled to zero (where Material's spinner freezes).
 */
@Composable
fun KiroSpinner(modifier: Modifier = Modifier, size: Dp = 44.dp, color: Color = Color.White, strokeWidth: Dp = 3.dp) {
    var nanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { nanos = it }
        }
    }
    val t = (nanos / 1_000_000L) / 1000f
    val rotation = (t * 300f) % 360f                        // one turn every 1.2 s
    val sweep = 90f + 150f * (0.5f + 0.5f * kotlin.math.sin(t * 3.2f))  // the arc breathes
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(stroke),
        )
        drawArc(
            color = color,
            startAngle = rotation, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
