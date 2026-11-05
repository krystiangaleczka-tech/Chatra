package app.chatra.core.designsystem.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Element ikony w siatce 24 × 24, przepisany ze źródłowego SVG bez zmian geometrii. */
sealed interface IconShape {
    data class PathData(val d: String) : IconShape
    data class Circle(val cx: Float, val cy: Float, val r: Float) : IconShape
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float, val rx: Float) : IconShape
}

/** Ikona ze źródła: `fill` (np. „więcej”) albo `stroke` z zaokrąglonymi końcami i łączeniami. */
@Immutable
class ChatraIcon(val shapes: List<IconShape>, val filled: Boolean = false) {
    internal val paths: List<Path?> by lazy {
        shapes.map { if (it is IconShape.PathData) PathParser().parsePathString(it.d).toPath() else null }
    }
}

private fun icon(vararg shapes: IconShape, filled: Boolean = false) = ChatraIcon(shapes.toList(), filled)
private fun p(d: String) = IconShape.PathData(d)
private fun c(cx: Float, cy: Float, r: Float) = IconShape.Circle(cx, cy, r)
private fun r(x: Float, y: Float, w: Float, h: Float, rx: Float) = IconShape.Rect(x, y, w, h, rx)

/** Komplet 31 ikon z dziesięciu plansz (Chatra_Pakiet_referencyjny/ikony i szablony). */
object ChatraIcons {
    val Chat = icon(p("M4 12c0-4.4 3.6-7.5 8-7.5s8 3.1 8 7.5-3.6 7.5-8 7.5c-1.2 0-2.4-.2-3.4-.6L4 20l1.2-3.6C4.4 15.1 4 13.6 4 12z"))
    val Check = icon(p("M5 12.5l4.5 4.5L19 7.5"))
    val Minus = icon(p("M6 12h12"))
    val More = icon(c(5.5f, 12f, 1.7f), c(12f, 12f, 1.7f), c(18.5f, 12f, 1.7f), filled = true)
    val Search = icon(c(11f, 11f, 6.5f), p("M16 16l4.5 4.5"))
    val Pin = icon(p("M9 4h6l-1 6 3 3H7l3-3z"), p("M12 13v7"))
    val Parcel = icon(p("M4 7.5l8-4 8 4v9l-8 4-8-4z"), p("M4 7.5l8 4 8-4M12 11.5v9"))
    val Person = icon(c(12f, 8.5f, 3.5f), p("M5 20c0-3.6 3.1-6 7-6s7 2.4 7 6"))
    val Alert = icon(c(12f, 12f, 8.5f), p("M12 7.5v5.5M12 16.2v.3"))
    val Group = icon(c(9f, 8.5f, 3f), c(16.5f, 9.5f, 2.5f), p("M3.5 19c0-3 2.5-5 5.5-5s5.5 2 5.5 5M15 14.2c2.9-.3 5.5 1.4 5.5 4.3"))
    val Clock = icon(c(12f, 12f, 8.5f), p("M12 7.5V12l3 2"))
    val Sliders = icon(p("M4 7h10M18 7h2M4 17h4M12 17h8"), c(16f, 7f, 2f), c(10f, 17f, 2f))
    val Compose = icon(p("M12 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6"), p("M17.5 3.5l3 3L12 15l-4 1 1-4z"))
    val ChevronLeft = icon(p("M15 5l-7 7 7 7"))
    val ChevronRight = icon(p("M9 5l7 7-7 7"))
    val ChevronDown = icon(p("M6 9l6 6 6-6"))
    val Info = icon(c(12f, 12f, 8.5f), p("M12 11v5.5M12 7.8v.3"))
    val Sim = icon(p("M7 3h7l4 4v14H7z"), r(9.5f, 11f, 6f, 6f, 1f))
    val Plus = icon(p("M12 5v14M5 12h14"))
    val PlusCircle = icon(c(12f, 12f, 8.5f), p("M12 8.5v7M8.5 12h7"))
    val ArrowUp = icon(p("M12 19V5M6 11l6-6 6 6"))
    val Close = icon(p("M6 6l12 12M18 6L6 18"))
    val Photo = icon(r(3.5f, 5f, 17f, 14f, 2.5f), c(9f, 10f, 1.6f), p("M4 17l5-4.5 4 3.5 3-2.5 4 3.5"))
    val Camera = icon(p("M4 8h3l2-2.5h6L17 8h3v11H4z"), c(12f, 13f, 3.5f))
    val Lines = icon(p("M5 6h14M5 11h14M5 16h9"))
    val Trash = icon(p("M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13"))
    val Copy = icon(r(8f, 8f, 12f, 12f, 2f), p("M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"))
    val Appearance = icon(c(12f, 12f, 8.5f), p("M12 3.5v17"), p("M12 7h4M12 11h6M12 15h5"))
    val Question = icon(c(12f, 12f, 8.5f), p("M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .8-1 1.5v.5M12 16.5v.3"))
    val Blocked = icon(c(12f, 12f, 8.5f), p("M6 6l12 12"))
    val Lock = icon(r(5f, 10.5f, 14f, 10f, 2f), p("M8 10.5V8a4 4 0 0 1 8 0v2.5"))

    val all: Map<String, ChatraIcon> = mapOf(
        "Chat" to Chat, "Check" to Check, "Minus" to Minus, "More" to More, "Search" to Search, "Pin" to Pin,
        "Parcel" to Parcel, "Person" to Person, "Alert" to Alert, "Group" to Group, "Clock" to Clock,
        "Sliders" to Sliders, "Compose" to Compose, "ChevronLeft" to ChevronLeft, "ChevronRight" to ChevronRight,
        "ChevronDown" to ChevronDown, "Info" to Info, "Sim" to Sim, "Plus" to Plus, "PlusCircle" to PlusCircle,
        "ArrowUp" to ArrowUp, "Close" to Close, "Photo" to Photo, "Camera" to Camera, "Lines" to Lines,
        "Trash" to Trash, "Copy" to Copy, "Appearance" to Appearance, "Question" to Question,
        "Blocked" to Blocked, "Lock" to Lock,
    )
}

/**
 * Rysuje ikonę w rozmiarze [size] z grubością linii [strokeWidth] w jednostkach viewBox 24
 * (tak jak `stroke-width` w SVG, więc skaluje się z rozmiarem). Ikony są dekoracyjne;
 * opis dostępności nadaje element nadrzędny.
 */
@Composable
fun Icon(icon: ChatraIcon, size: Dp, color: Color, strokeWidth: Float = 2f, modifier: Modifier = Modifier) {
    val paths = remember(icon) { icon.paths }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, pivot = Offset.Zero) {
            val style = if (icon.filled) Fill else Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            icon.shapes.forEachIndexed { i, shape ->
                when (shape) {
                    is IconShape.PathData -> drawPath(paths[i] ?: Path(), color, style = style)
                    is IconShape.Circle -> drawCircle(color, shape.r, Offset(shape.cx, shape.cy), style = style)
                    is IconShape.Rect -> drawRoundRect(
                        color, Offset(shape.x, shape.y), Size(shape.width, shape.height),
                        CornerRadius(shape.rx), style = style,
                    )
                }
            }
        }
    }
}
