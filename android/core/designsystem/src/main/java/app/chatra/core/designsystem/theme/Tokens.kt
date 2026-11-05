package app.chatra.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.chatra.core.designsystem.R

/** Kolory odczytane z HTML (specyfikacja 4). Wartość CSS → stała, bez mapowania na Material. */
object ChatraColors {
    val Accent = Color(0xFF0A6CFF)
    val Link = Color(0xFF0058D6)
    val TextPrimary = Color(0xFF0B0D14)
    val TextSecondary = Color(0xFF3A4054)
    val TextTertiary = Color(0xFF2A3042)
    val Error = Color(0xFFC0262D)
    val ErrorText = Color(0xFFA3141B)
    val Draft = Color(0xFFB4410F)
    val Decision = Color(0xFF9A3A0B)
    val BackgroundMain = Color(0xFFEDF0F7)
    val BackgroundConversation = Color(0xFFEEF1F8)
    val Success = Color(0xFF0B7A52)
    val Ink07 = Color(0x120B0D14) // rgba(11,13,20,0.07): separatory i plakietki
    val Ink06 = Color(0x0F0B0D14)

    fun white(alpha: Float) = Color.White.copy(alpha = alpha)
    fun ink(alpha: Float) = TextPrimary.copy(alpha = alpha)
}

/**
 * G01 (zatwierdzona 05.11.2026): Inter 4.001 w wagach 400–700, te same kontury co web font z bundla.
 * Waga 800 nie jest używana; „HD” rysuje się w 700 jak w goldenie Androida.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_medium, FontWeight.W500),
    Font(R.font.inter_semibold, FontWeight.W600),
    Font(R.font.inter_bold, FontWeight.W700),
)

/** CSS `line-height: normal` dla Inter (typo ascender 1984 + descender 494) / 2048 upm. */
const val InterNormalLineHeight = 1.2099609f

/** Bez dodatkowego paddingu fontu; wysokość linii rozkładana równo jak w CSS (half-leading). */
private val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

fun chatraText(
    size: Float,
    weight: Int = 400,
    lineHeight: Float? = null,
    letterSpacingEm: Float = 0f,
    color: Color = ChatraColors.TextPrimary,
): TextStyle = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    lineHeight = lineHeight?.sp ?: (size * InterNormalLineHeight).sp,
    letterSpacing = if (letterSpacingEm == 0f) TextUnit.Unspecified else letterSpacingEm.em,
    color = color,
    lineHeightStyle = CssLineHeight,
)

/** Role tekstu ze specyfikacji 4; wartości pochodzą ze stylów inline plansz. */
object ChatraType {
    val ListTitle = chatraText(34f, 700, 41f, -0.02f)
    val AppearanceTitle = chatraText(28f, 700, letterSpacingEm = -0.02f)
    val SheetTitle = chatraText(17f, 700)
    val ContactName = chatraText(16f, 600)
    val ContactNameUnread = chatraText(16f, 700)
    val Preview = chatraText(14f, 400, 19f, color = ChatraColors.TextSecondary)
    val PreviewUnread = chatraText(14f, 500, 19f)
    val Bubble = chatraText(16f, 400, 21f)
    val Body = chatraText(16f, 400)
    val SectionLabel = chatraText(13f, 600, letterSpacingEm = 0.04f, color = ChatraColors.TextSecondary)
    val Meta = chatraText(13f, 400, color = ChatraColors.TextSecondary)
    val DockActive = chatraText(11f, 600)
    val Dock = chatraText(11f, 500)
}
