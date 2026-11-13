package app.chatra.core.designsystem.theme

import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

/** Render tokens. The original light Glass palette is the default and reference reset. */
@Immutable data class AppearanceTokens(
    val dark: Boolean = false, val contrast: Boolean = false, val matte: Boolean = false,
    val composition: String = "Glass", val compact: Boolean = false,
) {
    val primary get() = if (dark) Color(0xFFF4F6FF) else if (contrast) Color.Black else Color(0xFF0B0D14)
    val secondary get() = if (dark) Color(0xFFD1D7E8) else if (contrast) Color(0xFF222222) else Color(0xFF3A4054)
    val surface get() = when {
        dark && contrast -> Color(0xFF0C0C0C)
        dark -> when (composition) {
            "Mist" -> Color(0xFF2D3B49)
            "Graphite" -> Color(0xFF252A3A)
            "Amber" -> Color(0xFF433328)
            "Forest" -> Color(0xFF263C35)
            else -> Color(0xFF282D40)
        }
        contrast -> Color.White
        composition == "Graphite" -> Color(0xFFF0F2F8)
        composition == "Mist" -> Color(0xFFF7FAFD)
        composition == "Amber" -> Color(0xFFFFF7E8)
        composition == "Forest" -> Color(0xFFEFF8F2)
        else -> Color.White
    }
    val background get() = when {
        dark && contrast -> Color.Black
        dark -> when (composition) {
            "Mist" -> Color(0xFF1D2630)
            "Graphite" -> Color(0xFF171A27)
            "Amber" -> Color(0xFF2D211A)
            "Forest" -> Color(0xFF162923)
            else -> Color(0xFF131724)
        }
        contrast -> Color.White
        composition == "Graphite" -> Color(0xFFD1D8E6)
        composition == "Mist" -> Color(0xFFE8EDF4)
        composition == "Amber" -> Color(0xFFF4DFC3)
        composition == "Forest" -> Color(0xFFD0E8DA)
        else -> Color(0xFFEDF0F7)
    }
    val accent get() = if (contrast) Color(0xFF0046AA) else Color(0xFF0A6CFF)
    val link get() = if (dark) Color(0xFF9BC7FF) else if (contrast) Color(0xFF003B91) else if (composition == "Graphite") Color(0xFF004BB8) else Color(0xFF0058D6)
}
val LocalAppearanceTokens = staticCompositionLocalOf { AppearanceTokens() }
