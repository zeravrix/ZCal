package app.zcal.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.zcal.ui.localization.LocalZCalStrings
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import kotlin.math.abs

/**
 * Section 12 Page 23–25 Calorie Ring Component:
 * - 220 dp diameter, 14 dp stroke, round caps, track in `surfaceVariant`, progress in `accent`.
 * - Over goal draws a thinner second lap (8 dp) in the `carbs` amber color with friendly,
 *   non-judgmental microcopy ("120 kcal over today. That is fine, tomorrow is a fresh day.").
 * - Center shows kcal left in Numeral (48 sp tabular figures) with a Label below.
 * - Includes TalkBack summary ("1,240 of 2,130 calories eaten, 890 left").
 */
@Composable
fun CalorieRingCard(
    eatenCalories: Int,
    goalCalories: Int,
    hideNumbers: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val safeGoal = goalCalories.coerceAtLeast(1)
    val rawRatio = eatenCalories.toFloat() / safeGoal.toFloat()
    val primarySweepRatio = rawRatio.coerceIn(0f, 1f)
    val overGoalSweepRatio = (rawRatio - 1f).coerceIn(0f, 1f)

    val animatedPrimarySweep by animateFloatAsState(
        targetValue = primarySweepRatio * 360f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "primaryCalorieRingSweep"
    )
    val animatedOverSweep by animateFloatAsState(
        targetValue = overGoalSweepRatio * 360f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "overGoalCalorieRingSweep"
    )
    val remaining = goalCalories - eatenCalories
    val animatedCenterValue by animateIntAsState(
        targetValue = abs(remaining),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "calorieCenterCounter"
    )

    val talkBackSummary = if (remaining >= 0) {
        "$eatenCalories of $goalCalories calories eaten, $remaining calories left"
    } else {
        "$eatenCalories of $goalCalories calories eaten, ${abs(remaining)} calories over goal"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = talkBackSummary },
        shape = ZCalShapes.Card,
        color = colors.surfaceElevated,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val strokePx = 14.dp.toPx()
                    val overStrokePx = 8.dp.toPx()

                    // Track
                    drawArc(
                        color = colors.surfaceVariant,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // First lap (up to 100% of goal)
                    if (animatedPrimarySweep > 0.5f) {
                        drawArc(
                            color = colors.accent,
                            startAngle = -90f,
                            sweepAngle = animatedPrimarySweep,
                            useCenter = false,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }

                    // Second thinner lap when over goal (in carbs color per Section 12 Page 23)
                    if (animatedOverSweep > 0.5f) {
                        drawArc(
                            color = colors.carbs,
                            startAngle = -90f,
                            sweepAngle = animatedOverSweep,
                            useCenter = false,
                            style = Stroke(width = overStrokePx, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (hideNumbers) "•••" else "%,d".format(animatedCenterValue),
                        style = ZCalTypeScale.Numeral,
                        color = colors.textPrimary
                    )
                    Text(
                        text = if (remaining >= 0) strings.kcalLeft else strings.kcalOver,
                        style = ZCalTypeScale.Label,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!hideNumbers) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "%,d".format(eatenCalories),
                            style = ZCalTypeScale.Title,
                            color = colors.textPrimary
                        )
                        Text(
                            text = strings.eaten,
                            style = ZCalTypeScale.Label,
                            color = colors.textSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "%,d".format(goalCalories),
                            style = ZCalTypeScale.Title,
                            color = colors.textPrimary
                        )
                        Text(
                            text = strings.dailyGoal,
                            style = ZCalTypeScale.Label,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Section 12 Page 25 Non-judgmental microcopy when over goal
            if (remaining < 0 && !hideNumbers) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${abs(remaining)} kcal over today. That is fine, tomorrow is a fresh day.",
                    style = ZCalTypeScale.BodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}
