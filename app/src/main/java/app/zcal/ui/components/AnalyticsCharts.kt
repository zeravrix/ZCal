package app.zcal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zcal.domain.model.DaySummary
import app.zcal.domain.model.WeightPointWithTrend
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalPalette
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Section 11 Page 18–20 7-Column Calendar Heat Map:
 * Supports both Default Palette (Green/Amber/Coral tints) and Calm Palette (Green-only tints for
 * users who find red days stressful), plus shape indicators (check/dot) for accessibility.
 */
@Composable
fun MonthCalendarHeatMap(
    yearMonth: YearMonth,
    summariesByDate: Map<String, DaySummary>,
    calorieGoal: Int,
    calmPalette: Boolean,
    onDayClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    // Monday = 0 .. Sunday = 6
    val leadingEmptyCells = (firstDayOfMonth.dayOfWeek.value - 1)
    val totalCells = leadingEmptyCells + daysInMonth
    val rows = (totalCells + 6) / 7
    val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    Column(modifier = modifier.fillMaxWidth()) {
        // Weekday header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLabel ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayLabel,
                        style = ZCalTypeScale.Label,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        for (r in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in 0 until 7) {
                    val cellIndex = r * 7 + c
                    val dayNum = cellIndex - leadingEmptyCells + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = yearMonth.atDay(dayNum)
                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val summary = summariesByDate[dateStr]
                        val isToday = dateStr == todayStr

                        CalendarDaySquare(
                            dayNumber = dayNum,
                            summary = summary,
                            calorieGoal = calorieGoal,
                            calmPalette = calmPalette,
                            isToday = isToday,
                            onClick = { onDayClicked(dateStr) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDaySquare(
    dayNumber: Int,
    summary: DaySummary?,
    calorieGoal: Int,
    calmPalette: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val safeGoal = calorieGoal.coerceAtLeast(1)
    val diffRatio = if (summary != null && summary.mealCount > 0) {
        abs(summary.kcal - safeGoal).toFloat() / safeGoal.toFloat()
    } else 1f

    // Section 11 Page 20 Status Rules & Palette Tints
    val bgTint = when {
        summary == null || summary.mealCount == 0 -> colors.surfaceVariant.copy(alpha = 0.55f)
        !summary.complete -> if (calmPalette) {
            if (colors.isDark) ZCalPalette.CalCalmMedDark else ZCalPalette.CalCalmMedLight
        } else {
            colors.surface
        }
        calmPalette -> if (colors.isDark) ZCalPalette.CalCalmHighDark else ZCalPalette.CalCalmHighLight
        diffRatio <= 0.10f -> if (colors.isDark) ZCalPalette.CalOnTargetTintDark else ZCalPalette.CalOnTargetTintLight
        diffRatio <= 0.25f -> if (colors.isDark) ZCalPalette.CalCloseTintDark else ZCalPalette.CalCloseTintLight
        else -> if (colors.isDark) ZCalPalette.CalOffTintDark else ZCalPalette.CalOffTintLight
    }

    // Shape marker for accessibility (Section 12 Page 25: "calendar carries shapes")
    val shapeMarker = when {
        summary == null || summary.mealCount == 0 -> ""
        !summary.complete -> "○"
        calmPalette || diffRatio <= 0.10f -> "•"
        diffRatio <= 0.25f -> "▪"
        else -> "▫"
    }

    val borderStroke = when {
        isToday -> BorderStroke(2.dp, colors.accent)
        summary != null && summary.mealCount > 0 && !summary.complete -> BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, colors.outline.copy(alpha = 0.5f))
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(bgTint)
            .border(borderStroke, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNumber.toString(),
                style = ZCalTypeScale.Label.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                ),
                color = colors.textPrimary
            )
            if (shapeMarker.isNotEmpty()) {
                Text(
                    text = shapeMarker,
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/**
 * Section 11 Block 4 & Section 12 Page 24 Bar Chart Spec:
 * "Bars with 6 dp rounded tops, dashed 1.5 dp goal line, dotted gridlines at half-strength outline,
 * plus 7-day moving average line."
 */
@Composable
fun DailyCaloriesBarChart(
    summaries: List<DaySummary>,
    calorieGoal: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val maxVal = (summaries.maxOfOrNull { it.kcal } ?: calorieGoal)
        .coerceAtLeast(calorieGoal)
        .coerceAtLeast(1000) * 1.15f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Daily calories bar chart with goal line at $calorieGoal calories"
            },
        shape = ZCalShapes.Card,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Daily Calories & 7D Trend",
                    style = ZCalTypeScale.Title,
                    color = colors.textPrimary
                )
                Text(
                    text = "Goal: $calorieGoal kcal",
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                val width = size.width
                val height = size.height

                // Dotted horizontal gridlines at 25%, 50%, 75%
                val gridEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    val y = height * frac
                    drawLine(
                        color = colors.outline.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = gridEffect
                    )
                }

                // Bars with 6 dp rounded tops
                val count = summaries.size.coerceAtLeast(7)
                val slotWidth = width / count.toFloat()
                val barWidth = (slotWidth * 0.62f).coerceIn(6f, 28.dp.toPx())
                val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                summaries.forEachIndexed { idx, day ->
                    val barHeight = ((day.kcal / maxVal) * height).coerceIn(4f, height)
                    val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val y = height - barHeight
                    drawRoundRect(
                        color = colors.accent,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }

                // Dashed 1.5 dp goal line
                val goalY = (height - (calorieGoal / maxVal) * height).coerceIn(0f, height)
                drawLine(
                    color = colors.carbs,
                    start = Offset(0f, goalY),
                    end = Offset(width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            }
        }
    }
}

/**
 * Section 11 Block 5 Macro Split Donut Chart (Protein, Carbs, Fat share of calories).
 */
@Composable
fun MacroSplitDonutCard(
    avgProteinG: Double,
    avgCarbsG: Double,
    avgFatG: Double,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val pKcal = (avgProteinG * 4.0).toFloat()
    val cKcal = (avgCarbsG * 4.0).toFloat()
    val fKcal = (avgFatG * 9.0).toFloat()
    val total = (pKcal + cKcal + fKcal).coerceAtLeast(1f)

    val pPct = ((pKcal / total) * 100).toInt()
    val cPct = ((cKcal / total) * 100).toInt()
    val fPct = (100 - pPct - cPct).coerceAtLeast(0)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ZCalShapes.Card,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(100.dp)) {
                    val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Butt)
                    val pSweep = (pKcal / total) * 360f
                    val cSweep = (cKcal / total) * 360f
                    val fSweep = 360f - pSweep - cSweep

                    drawArc(colors.protein, -90f, pSweep, false, style = stroke)
                    drawArc(colors.carbs, -90f + pSweep, cSweep, false, style = stroke)
                    drawArc(colors.fat, -90f + pSweep + cSweep, fSweep, false, style = stroke)
                }
                Text(
                    text = "Macros",
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text(
                    text = "P • Protein: ${avgProteinG.toInt()}g ($pPct%)",
                    style = ZCalTypeScale.BodySmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "C • Carbs: ${avgCarbsG.toInt()}g ($cPct%)",
                    style = ZCalTypeScale.BodySmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "F • Fat: ${avgFatG.toInt()}g ($fPct%)",
                    style = ZCalTypeScale.BodySmall,
                    color = colors.textPrimary
                )
            }
        }
    }
}

/**
 * Section 8 & 11 Weight Trend Line Chart with 7-day EWMA line over raw points & goal reference line.
 */
@Composable
fun WeightTrendChartCard(
    points: List<WeightPointWithTrend>,
    targetWeightKg: Double,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ZCalShapes.Card,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Weight Trend (7D EWMA)",
                    style = ZCalTypeScale.Title,
                    color = colors.textPrimary
                )
                Text(
                    text = "Target: ${String.format("%.1f", targetWeightKg)} kg",
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (points.isEmpty()) {
                Text(
                    text = "Log your weight below to plot your 7-day moving average trend.",
                    style = ZCalTypeScale.BodySmall,
                    color = colors.textSecondary
                )
            } else {
                val allVals = points.flatMap { listOf(it.rawWeightKg, it.ewmaWeightKg) } + targetWeightKg
                val minW = (allVals.minOrNull() ?: 60.0) - 1.5
                val maxW = (allVals.maxOrNull() ?: 80.0) + 1.5
                val span = (maxW - minW).coerceAtLeast(1.0)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Dashed target weight line
                    val targetY = (h - ((targetWeightKg - minW) / span) * h).toFloat().coerceIn(0f, h)
                    drawLine(
                        color = colors.carbs,
                        start = Offset(0f, targetY),
                        end = Offset(w, targetY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    val stepX = if (points.size > 1) w / (points.size - 1).toFloat() else w / 2f
                    var prevEwmaOffset: Offset? = null

                    points.forEachIndexed { idx, pt ->
                        val x = if (points.size > 1) idx * stepX else w / 2f
                        val rawY = (h - ((pt.rawWeightKg - minW) / span) * h).toFloat().coerceIn(4f, h - 4f)
                        val ewmaY = (h - ((pt.ewmaWeightKg - minW) / span) * h).toFloat().coerceIn(4f, h - 4f)

                        // Raw weight dot
                        drawCircle(
                            color = colors.textSecondary.copy(alpha = 0.45f),
                            radius = 3.5.dp.toPx(),
                            center = Offset(x, rawY)
                        )

                        // 7-day EWMA trend line
                        val currentEwmaOffset = Offset(x, ewmaY)
                        prevEwmaOffset?.let { prev ->
                            drawLine(
                                color = colors.accent,
                                start = prev,
                                end = currentEwmaOffset,
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        prevEwmaOffset = currentEwmaOffset
                    }
                }
            }
        }
    }
}
