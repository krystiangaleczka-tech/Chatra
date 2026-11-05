package app.chatra.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chatra.core.designsystem.icon.ChatraIcon
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText

/** Odcień awatara: gradient 180° i kolor inicjałów z plansz. */
@Immutable
data class AvatarTone(val top: Color, val bottom: Color, val ink: Color)

object AvatarTones {
    val Slate = AvatarTone(Color(0xFFD6DDEE), Color(0xFFB7C1D9), Color(0xFF1E2433))
    val Rose = AvatarTone(Color(0xFFF1D9D2), Color(0xFFDDB8AE), Color(0xFF3A1E16))
    val Mint = AvatarTone(Color(0xFFD9E8DF), Color(0xFFB8D3C3), Color(0xFF143322))
    val Neutral = AvatarTone(Color(0xFFE3E5EA), Color(0xFFC9CDD6), Color(0xFF1E2433))
    val Lilac = AvatarTone(Color(0xFFE7DCF2), Color(0xFFCDBDE3), Color(0xFF2A1A3E))
    val Sky = AvatarTone(Color(0xFFD3E3F1), Color(0xFFB2CBE2), Color(0xFF14283A))

    private val byName = mapOf("slate" to Slate, "rose" to Rose, "mint" to Mint, "neutral" to Neutral, "lilac" to Lilac, "sky" to Sky)
    private val rotation = listOf(Slate, Rose, Lilac, Mint, Sky)

    fun named(name: String?): AvatarTone? = name?.let { byName[it] }

    /** Odcień z klucza uczestników (stabilny między uruchomieniami). */
    fun forKey(key: String): AvatarTone = rotation[Math.floorMod(key.hashCode(), rotation.size)]
}

/** Zawartość awatara: inicjały albo ikona (kurier bez kontaktu, paczkomat, grupa). */
sealed interface AvatarContent {
    data class Initials(val text: String) : AvatarContent
    data class Glyph(val icon: ChatraIcon) : AvatarContent
}

@Composable
fun Avatar(content: AvatarContent, tone: AvatarTone, size: Dp = 46.dp, textStyle: TextStyle = chatraText(17f, 600), iconSize: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.verticalGradient(listOf(tone.top, tone.bottom))),
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            is AvatarContent.Initials -> BasicText(content.text, style = textStyle.copy(color = tone.ink))
            is AvatarContent.Glyph -> Icon(content.icon, iconSize, tone.ink, 1.8f)
        }
    }
}

/** Plakietka nieprzeczytanych: min 22 × 22, promień 11, padding 0 6 (border-box). */
@Composable
fun UnreadBadge(count: Int, contentDescription: String, color: Color = ChatraColors.Accent) {
    Box(
        Modifier
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .defaultMinSize(minWidth = 22.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(color)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(count.toString(), style = chatraText(13f, 700, color = Color.White)) }
}

/** Mała etykieta („Kod”, „Nieznany numer”): 11/600, padding 2 7, promień 9, tło ink 0.07. */
@Composable
fun SmallTag(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(ChatraColors.Ink07).padding(horizontal = 7.dp, vertical = 2.dp)) {
        BasicText(text, style = chatraText(11f, 600, color = ChatraColors.TextTertiary))
    }
}

/** Etykieta sekcji: 13/600, wielkie litery, 0.04 em. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = ChatraColors.TextSecondary) {
    BasicText(text.uppercase(), modifier, style = chatraText(13f, 600, letterSpacingEm = 0.04f, color = color))
}

/** Przełącznik 52 × 32 z gałką 28 (S09, S10): kolor włączenia #0B7A52. */
@Composable
fun ChatraSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .semantics {
                role = Role.Switch
                contentDescription = label
                stateDescription = if (checked) "włączone" else "wyłączone"
            }
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) ChatraColors.Success else ChatraColors.ink(0.16f))
            .pressable({ onCheckedChange(!checked) })
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(28.dp).cssShadow(CircleShape, CssShadow(2.dp, 6.dp, Color.Black.copy(alpha = 0.2f))).clip(CircleShape).background(Color.White))
    }
}

/** Zakładka docka. */
@Immutable
data class DockTab(val label: String, val icon: ChatraIcon)

/**
 * Dolny dock (S02/S06/S10): pigułka 62 (border-box, padding 4, gap 2) i okrągła akcja Nowa
 * wiadomość 62 + obramowanie 1 (64 łącznie), odstęp 10.
 */
@Composable
fun Dock(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.semantics { contentDescription = "Główne sekcje" }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassSurface(
            GlassStyles.Dock, RoundedCornerShape(31.dp),
            Modifier.weight(1f).height(62.dp),
            shadows = listOf(Shadows.Dock),
        ) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEachIndexed { i, tab -> DockItem(tab, i == selected) { onSelect(i) } }
            }
        }
        Box(
            Modifier
                .semantics { contentDescription = "Nowa wiadomość"; role = Role.Button }
                .size(64.dp)
                .cssShadow(CircleShape, Shadows.Fab)
                .clip(CircleShape)
                .background(ChatraColors.Accent)
                .border(1.dp, ChatraColors.white(0.6f), CircleShape)
                .topHighlight(CircleShape, ChatraColors.white(0.5f))
                .pressable(onCompose),
            contentAlignment = Alignment.Center,
        ) { Icon(ChatraIcons.Compose, 26.dp, Color.White, 2f) }
    }
}

@Composable
private fun RowScope.DockItem(tab: DockTab, active: Boolean, onClick: () -> Unit) {
    val color = if (active) ChatraColors.Accent else ChatraColors.TextTertiary
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(27.dp))
            .then(if (active) Modifier.background(ChatraColors.white(0.85f)) else Modifier)
            .semantics(mergeDescendants = true) { role = Role.Tab; selected = active }
            .pressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(tab.icon, 24.dp, color, 1.9f)
        BasicText(tab.label, style = chatraText(11f, if (active) 600 else 500, color = color))
    }
}

/** Rząd z ikoną po lewej: tylko układ, bez semantyki. */
@Composable
fun IconLabel(icon: ChatraIcon, text: String, style: TextStyle, iconSize: Dp, strokeWidth: Float, gap: Dp = 6.dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, iconSize, style.color, strokeWidth)
        BasicText(text, style = style)
    }
}

fun Modifier.fixedWidth(width: Dp) = this.width(width)
