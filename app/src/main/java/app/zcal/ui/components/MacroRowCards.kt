package app.zcal.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import kotlin.math.roundToInt

/**
 * Section 12 Page 23 & 25 Macro Card Spec:
 * - Three in a row, 104 dp tall.
 * - Letter chip P, C or F in the macro color (so color is never the only accessibility signal).
 * - Eaten and goal grams in tabular figures (always in textPrimary, never in a macro color).
 * - 6 dp pill progress bar.
 */
@Composable
fun MacroRowCards(
    proteinEatenG: Double,
    proteinGoalG: Int,
    carbsEatenG: Double,
    carbsGoalG: Int,
    fatEatenG: Double,
    fatGoalG: Int,
    hideNumbers: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleMacroCard(
            letter = "P",
            label = "Protein",
            eatenG = proteinEatenG.roundToInt(),
            goalG = proteinGoalG,
            macroColor = colors.protein,
            hideNumbers = hideNumbers,
            modifier = Modifier.weight(1f)
        )
        SingleMacroCard(
            letter = "C",
            label = "Carbs",
            eatenG = carbsEatenG.roundToInt(),
            goalG = carbsGoalG,
            macroColor = colors.carbs,
            hideNumbers = hideNumbers,
            modifier = Modifier.weight(1f)
        )
        SingleMacroCard(
            letter = "F",
            label = "Fat",
            eatenG = fatEatenG.roundToInt(),
            goalG = fatGoalG,
            macroColor = colors.fat,
            hideNumbers = hideNumbers,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SingleMacroCard(
    letter: String,
    label: String,
    eatenG: Int,
    goalG: Int,
    macroColor: Color,
    hideNumbers: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val progress = if (goalG > 0) (eatenG.toFloat() / goalG.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "macroBarProgress"
    )

    Surface(
        modifier = modifier
            .height(104.dp)
            .semantics {
                contentDescription = "$label: $eatenG of $goalG grams"
            },
        shape = ZCalShapes.Card,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(ZCalShapes.Chip)
                        .background(macroColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        style = ZCalTypeScale.Label.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                }
                Text(
                    text = label,
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary
                )
            }

            Text(
                text = if (hideNumbers) "•• / ••g" else "${eatenG}g / ${goalG}g",
                style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )

            // 6 dp pill progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(ZCalShapes.Pill)
                    .background(colors.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(ZCalShapes.Pill)
                        .background(macroColor)
                )
            }
        }
    }
}
