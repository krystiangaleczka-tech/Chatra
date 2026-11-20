package app.chatra.core.designsystem.glass

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Parametry `backdrop-filter` i powierzchni z HTML: rozmycie (odchylenie standardowe CSS),
 * saturacja, gradient liniowy pod kątem CSS, obramowanie i wewnętrzny refleks u góry.
 */
@Immutable
data class GlassStyle(
    val blur: Dp,
    val saturation: Float,
    val angleDegrees: Float,
    val from: Color,
    val to: Color,
    val border: Color,
    val borderWidth: Dp = 1.dp,
    val topHighlight: Color = Color.Transparent,
)

/**
 * Szkło nad [backdrop]: scena → fragment pod panelem → rozmycie i saturacja → maska kształtu →
 * gradient powierzchni → refleks → obramowanie → ostra treść. Treść nie jest rozmywana.
 * Przy [matte] (ograniczenie przezroczystości) rysowana jest tylko nieprzezroczysta powierzchnia.
 */
fun Modifier.glass(
    backdrop: Backdrop?,
    shape: Shape,
    style: GlassStyle,
    matte: Boolean = false,
): Modifier = this then GlassElement(backdrop, shape, style, matte)

private data class GlassElement(
    val backdrop: Backdrop?,
    val shape: Shape,
    val style: GlassStyle,
    val matte: Boolean,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(backdrop, shape, style, matte)
    override fun update(node: GlassNode) {
        node.backdrop = backdrop
        node.shape = shape
        node.style = style
        node.matte = matte
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
    }
}

private class GlassNode(
    var backdrop: Backdrop?,
    var shape: Shape,
    var style: GlassStyle,
    var matte: Boolean,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var blurLayer: GraphicsLayer? = null

    /**
     * Scena przesunięta pod panel. Przesunięcie to właściwość warstwy (RenderNode), więc ruch panelu —
     * np. przewijanie przy górze skrzynki — nie wymaga ponownego nagrania ani rysowania węzła. HWUI i tak
     * przelicza rozmycie, bo zmiana właściwości uszkadza warstwę z efektem. Przerysowanie węzła przy każdym
     * przesunięciu kosztowało na A15 kilka ms wątku UI na klatkę.
     */
    private var shiftLayer: GraphicsLayer? = null
    private var position = Offset.Unspecified
    private var effectFor: Triple<Float, Float, Float>? = null

    /** Ostatnio nagrana warstwa rozmycia (scena, rozmiar, skala, zapas); położenie nie należy do klucza. */
    private var recordedFor: RecordKey? = null

    /** Zapas warstwy rozmycia z ostatniego rysowania (px) — potrzebny do przesunięcia sceny bez rysowania. */
    private var margin = Float.NaN

    override fun onAttach() {
        val graphics = currentValueOf(LocalGraphicsContext)
        blurLayer = graphics.createGraphicsLayer()
        shiftLayer = graphics.createGraphicsLayer()
    }

    override fun onDetach() {
        val graphics = currentValueOf(LocalGraphicsContext)
        blurLayer?.let(graphics::releaseGraphicsLayer)
        shiftLayer?.let(graphics::releaseGraphicsLayer)
        blurLayer = null
        shiftLayer = null
        effectFor = null
        recordedFor = null
        margin = Float.NaN
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        if (p == position) return
        val first = !position.isSpecified
        position = p
        if (first || (!matte && !shiftScene())) invalidateDraw()
    }

    /** Ustawia przesunięcie sceny pod panelem; false, gdy warstwa nie jest jeszcze nagrana. */
    private fun shiftScene(): Boolean {
        val source = backdrop ?: return false
        val shift = shiftLayer ?: return false
        if (recordedFor == null || margin.isNaN() || !source.positionInRoot.isSpecified || !position.isSpecified) return false
        val offset = position - source.positionInRoot
        shift.translationX = margin - offset.x
        shift.translationY = margin - offset.y
        return true
    }

    override fun ContentDrawScope.draw() {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = outline.toPath()
        val source = backdrop
        val layer = blurLayer
        val shift = shiftLayer
        if (!matte && source != null && layer != null && shift != null && source.recorded &&
            source.positionInRoot.isSpecified && position.isSpecified
        ) {
            drawBackdrop(source, layer, shift, path)
        }
        val surface = if (matte) style.copy(from = style.from.copy(alpha = 1f), to = style.to.copy(alpha = 1f)) else style
        drawOutline(outline, cssLinearGradient(surface.angleDegrees, size, surface.from, surface.to))
        drawTopHighlight(path)
        val stroke = style.borderWidth.toPx()
        clipPath(path) { drawOutline(outline, style.border, style = Stroke(width = stroke * 2)) }
        drawContent()
    }

    private fun DrawScope.drawBackdrop(source: Backdrop, layer: GraphicsLayer, shift: GraphicsLayer, clip: Path) {
        val sigma = style.blur.toPx()
        val margin = ceil(sigma * 3f)
        // Scena próbkowana w obniżonej rozdzielczości: przy σ 28–40 dp (≈ 100–140 px) rozmycie i tak usuwa
        // szczegóły, a warstwa zajmuje 1/16 pamięci i pracy GPU (CH003). Po rozmyciu skalowana z powrotem.
        val k = downsample(sigma)
        val key = Triple(sigma, style.saturation, k)
        if (effectFor != key) {
            val r = sigmaToRadius(sigma * k)
            layer.renderEffect = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix().apply { setToSaturation(style.saturation) }.values),
                RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP),
            ).asComposeRenderEffect()
            effectFor = key
        }
        val layerSize = IntSize(ceil((size.width + 2 * margin) * k).toInt(), ceil((size.height + 2 * margin) * k).toInt())
        // Nagrywać tylko po zmianie rozmiaru panelu lub sceny. Zmiany treści sceny przechodzą same przez
        // odwołanie do jej RenderNode, a niezmieniona warstwa pozwala HWUI nie liczyć rozmycia od nowa,
        // gdy przerysowuje się tylko treść panelu (np. przewijanie wariantów w arkuszu S04, CH003).
        // Położenie panelu ustawia shiftScene() jako przesunięcie warstwy sceny.
        val recordKey = RecordKey(source, source.layer.size, layerSize, k, margin)
        if (recordedFor != recordKey) {
            shift.record(source.layer.size) { drawLayer(source.layer) }
            layer.record(layerSize) {
                scale(k, k, pivot = Offset.Zero) { drawLayer(shift) }
            }
            recordedFor = recordKey
        }
        this@GlassNode.margin = margin
        shiftScene()
        clipPath(clip) {
            translate(-margin, -margin) { scale(1f / k, 1f / k, pivot = Offset.Zero) { drawLayer(layer) } }
        }
    }

    private fun DrawScope.drawTopHighlight(clip: Path) {
        if (style.topHighlight.alpha == 0f) return
        // CSS `inset 0 1px 0 color`: jasna linia 1 px wzdłuż górnej krawędzi wewnątrz kształtu.
        val line = 1.dp.toPx()
        clipPath(clip) {
            val inner = Path().apply {
                addOutline(shape.createOutline(Size(size.width, size.height), layoutDirection, this@drawTopHighlight))
                translate(Offset(0f, line))
            }
            clipPath(inner, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                drawRect(style.topHighlight)
            }
        }
    }
}

private data class RecordKey(val source: Backdrop, val sourceSize: IntSize, val size: IntSize, val scale: Float, val margin: Float)

/**
 * Współczynnik próbkowania warstwy rozmycia: σ po zmniejszeniu ≥ 16 px (bez widocznych schodków przy
 * powiększeniu), najwyżej 4 × w każdej osi. Małe rozmycia zostają w pełnej rozdzielczości.
 */
internal fun downsample(sigmaPx: Float): Float = if (sigmaPx <= 16f) 1f else (16f / sigmaPx).coerceIn(0.25f, 1f)

/** HWUI zamienia „radius” RenderEffect na sigma = 0.57735 · r + 0.5; CSS blur() podaje sigma. */
internal fun sigmaToRadius(sigma: Float): Float = if (sigma <= 0.5f) 0f else (sigma - 0.5f) / 0.57735f

/** `linear-gradient(<angle>deg, from, to)`: 0° w górę, zgodnie z ruchem wskazówek, długość wg CSS. */
fun cssLinearGradient(angleDegrees: Float, size: Size, from: Color, to: Color): Brush {
    val a = angleDegrees * PI.toFloat() / 180f
    val dx = sin(a)
    val dy = -cos(a)
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(
        colors = listOf(from, to),
        start = center - Offset(dx * half, dy * half),
        end = center + Offset(dx * half, dy * half),
    )
}

private fun Outline.toPath(): Path = Path().apply { addOutline(this@toPath) }
