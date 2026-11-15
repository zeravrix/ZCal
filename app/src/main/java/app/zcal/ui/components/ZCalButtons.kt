package app.zcal.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale

/**
 * Enterprise-grade, alive interactive CTA button with:
 * - Full-width touch target (56dp height)
 * - Tactile spring scale bounce on press (1.0 -> 0.975 -> 1.0)
 * - Vibrant gradient accent fill with colored ambient glow
 * - Smooth loading state indicator
 */
@Composable
fun ZCalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val colors = LocalZCalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "btnScale"
    )

    val gradientBrush = if (enabled && !isLoading) {
        Brush.horizontalGradient(
            colors = listOf(
                colors.accent,
                colors.accent.copy(alpha = 0.88f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                colors.surfaceVariant,
                colors.surfaceVariant
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (enabled && !isLoading) 6.dp else 0.dp,
                shape = ZCalShapes.Pill,
                spotColor = colors.accentGlow,
                ambientColor = colors.accentGlow
            )
            .clip(ZCalShapes.Pill)
            .background(gradientBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colors.onAccent,
                    strokeWidth = 2.5.dp
                )
            } else {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) colors.onAccent else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) colors.onAccent else colors.textSecondary
                )
            }
        }
    }
}

/**
 * Secondary Glass / Outline Button with tactile press bounce and subtle surface fill
 */
@Composable
fun ZCalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val colors = LocalZCalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "secBtnScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(ZCalShapes.Pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        shape = ZCalShapes.Pill,
        color = if (isPressed) colors.surfaceVariant else colors.surfaceElevated,
        border = BorderStroke(1.2.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = ZCalTypeScale.Title.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
        }
    }
}
