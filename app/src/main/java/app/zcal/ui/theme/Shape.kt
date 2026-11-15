package app.zcal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Section 12 Page 23 Shape & Radii:
 * - Chips: 12 dp
 * - Inputs: 16 dp
 * - Cards: 24 dp
 * - Bottom sheets & hero images: 28 dp
 * - Buttons: fully rounded (percent = 50)
 */
object ZCalShapes {
    val Chip = RoundedCornerShape(12.dp)
    val Input = RoundedCornerShape(16.dp)
    val Thumbnail = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(24.dp)
    val SheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Hero = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

val MaterialShapes = Shapes(
    extraSmall = ZCalShapes.Chip,
    small = ZCalShapes.Input,
    medium = ZCalShapes.Card,
    large = ZCalShapes.Hero,
    extraLarge = ZCalShapes.Pill
)
