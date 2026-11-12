package app.chatra.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.chatra.core.designsystem.glass.Backdrop
import app.chatra.core.designsystem.glass.GlassStyle
import app.chatra.core.designsystem.glass.glass
import app.chatra.core.designsystem.icon.ChatraIcon
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.LocalAppearanceTokens

/** Scena tła ekranu, którą próbkują panele (ustawiana przez [app.chatra.core.designsystem.component.ChatraScreen]). */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Ograniczenie przezroczystości (S09): panele matowe zamiast szkła. */
val LocalReduceTransparency = staticCompositionLocalOf { false }

@Composable
fun ProvideBackdrop(backdrop: Backdrop?, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalBackdrop provides backdrop, content = content)

private fun white(a: Float) = Color.White.copy(alpha = a)

/** Parametry `backdrop-filter` i tła powierzchni z plansz (specyfikacja 4, szkło i geometria). */
object GlassStyles {
    val Control = GlassStyle(28.dp, 1.7f, 160f, white(0.74f), white(0.44f), white(0.85f), topHighlight = white(0.95f))
    val Card = GlassStyle(28.dp, 1.7f, 160f, white(0.78f), white(0.52f), white(0.85f), topHighlight = white(0.95f))
    val Sheet = GlassStyle(40.dp, 1.8f, 170f, white(0.86f), white(0.70f), white(0.95f), topHighlight = Color.White)
    val Dock = GlassStyle(30.dp, 1.8f, 160f, white(0.72f), white(0.42f), white(0.85f), topHighlight = white(0.95f))
    val Segment = GlassStyle(28.dp, 1.7f, 160f, white(0.38f), white(0.38f), white(0.75f))

    // S03 rozmowa
    val Bubble = GlassStyle(24.dp, 1.7f, 160f, white(0.88f), white(0.66f), white(0.9f))
    /** S06 „Wymaga decyzji”: ciepły gradient rgba(255,244,236,.92) → biel .62. */
    val Decision = GlassStyle(28.dp, 1.7f, 160f, Color(255, 244, 236).copy(alpha = 0.92f), white(0.62f), white(0.9f), topHighlight = white(0.95f))
    val HdCard = GlassStyle(24.dp, 1.7f, 160f, white(0.86f), white(0.60f), white(0.9f))
    val DayChip = GlassStyle(20.dp, 1f, 180f, white(0.55f), white(0.55f), white(0.8f))
    val SimChip = GlassStyle(24.dp, 1f, 160f, white(0.80f), white(0.50f), white(0.9f))
    val Input = GlassStyle(28.dp, 1.7f, 160f, white(0.86f), white(0.60f), white(0.9f), topHighlight = white(0.95f))
}

/** Jeden cień CSS `box-shadow: x y blur spread color` (bez `inset`). */
@Immutable
data class CssShadow(val y: Dp, val blur: Dp, val color: Color, val x: Dp = 0.dp, val spread: Dp = 0.dp)

object Shadows {
    val Control = CssShadow(6.dp, 18.dp, Color(27, 35, 74).copy(alpha = 0.12f))
    val Search = CssShadow(6.dp, 18.dp, Color(27, 35, 74).copy(alpha = 0.10f))
    val Card = CssShadow(10.dp, 30.dp, Color(27, 35, 74).copy(alpha = 0.10f))
    val Dock = CssShadow(12.dp, 32.dp, Color(27, 35, 74).copy(alpha = 0.18f))
    val Fab = CssShadow(12.dp, 28.dp, Color(10, 60, 160).copy(alpha = 0.32f))
    val Sheet = CssShadow((-10).dp, 40.dp, Color(14, 18, 32).copy(alpha = 0.18f))
    val SegmentThumb = CssShadow(2.dp, 8.dp, Color(27, 35, 74).copy(alpha = 0.12f))
    val Bubble = CssShadow(4.dp, 14.dp, Color(27, 35, 74).copy(alpha = 0.08f))
    val BubbleOut = CssShadow(4.dp, 14.dp, Color(10, 60, 160).copy(alpha = 0.2f))
    val Decision = CssShadow(10.dp, 30.dp, Color(27, 35, 74).copy(alpha = 0.12f))
    val HdCard = CssShadow(6.dp, 18.dp, Color(27, 35, 74).copy(alpha = 0.10f))
}

fun Modifier.cssShadow(shape: Shape, vararg shadows: CssShadow): Modifier =
    shadows.fold(this) { m, s ->
        m.dropShadow(shape, Shadow(radius = s.blur, color = s.color, spread = s.spread, offset = DpOffset(s.x, s.y)))
    }

/** Panel szklany: cień → szkło nad [LocalBackdrop] → ostra treść. */
@Composable
fun GlassSurface(
    style: GlassStyle,
    shape: Shape,
    modifier: Modifier = Modifier,
    shadows: List<CssShadow> = emptyList(),
    backdrop: Backdrop? = LocalBackdrop.current,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = LocalAppearanceTokens.current
    val resolved = if (tokens.dark || tokens.contrast || tokens.composition != "Glass") style.copy(
        from = tokens.surface.copy(alpha = if (tokens.matte) 1f else .94f),
        to = tokens.surface.copy(alpha = if (tokens.matte) 1f else .84f),
        border = tokens.primary.copy(alpha = if (tokens.contrast) .65f else .18f),
        topHighlight = tokens.primary.copy(alpha = .08f),
    ) else style
    Box(
        modifier
            .cssShadow(shape, *shadows.toTypedArray())
            .glass(backdrop, shape, resolved, matte = LocalReduceTransparency.current || tokens.matte),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Okrągła akcja 44 × 44 (więcej, wróć, szczegóły) z ikoną 22. */
@Composable
fun GlassCircleButton(
    icon: ChatraIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    strokeWidth: Float = 2f,
    tint: Color = ChatraColors.TextPrimary,
) {
    GlassSurface(
        GlassStyles.Control, CircleShape,
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .pressable(onClick),
        shadows = listOf(Shadows.Control),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, iconSize, tint, strokeWidth) }
}

val Radius28 = RoundedCornerShape(28.dp)
val Radius40Top = RoundedCornerShape(40.dp)
