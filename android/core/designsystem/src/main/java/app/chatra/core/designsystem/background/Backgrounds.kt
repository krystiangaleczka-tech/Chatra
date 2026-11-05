package app.chatra.core.designsystem.background

import androidx.compose.ui.graphics.Color

private fun l(rx: Int, ry: Int, cx: Int, cy: Int, color: Long) = RadialLayer(rx / 100f, ry / 100f, cx / 100f, cy / 100f, Color(color))

/** Tła dziesięciu plansz, przepisane z deklaracji `background` korzeni szablonów. */
object Backgrounds {
    val Main = CssBackground(
        Color(0xFFEDF0F7),
        listOf(l(65, 38, 8, 6, 0xFF9CC3FF), l(55, 35, 100, 28, 0xFFFFC2AE), l(70, 40, 12, 80, 0xFFCDBFFF), l(60, 35, 96, 98, 0xFF9FE3DA)),
    )

    /** S01, S02, S09. */
    val Start = Main
    val Inbox = Main
    val Appearance = Main

    /** S03, S04, S05: rozmowa i arkusze nad nią. */
    val Conversation = CssBackground(
        Color(0xFFEEF1F8),
        listOf(l(70, 40, 0, 20, 0xFFB9D3FF), l(60, 40, 100, 55, 0xFFFFD3C4), l(80, 40, 30, 100, 0xFFD6CBFF)),
    )

    val Scheduled = CssBackground(
        Color(0xFFEDF0F7),
        listOf(l(65, 38, 92, 6, 0xFF9CC3FF), l(55, 35, 0, 40, 0xFFFFC2AE), l(70, 40, 90, 85, 0xFFCDBFFF)),
    )

    val Search = CssBackground(
        Color(0xFFEDF0F7),
        listOf(l(65, 38, 8, 6, 0xFF9CC3FF), l(55, 35, 100, 40, 0xFFFFC2AE), l(70, 40, 12, 90, 0xFFCDBFFF)),
    )

    val NewMessage = CssBackground(
        Color(0xFFEDF0F7),
        listOf(l(65, 38, 8, 6, 0xFF9CC3FF), l(55, 35, 100, 50, 0xFFFFC2AE), l(70, 40, 12, 95, 0xFFCDBFFF)),
    )

    val Settings = CssBackground(
        Color(0xFFEDF0F7),
        listOf(l(65, 38, 92, 4, 0xFF9CC3FF), l(55, 35, 0, 35, 0xFFFFC2AE), l(70, 40, 100, 75, 0xFFCDBFFF), l(60, 35, 0, 100, 0xFF9FE3DA)),
    )

    /** Przyciemnienie pod arkuszem (S04, S05). */
    val SheetScrim = Color(14, 18, 32).copy(alpha = 0.28f)
}
