package app.chatra.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/** CSS `box-shadow: inset 0 1px 0 color` dla nieszklanych powierzchni (przycisk akcentu, dymek). */
fun Modifier.topHighlight(shape: Shape, color: Color): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { addOutline(outline) }
    val inner = Path().apply { addOutline(outline); translate(Offset(0f, 1.dp.toPx())) }
    clipPath(clip) { clipPath(inner, ClipOp.Difference) { drawRect(color) } }
}
