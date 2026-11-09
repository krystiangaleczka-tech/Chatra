package app.chatra.core.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Scena, którą próbkuje szkło. Źródło nagrywa swoją zawartość do [layer], a panele szklane
 * rysują rozmyty fragment tej warstwy pod sobą. Panel nigdy nie należy do własnego źródła,
 * więc nie ma rekurencji (specyfikacja 4: kolejność renderowania szkła).
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var positionInRoot by mutableStateOf(Offset.Unspecified)
    internal var recorded by mutableStateOf(false)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Oznacza zawartość jako scenę dla szkła położonego nad nią. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this then BackdropSourceElement(backdrop)

private data class BackdropSourceElement(val backdrop: Backdrop) : ModifierNodeElement<BackdropSourceNode>() {
    override fun create() = BackdropSourceNode(backdrop)
    override fun update(node: BackdropSourceNode) {
        node.backdrop = backdrop
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "backdropSource"
    }
}

private class BackdropSourceNode(var backdrop: Backdrop) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        if (backdrop.positionInRoot != position) backdrop.positionInRoot = position
    }

    override fun ContentDrawScope.draw() {
        val layer = backdrop.layer
        layer.record { this@draw.drawContent() }
        drawLayer(layer)
        if (!backdrop.recorded) backdrop.recorded = true
    }
}
