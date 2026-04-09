package com.example.fuelconsumptionapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FuelGauge(
    value: Double?,
    min: Double,
    max: Double,
    greenMax: Double,
    yellowMax: Double,
    label: String,
    secondary: String? = null,
    /** Second needle: fuel flow (litres per hour), mapped onto the same arc with its own scale. */
    litersPerHour: Double? = null,
    litersPerHourMin: Double = 0.0,
    litersPerHourMax: Double = 40.0,
    /** Third needle: trip average L/100 km since reset (same scale as [min]…[max]). */
    tripAverageLitersPer100Km: Double? = null,
    /** Trip average line under L/h (same typography as [label]). */
    tripAverageLabel: String? = null,
) {
    val needleColorLPer100 = MaterialTheme.colorScheme.onSurface
    val needleColorLpH = MaterialTheme.colorScheme.tertiary
    val needleColorTrip = MaterialTheme.colorScheme.secondary
    val centerDotColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            val w = size.width
            val h = size.height
            val stroke = 18.dp.toPx()

            val radius = min(w, h) * 0.42f
            val center = Offset(w / 2f, h * 0.62f)

            val startAngle = 180f + 25f
            val sweep = 180f - 50f

            val rect = Rect(
                center = center,
                radius = radius
            )

            fun angleForRange(v: Double, rangeMin: Double, rangeMax: Double): Float {
                val span = rangeMax - rangeMin
                val t = if (span > 1e-12) ((v - rangeMin) / span).coerceIn(0.0, 1.0) else 0.0
                return startAngle + (sweep * t).toFloat()
            }

            fun drawBand(from: Double, to: Double, color: Color) {
                val a0 = angleForRange(from, min, max)
                val a1 = angleForRange(to, min, max)
                val s = a1 - a0
                drawArc(
                    color = color,
                    startAngle = a0,
                    sweepAngle = s,
                    useCenter = false,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            // Bands
            drawBand(min, greenMax, Color(0xFF2E7D32))
            drawBand(greenMax, yellowMax, Color(0xFFF9A825))
            drawBand(yellowMax, max, Color(0xFFC62828))

            fun drawNeedle(
                needleValue: Double?,
                rangeMin: Double,
                rangeMax: Double,
                lengthFactor: Float,
                strokePx: Float,
                color: Color,
            ) {
                val v = needleValue?.coerceIn(rangeMin, rangeMax)
                if (v == null || !v.isFinite()) return
                val angDeg = angleForRange(v, rangeMin, rangeMax)
                val ang = (angDeg / 180f) * PI.toFloat()
                val needleLen = radius * lengthFactor
                val end = Offset(
                    x = center.x + cos(ang) * needleLen,
                    y = center.y + sin(ang) * needleLen,
                )
                drawLine(
                    color = color,
                    start = center,
                    end = end,
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            }

            // Shortest → longest so all three stay readable
            drawNeedle(
                needleValue = litersPerHour,
                rangeMin = litersPerHourMin,
                rangeMax = litersPerHourMax,
                lengthFactor = 0.72f,
                strokePx = 5.dp.toPx(),
                color = needleColorLpH,
            )
            drawNeedle(
                needleValue = tripAverageLitersPer100Km,
                rangeMin = min,
                rangeMax = max,
                lengthFactor = 0.80f,
                strokePx = 5.dp.toPx(),
                color = needleColorTrip,
            )
            drawNeedle(
                needleValue = value,
                rangeMin = min,
                rangeMax = max,
                lengthFactor = 0.88f,
                strokePx = 6.dp.toPx(),
                color = needleColorLPer100,
            )

            val hasL100 = value != null && value.isFinite()
            val hasLpH = litersPerHour != null && litersPerHour.isFinite()
            val hasTrip = tripAverageLitersPer100Km != null && tripAverageLitersPer100Km.isFinite()
            if (hasL100 || hasLpH || hasTrip) {
                val hubColor =
                    when {
                        hasL100 -> needleColorLPer100
                        hasTrip -> needleColorTrip
                        else -> needleColorLpH
                    }
                drawCircle(
                    color = hubColor,
                    radius = 8.dp.toPx(),
                    center = center,
                )
            } else {
                drawCircle(
                    color = centerDotColor,
                    radius = 6.dp.toPx(),
                    center = center,
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (secondary != null) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = needleColorLpH,
            )
        }
        if (tripAverageLabel != null) {
            Text(
                text = tripAverageLabel,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = needleColorTrip,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "● L/100 km",
                style = MaterialTheme.typography.bodySmall,
                color = needleColorLPer100,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "● L/h",
                style = MaterialTheme.typography.bodySmall,
                color = needleColorLpH,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "● Trip",
                style = MaterialTheme.typography.bodySmall,
                color = needleColorTrip,
            )
        }
    }
}

