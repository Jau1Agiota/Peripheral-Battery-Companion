package dev.peripheral.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal enum class Glyph { Battery, BoltBattery, Devices, History, Settings, Monitor, Refresh, Chevron }

/** Resolution-independent decorative line icons. Labels belong to the enclosing controls. */
@Composable
internal fun LineIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x, y), Offset(x2, y2), strokeWidth = 1.6f, cap = StrokeCap.Round)
            fun path(vararg points: Pair<Float, Float>) {
                val p = Path(); points.forEachIndexed { i, v -> if (i == 0) p.moveTo(v.first, v.second) else p.lineTo(v.first, v.second) }
                drawPath(p, color, style = Stroke(1.6f, cap = StrokeCap.Round))
            }
            when (glyph) {
                Glyph.Battery, Glyph.BoltBattery -> {
                    drawRoundRect(color, Offset(5f, 3f), Size(14f, 20f), CornerRadius(1f), style = Stroke(1.5f))
                    line(9f, 0.8f, 15f, 0.8f)
                    if (glyph == Glyph.BoltBattery) path(14f to 6f, 9f to 13f, 14f to 13f, 10f to 20f)
                    else drawRect(color, Offset(8f, 7f), Size(8f, 13f))
                }
                Glyph.Devices -> path(2f to 11f, 12f to 3f, 22f to 11f, 20f to 11f, 20f to 22f, 4f to 22f, 4f to 11f)
                Glyph.History -> { line(3f, 22f, 22f, 22f); line(5f, 19f, 5f, 10f); line(12f, 19f, 12f, 3f); line(19f, 19f, 19f, 13f) }
                Glyph.Settings -> {
                    drawCircle(color, 7f, Offset(12f, 12f), style = Stroke(3f))
                    drawCircle(color, 2.4f, Offset(12f, 12f), style = Stroke(1.2f))
                    repeat(8) { i ->
                        val angle = i * Math.PI / 4
                        line(12f + cos(angle).toFloat() * 8, 12f + sin(angle).toFloat() * 8,
                            12f + cos(angle).toFloat() * 10, 12f + sin(angle).toFloat() * 10)
                    }
                }
                Glyph.Monitor -> { drawRoundRect(color, Offset(1f, 3f), Size(22f, 15f), CornerRadius(1f), style = Stroke(1.6f)); line(12f, 18f, 12f, 22f); line(7f, 22f, 17f, 22f) }
                Glyph.Refresh -> { drawArc(color, 35f, 275f, false, Offset(3f, 3f), Size(18f, 18f), style = Stroke(1.6f)); path(15f to 2f, 20f to 4f, 19f to 9f); path(9f to 22f, 4f to 20f, 5f to 15f) }
                Glyph.Chevron -> path(8f to 4f, 16f to 12f, 8f to 20f)
            }
        }
    }
}
