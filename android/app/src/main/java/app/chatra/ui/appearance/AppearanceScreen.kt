package app.chatra.ui.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.chatra.core.designsystem.background.*
import app.chatra.core.designsystem.component.*
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.core.model.Composition as AppearanceComposition
import app.chatra.core.model.Density as ListDensity
import app.chatra.di.PreferenceStore
import app.chatra.ui.*
import app.chatra.ui.common.drawTopLine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(store: PreferenceStore<AppearancePrefs>, onBack: () -> Unit) {
    val prefs by store.value.collectAsState()
    val scope = rememberCoroutineScope()
    fun update(transform: (AppearancePrefs) -> AppearancePrefs) { scope.launch { store.update(transform) } }
    val chrome = LocalChrome.current
    ChatraScreen(Backgrounds.Appearance) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = chrome.top(50.dp), bottom = chrome.bottom(20.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCircleButton(ChatraIcons.ChevronLeft, "Wróć do ustawień", onBack, size = 46.dp)
                BasicText("Wygląd", Modifier.semantics { heading() }, style = ChatraType.AppearanceTitle)
            }
            ConversationPreview(prefs)
            Row(Modifier.fillMaxWidth().semantics { selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val names = listOf("Szkło", "Mgła", "Grafit", "Kontrast")
                val swatches = listOf(listOf(Color(0xFF9CC3FF), Color(0xFFFFC2AE)), listOf(Color(0xFFE4E8F2), Color(0xFFF7F8FC)), listOf(Color(0xFF1A1F2E), Color(0xFF3A3F5C)), listOf(Color.Black, Color.White))
                AppearanceComposition.entries.forEachIndexed { i, item ->
                    val active = prefs.composition == item
                    val shape = RoundedCornerShape(20.dp)
                    Column(Modifier.weight(1f).height(84.dp).clip(shape).background(if (active) Color.White else ChatraColors.white(.6f)).border(if (active) 2.dp else cssBorder(), if (active) ChatraColors.Accent else ChatraColors.white(.95f), shape).selectable(active, role = Role.RadioButton) { update { it.copy(composition = item) } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (i == 3) Brush.horizontalGradient(0f to Color.Black, .5f to Color.Black, .5f to Color.White, 1f to Color.White) else Brush.linearGradient(swatches[i])).border(cssBorder(), ChatraColors.ink(if (i == 3) .3f else .1f), CircleShape))
                        BasicText(names[i], style = chatraText(12f, if (active) 600 else 500))
                    }
                }
            }
            if (prefs.composition != AppearanceComposition.Glass || prefs.mode != ThemeMode.Light || prefs.density != ListDensity.Standard || prefs.increaseContrast) {
                BasicText("Podgląd roboczy. Pozostałe kompozycje, tryb ciemny i układ kompaktowy wymagają dopracowania projektu przed zastosowaniem w całej aplikacji.", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = chatraText(13f, lineHeight = 18f, color = ChatraColors.TextSecondary))
            }
            GlassSurface(GlassStyles.Card.copy(from = ChatraColors.white(.82f), to = ChatraColors.white(.58f), border = ChatraColors.white(.9f)), RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Card)) {
                Column(Modifier.padding(cssBorder())) {
                    SettingRow("Tryb") {
                        Segments(listOf("Jasny", "Ciemny", "Auto"), prefs.mode.ordinal, "Tryb kolorów") { index -> update { it.copy(mode = ThemeMode.entries[index]) } }
                    }
                    SettingRow("Gęstość", line = true) {
                        Segments(listOf("Standardowa", "Kompaktowa"), prefs.density.ordinal, "Gęstość listy") { index -> update { it.copy(density = ListDensity.entries[index]) } }
                    }
                    Column(Modifier.fillMaxWidth().drawTopLine(ChatraColors.Ink06).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText("Rozmiar tekstu", style = chatraText(16f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BasicText("A", style = chatraText(13f, 600))
                            TextSlider(prefs.textStep, { value -> update { it.copy(textStep = value) } }, Modifier.weight(1f))
                            BasicText("A", style = chatraText(21f, 600))
                        }
                    }
                    Row(Modifier.fillMaxWidth().drawTopLine(ChatraColors.Ink06).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText("Ogranicz przezroczystość", style = chatraText(16f))
                            BasicText("Panele staną się matowe, a tekst wyraźniejszy.", style = chatraText(13f, lineHeight = 17f, color = ChatraColors.TextSecondary))
                        }
                        ChatraSwitch(prefs.reduceTransparency, { checked -> update { it.copy(reduceTransparency = checked) } }, "Ogranicz przezroczystość")
                    }
                    Row(Modifier.fillMaxWidth().drawTopLine(ChatraColors.Ink06).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BasicText("Zwiększ kontrast", Modifier.weight(1f), style = chatraText(16f))
                        ChatraSwitch(prefs.increaseContrast, { checked -> update { it.copy(increaseContrast = checked) } }, "Zwiększ kontrast")
                    }
                }
            }
            Box(Modifier.align(Alignment.CenterHorizontally).heightIn(min = 44.dp).semantics { role = Role.Button }.pressable({ update { AppearancePrefs.Default } }).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                BasicText("Przywróć ustawienia domyślne", style = chatraText(16f, color = ChatraColors.Link))
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, line: Boolean = false, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().then(if (line) Modifier.drawTopLine(ChatraColors.Ink06) else Modifier).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(label, Modifier.weight(1f), style = chatraText(16f))
        control()
    }
}

@Composable
private fun Segments(labels: List<String>, selected: Int, label: String, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(ChatraColors.Ink06).padding(3.dp).semantics { contentDescription = label; selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { i, text ->
            val shape = RoundedCornerShape(9.dp)
            Box(Modifier.height(32.dp).then(if (i == selected) Modifier.cssShadow(shape, CssShadow(1.dp, 4.dp, Color(27,35,74).copy(alpha = .15f))) else Modifier).clip(shape).then(if (i == selected) Modifier.background(Color.White) else Modifier).selectable(i == selected, role = Role.RadioButton, onClick = { onSelect(i) }).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                BasicText(text, style = chatraText(14f, if (i == selected) 600 else 400, color = if (i == selected) ChatraColors.TextPrimary else ChatraColors.TextTertiary))
            }
        }
    }
}

@Composable
private fun TextSlider(step: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val change by rememberUpdatedState(onChange)
    Canvas(modifier.height(30.dp).semantics {
        contentDescription = "Rozmiar tekstu"; stateDescription = "$step z 7"
        progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 1f..7f, 5)
        setProgress { change(it.roundToInt().coerceIn(1, 7)); true }
    }.pointerInput(Unit) {
        detectTapGestures { pos -> change((1 + ((pos.x - 8.dp.toPx()) / (size.width - 16.dp.toPx())) * 6).roundToInt().coerceIn(1, 7)) }
    }.pointerInput(Unit) {
        detectDragGestures { event, _ -> event.consume(); change((1 + ((event.position.x - 8.dp.toPx()) / (size.width - 16.dp.toPx())) * 6).roundToInt().coerceIn(1, 7)) }
    }) {
        val radius = 8.dp.toPx(); val y = size.height / 2
        val x = radius + (size.width - 2 * radius) * (step - 1) / 6f
        drawLine(ChatraColors.ink(.2f), Offset(radius, y), Offset(size.width - radius, y), 4.dp.toPx())
        drawLine(ChatraColors.Accent, Offset(radius, y), Offset(x, y), 4.dp.toPx())
        drawCircle(ChatraColors.Accent, radius, Offset(x, y))
    }
}

@Composable
private fun ConversationPreview(prefs: AppearancePrefs) {
    val dark = prefs.composition == AppearanceComposition.Graphite || prefs.mode == ThemeMode.Dark || (prefs.mode == ThemeMode.Auto && isSystemInDarkTheme())
    val ink = if (dark) Color.White else ChatraColors.TextPrimary
    val matte = prefs.reduceTransparency || prefs.increaseContrast || prefs.composition == AppearanceComposition.Contrast
    val surface = if (dark) Color(0xFF34394C) else Color.White.copy(alpha = if (matte) 1f else .85f)
    val base = if (dark) Color(0xFF1A1F2E) else Color(0xFFF2F4FA)
    val scale = if (LocalContainer.current.referenceFrame) prefs.textScale else 1f
    val background = CssBackground(base, if (dark || prefs.composition == AppearanceComposition.Contrast || prefs.composition == AppearanceComposition.Mist) emptyList() else listOf(RadialLayer(.8f, .6f, 0f, 0f, Color(0xFF9CC3FF)), RadialLayer(.7f, .6f, 1f, 1f, Color(0xFFFFC2AE))))
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().height(196.dp).cssShadow(shape, CssShadow(10.dp, 30.dp, Color(27,35,74).copy(alpha = .12f))).clip(shape).background(base).border(cssBorder(), ChatraColors.white(.95f), shape).semantics { contentDescription = "Podgląd rozmowy w wybranym wyglądzie" }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCssBackground(background) }
        Column(Modifier.fillMaxSize().padding(14.dp + cssBorder()), verticalArrangement = Arrangement.spacedBy(if (prefs.density == ListDensity.Compact) 3.dp else 6.dp)) {
            Row(Modifier.align(Alignment.CenterHorizontally).height(32.dp).clip(RoundedCornerShape(15.dp)).background(surface).border(cssBorder(), ChatraColors.white(.95f), RoundedCornerShape(15.dp)).padding(start = 4.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(AvatarTones.Rose.top, AvatarTones.Rose.bottom))))
                BasicText("Ola Nowak", style = chatraText(12f * scale, 600, color = ink))
            }
            val incoming = RoundedCornerShape(16.dp,16.dp,16.dp,5.dp)
            BasicText("Dotarły, wyglądają super!", Modifier.clip(incoming).background(surface).border(cssBorder(), ChatraColors.white(.95f), incoming).padding(horizontal = 12.dp + cssBorder(), vertical = 7.dp + cssBorder()), style = chatraText(14f * scale, color = ink))
            BasicText("To w sobotę o 18:00?", Modifier.align(Alignment.End).clip(RoundedCornerShape(16.dp,16.dp,5.dp,16.dp)).background(if (prefs.increaseContrast) Color(0xFF0046AA) else ChatraColors.Accent).padding(horizontal = 12.dp, vertical = 7.dp), style = chatraText(14f * scale, color = Color.White))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(17.dp)).background(surface).border(cssBorder(), ChatraColors.white(.95f), RoundedCornerShape(17.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicText("Wiadomość SMS", style = chatraText(13f * scale, color = if (dark) Color.White else ChatraColors.TextSecondary))
            }
        }
    }
}
