package app.zcal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object ZCalShapes {
    val Chip = RoundedCornerShape(10.dp)
    val Input = RoundedCornerShape(14.dp)
    val Thumbnail = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
    val Dialog = RoundedCornerShape(20.dp)
    val SheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Hero = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

val MaterialShapes = Shapes(
    extraSmall = ZCalShapes.Chip,
    small = ZCalShapes.Input,
    medium = ZCalShapes.Card,
    large = ZCalShapes.Hero,
    extraLarge = ZCalShapes.Pill
)
