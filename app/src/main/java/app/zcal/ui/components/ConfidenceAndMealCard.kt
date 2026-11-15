package app.zcal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zcal.domain.model.ConfidenceLevel
import app.zcal.domain.model.Meal
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import coil.compose.AsyncImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Section 12 Page 24 Confidence Chip Spec:
 * "High, Medium or Low with a colored dot and the word, so color is never the only signal"
 */
@Composable
fun ConfidenceChip(
    confidence: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val (dotColor, label) = when (confidence) {
        ConfidenceLevel.HIGH -> colors.accent to "High confidence"
        ConfidenceLevel.MEDIUM -> colors.carbs to "Medium confidence"
        ConfidenceLevel.LOW -> colors.protein to "Low confidence"
    }

    Surface(
        modifier = modifier,
        shape = ZCalShapes.Chip,
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = ZCalTypeScale.Label,
                color = colors.textPrimary
            )
        }
    }
}

/**
 * Section 12 Page 24 Meal Card Spec:
 * "72 dp tall; 56 dp thumbnail with 16 dp radius; name in Title, time and meal type in Label;
 * calories right-aligned in bold tabular figures"
 */
@Composable
fun MealRowCard(
    meal: Meal,
    hideNumbers: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    val timeFormatted = runCatching {
        Instant.ofEpochMilli(meal.loggedAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = ZCalShapes.Card,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 56 dp thumbnail with 16 dp radius
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ZCalShapes.Thumbnail)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val photoFile = meal.photoPath?.let { File(it) }
                if (photoFile != null && photoFile.exists()) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = meal.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = null,
                        tint = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.name,
                    style = ZCalTypeScale.Title,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${meal.mealType.label} • $timeFormatted • P ${meal.proteinG.toInt()}g C ${meal.carbsG.toInt()}g F ${meal.fatG.toInt()}g",
                    style = ZCalTypeScale.Label,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }

            if (!hideNumbers) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${meal.totalCalories}",
                        style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "kcal",
                        style = ZCalTypeScale.Label,
                        color = colors.textSecondary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete ${meal.name}",
                    tint = colors.textSecondary
                )
            }
        }
    }
}
