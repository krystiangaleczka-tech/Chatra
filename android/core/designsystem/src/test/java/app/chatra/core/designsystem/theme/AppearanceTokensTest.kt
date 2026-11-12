package app.chatra.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class AppearanceTokensTest {
    private fun luminance(c: Color): Double {
        fun channel(v: Float) = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        return .2126 * channel(c.red) + .7152 * channel(c.green) + .0722 * channel(c.blue)
    }
    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun allCompositionsKeepBodySecondaryAndLinksReadableInBothModes() {
        for (composition in listOf("Glass", "Mist", "Graphite", "Contrast")) {
            for (dark in listOf(false, true)) for (high in listOf(false, true)) {
                val t = AppearanceTokens(dark, high || composition == "Contrast", composition = composition)
                for (bg in listOf(t.background, t.surface)) for (text in listOf(t.primary, t.secondary, t.link)) {
                    assertTrue("$composition dark=$dark high=$high: ${contrast(text, bg)}", contrast(text, bg) >= 4.5)
                }
            }
        }
    }
    @Test fun defaultPalettePreservesReferenceInkAndResetBackground() {
        val t = AppearanceTokens()
        assertEquals(Color(0xFF0B0D14), t.primary)
        assertEquals(Color(0xFF3A4054), t.secondary)
        assertEquals(Color(0xFFEDF0F7), t.background)
        assertFalse(t.dark); assertFalse(t.matte); assertFalse(t.compact)
    }
}
