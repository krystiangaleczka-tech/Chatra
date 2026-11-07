package app.chatra.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chatra.core.designsystem.component.GlassStyles
import app.chatra.core.designsystem.component.GlassSurface
import app.chatra.core.designsystem.component.Shadows
import app.chatra.core.designsystem.component.cssShadow
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.component.topHighlight
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.ui.LocalChrome

/**
 * Arkusz S04/S05: left/right/bottom 8, promień 40, szkło 170° 0.86 → 0.70, blur 40, saturacja 180,
 * refleks i cień 0 −10 40. [top] to górna krawędź we wzorcu (196 dla S04, 120 dla S05).
 */
@Composable
fun SheetSurface(top: Dp, label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val chrome = LocalChrome.current
    Box(Modifier.fillMaxSize()) {
        GlassSurface(
            GlassStyles.Sheet, RoundedCornerShape(40.dp),
            modifier
                .padding(start = 8.dp, end = 8.dp, top = chrome.top(top), bottom = chrome.bottom(8.dp))
                .fillMaxSize()
                .semantics { contentDescription = label },
            shadows = listOf(Shadows.Sheet),
        ) {
            // border-box: obramowanie 1 leży wewnątrz, padding 8/18/20 liczony od niego (treść S04: y 205, szerokość 336).
            Column(Modifier.fillMaxSize().padding(start = 19.dp, end = 19.dp, top = 9.dp, bottom = 21.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(ChatraColors.ink(0.22f)))
    }
}

/** Tytuł 20/700 −0.01 em i okrągłe zamknięcie 36 (ikona 18, linia 2.4). */
@Composable
fun SheetTitle(title: String, onClose: () -> Unit, closeLabel: String = "Zamknij") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, Modifier.weight(1f).semantics { heading() }, style = chatraText(20f, 700, letterSpacingEm = -0.01f))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(ChatraColors.Ink07)
                .semantics { role = Role.Button; contentDescription = closeLabel }.pressable(onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(ChatraIcons.Close, 18.dp, ChatraColors.TextTertiary, 2.4f) }
    }
}

/** Główna akcja 54 dp, promień 27, akcent, 17/600 biały, refleks 0.45 i cień 0 10 24. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(27.dp)
    Box(
        modifier.fillMaxWidth().height(54.dp)
            .then(if (enabled) Modifier.cssShadow(shape, app.chatra.core.designsystem.component.CssShadow(10.dp, 24.dp, Color(10, 60, 160).copy(alpha = 0.26f))) else Modifier)
            .clip(shape).background(if (enabled) ChatraColors.Accent else ChatraColors.Accent.copy(alpha = 0.4f))
            .topHighlight(shape, ChatraColors.white(0.45f))
            .semantics { role = Role.Button }
            .pressable(onClick, enabled),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = chatraText(17f, 600, color = Color.White)) }
}

/**
 * Opcja z radiem (S04 „Jak wysłać?”, S08): wybrana — białe tło i obramowanie 1.5 akcentu,
 * pozostałe — biel 0.55 i obramowanie 1 bieli 0.95; padding 12/14 (S08: 14/14), promień 18.
 * Obramowanie jak w CSS `content-box` leży poza paddingiem, więc opcja jest o 2 × obramowanie wyższa.
 */
@Composable
fun RadioOption(
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val border = if (selected) 1.5.dp else 1.dp
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Color.White else ChatraColors.white(0.55f))
            .border(border, if (selected) ChatraColors.Accent else ChatraColors.white(0.95f), shape)
            .semantics(mergeDescendants = true) { role = Role.RadioButton; this.selected = selected }
            .pressable(onSelect)
            .padding(horizontal = horizontalPadding + border, vertical = verticalPadding + border),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Radio(selected, Modifier.padding(top = 3.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * Radio w stylu Chrome Android (accent-color): CSS 20 × 20, ale Chrome rysuje je w polu 16 × 20
 * (pomiar CH002, S04), więc tekst opcji zaczyna się 16 + gap od radia.
 */
@Composable
fun Radio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(16.dp, 20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(16.dp).clip(CircleShape)
                .border(if (selected) 2.dp else 1.5.dp, if (selected) ChatraColors.Accent else ChatraColors.ink(0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(ChatraColors.Accent)) }
    }
}
