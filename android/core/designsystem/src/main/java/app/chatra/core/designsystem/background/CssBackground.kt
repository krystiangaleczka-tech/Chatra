package app.chatra.core.designsystem.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawBehind

/**
 * Jedna warstwa `radial-gradient(<rx>% <ry>% at <cx>% <cy>%, color 0%, transparent <stop>%)`.
 * Promienie i środek są ułamkami rozmiaru panelu, nie ekranu (specyfikacja 4, tła gradientowe).
 */
@Immutable
data class RadialLayer(
    val radiusX: Float,
    val radiusY: Float,
    val centerX: Float,
    val centerY: Float,
    val color: Color,
    val stop: Float = 0.7f,
)

/** Tło CSS: kolor bazowy i warstwy radialne; pierwsza warstwa z listy leży na wierzchu, jak w CSS. */
@Immutable
data class CssBackground(val base: Color, val layers: List<RadialLayer>)

fun DrawScope.drawCssBackground(background: CssBackground, area: Size = size) {
    drawRect(background.base, size = area)
    for (layer in background.layers.asReversed()) drawRadialLayer(layer, area)
}

private fun DrawScope.drawRadialLayer(layer: RadialLayer, area: Size) {
    val rx = layer.radiusX * area.width
    val ry = layer.radiusY * area.height
    if (rx <= 0f || ry <= 0f) return
    val center = Offset(layer.centerX * area.width, layer.centerY * area.height)
    val scaleY = ry / rx
    val brush = Brush.radialGradient(
        0f to layer.color,
        layer.stop to layer.color.copy(alpha = 0f),
        center = center,
        radius = rx,
    )
    // Elipsa CSS = okrąg o promieniu rx rozciągnięty w pionie do ry wokół środka.
    withTransform({ scale(1f, scaleY, pivot = center) }) {
        val top = center.y - center.y / scaleY
        drawRect(brush, topLeft = Offset(0f, top), size = Size(area.width, area.height / scaleY))
    }
}

@Composable
fun CssBackgroundBox(background: CssBackground, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.drawBehind { drawCssBackground(background) }, content = content)
}
