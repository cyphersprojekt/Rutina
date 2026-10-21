@file:OptIn(ExperimentalLayoutApi::class)

package com.ignacio.rutina.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ignacio.rutina.data.CounterGoal
import com.ignacio.rutina.domain.MIN_TREND_DAYS
import com.ignacio.rutina.domain.SlotOutcome
import com.ignacio.rutina.domain.Trend
import com.ignacio.rutina.domain.TrendDirection
import com.ignacio.rutina.ui.approx
import com.ignacio.rutina.ui.short
import com.ignacio.rutina.ui.theme.late
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

private fun LocalTime.minutesOfDay(): Int = hour * 60 + minute

/**
 * Un punto por ocurrencia: en el eje X el día y en el Y la hora en que se marcó.
 * Las líneas punteadas son los horarios programados, así se ve a simple vista si
 * la tarea se hace siempre más tarde de lo previsto.
 */
@Composable
fun PunctualityChart(
    outcomes: List<SlotOutcome>,
    from: LocalDate,
    to: LocalDate,
    slotTimes: List<LocalTime>,
    modifier: Modifier = Modifier,
) {
    val onTimeColor = MaterialTheme.colorScheme.primary
    val lateColor = MaterialTheme.colorScheme.late
    val missedColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    val slotMinutes = (slotTimes + outcomes.map { it.time }).map { it.minutesOfDay() }.distinct()
    if (slotMinutes.isEmpty()) return
    // Algo marcado muchas horas después no debería aplastar el resto del gráfico.
    val lowLimit = slotMinutes.min() - 6 * 60
    val highLimit = slotMinutes.max() + 6 * 60
    fun plotted(outcome: SlotOutcome): Int =
        (outcome.time.minutesOfDay() + (outcome.delayMinutes ?: 0L).toInt()).coerceIn(lowLimit, highLimit)

    val values = slotMinutes + outcomes.map(::plotted)
    val minY = Math.floorDiv(values.min() - 30, 60) * 60
    val maxY = -Math.floorDiv(-(values.max() + 30), 60) * 60
    val dayCount = ChronoUnit.DAYS.between(from, to).toInt() + 1

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val left = 44.dp.toPx()
        val right = 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 20.dp.toPx()
        val width = size.width - left - right
        val height = size.height - top - bottom
        fun x(date: LocalDate) = left + width * (ChronoUnit.DAYS.between(from, date) + 0.5f) / dayCount
        fun y(minutes: Int) = top + height * (1f - (minutes - minY).toFloat() / (maxY - minY))

        val spanHours = (maxY - minY) / 60
        val stepHours = when {
            spanHours <= 4 -> 1
            spanHours <= 8 -> 2
            spanHours <= 16 -> 4
            else -> 6
        }
        for (minutes in minY..maxY step stepHours * 60) {
            val lineY = y(minutes)
            drawLine(gridColor, Offset(left, lineY), Offset(size.width - right, lineY), strokeWidth = 1.dp.toPx())
            val label = measurer.measure("%02d:00".format(Math.floorMod(minutes / 60, 24)), labelStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), lineY - label.size.height / 2f))
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        slotMinutes.forEach { minutes ->
            val lineY = y(minutes)
            drawLine(
                color = onTimeColor.copy(alpha = 0.6f),
                start = Offset(left, lineY),
                end = Offset(size.width - right, lineY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
        }

        val radius = (width / dayCount / 2.5f).coerceIn(2.5.dp.toPx(), 5.dp.toPx())
        outcomes.forEach { outcome ->
            val center = Offset(x(outcome.date), y(plotted(outcome)))
            when {
                outcome.doneAt == null -> drawCircle(missedColor, radius, center, style = Stroke(1.5.dp.toPx()))
                outcome.isOnTime -> drawCircle(onTimeColor, radius, center)
                else -> drawCircle(lateColor, radius, center)
            }
        }

        val fromLabel = measurer.measure(from.short(), labelStyle)
        drawText(fromLabel, topLeft = Offset(left, size.height - fromLabel.size.height))
        val toLabel = measurer.measure(to.short(), labelStyle)
        drawText(toLabel, topLeft = Offset(size.width - right - toLabel.size.width, size.height - toLabel.size.height))
    }
}

@Composable
fun PunctualityLegend(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem("A tiempo") { drawCircle(primary) }
        val late = MaterialTheme.colorScheme.late
        LegendItem("Más de 15 min tarde") { drawCircle(late) }
        val missed = MaterialTheme.colorScheme.error
        LegendItem("No hecha") { drawCircle(missed, style = Stroke(1.5.dp.toPx())) }
        LegendItem("Horario programado") {
            drawLine(
                color = primary.copy(alpha = 0.6f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
            )
        }
    }
}

@Composable
private fun LegendItem(text: String, symbol: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp), onDraw = symbol)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Color de la tendencia: el principal si va hacia el objetivo y de advertencia si va en contra. */
@Composable
private fun trendColor(trend: Trend?, goal: CounterGoal): Color = when (trend?.isGood(goal)) {
    true -> MaterialTheme.colorScheme.primary
    false -> MaterialTheme.colorScheme.late
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Barras con el total de cada día y, si hay, la recta de tendencia encima. */
@Composable
fun DailyBarChart(daily: List<Pair<LocalDate, Int>>, trend: Trend?, goal: CounterGoal, modifier: Modifier = Modifier) {
    if (daily.isEmpty()) return
    // Con la recta encima, las barras se aclaran para que se distinga.
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = if (trend == null) 1f else 0.45f)
    val lineColor = trendColor(trend, goal)
    val haloColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    // La recta puede pasarse un poco del día de más: la escala la incluye para que no se corte arriba.
    val trendMax = trend?.let { ceil(maxOf(it.start, it.end)).toInt() } ?: 0
    val max = maxOf(daily.maxOf { it.second }, trendMax).coerceAtLeast(1)

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val left = 36.dp.toPx()
        val right = 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 20.dp.toPx()
        val width = size.width - left - right
        val height = size.height - top - bottom
        val baseline = top + height

        listOf(max to top, 0 to baseline).forEach { (value, lineY) ->
            drawLine(gridColor, Offset(left, lineY), Offset(size.width - right, lineY), strokeWidth = 1.dp.toPx())
            val label = measurer.measure("$value", labelStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), lineY - label.size.height / 2f))
        }

        val slot = width / daily.size
        val barWidth = (slot * 0.7f).coerceAtLeast(1.dp.toPx())
        daily.forEachIndexed { index, (_, value) ->
            if (value == 0) return@forEachIndexed
            val barHeight = height * value / max
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left + slot * index + (slot - barWidth) / 2, baseline - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }

        trend?.let { line ->
            fun x(date: LocalDate) = left + slot * (ChronoUnit.DAYS.between(daily.first().first, date) + 0.5f)
            fun y(value: Float) = baseline - height * value / max
            val start = Offset(x(line.firstDay), y(line.start))
            val end = Offset(x(line.lastDay), y(line.end))
            // Si la recta baja de cero, se corta en el eje.
            clipRect(left, top, size.width - right, baseline) {
                // El borde del color del fondo la separa de las barras que cruza.
                drawLine(haloColor, start, end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(lineColor, start, end, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        val fromLabel = measurer.measure(daily.first().first.short(), labelStyle)
        drawText(fromLabel, topLeft = Offset(left, size.height - fromLabel.size.height))
        val toLabel = measurer.measure(daily.last().first.short(), labelStyle)
        drawText(toLabel, topLeft = Offset(size.width - right - toLabel.size.width, size.height - toLabel.size.height))
    }
}

/** Explica la recta del gráfico: si sube o baja, de cuánto a cuánto y si eso es bueno según el objetivo. */
@Composable
fun TrendSummary(trend: Trend?, goal: CounterGoal, unit: String, modifier: Modifier = Modifier) {
    val color = trendColor(trend, goal)
    val (title, detail) = when {
        trend == null -> "Tendencia" to "Aparece cuando haya al menos $MIN_TREND_DAYS días completos en el período."
        trend.direction == TrendDirection.FLAT ->
            "Sin una tendencia clara" to "Hay días de más y días de menos, pero no subís ni bajás de forma sostenida."
        else -> {
            val direction = if (trend.direction == TrendDirection.UP) "Cada vez más" else "Cada vez menos"
            val verdict = if (trend.isGood(goal) == true) "vas bien" else "al revés de tu objetivo"
            val amounts = listOf("De ${approx(trend.start)} a ${approx(trend.end)}", unit, "por día")
                .filter { it.isNotBlank() }
                .joinToString(" ")
            "$direction: $verdict" to "$amounts entre el ${trend.firstDay.short()} y el ${trend.lastDay.short()}."
        }
    }
    Row(modifier, verticalAlignment = Alignment.Top) {
        Canvas(Modifier.padding(top = 6.dp).size(width = 16.dp, height = 10.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height * 0.8f),
                end = Offset(size.width, size.height * 0.2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
