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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
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
import app.chatra.ui.common.TextAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(store: PreferenceStore<AppearancePrefs>, onPro: () -> Unit = {}, onBack: () -> Unit) {
    val stored by store.value.collectAsState()
    val billing = LocalContainer.current.billing
    val entitlement = billing?.state?.collectAsStateWithLifecycle()?.value?.entitled == true
    val prefs = stored.withProAccess(entitlement)
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val swatches = listOf(listOf(Color(0xFF9CC3FF), Color(0xFFFFC2AE)), listOf(Color(0xFFE4E8F2), Color(0xFFF7F8FC)), listOf(Color(0xFF1A1F2E), Color(0xFF3A3F5C)), listOf(Color.Black, Color.White))
                AppearanceComposition.entries.filter { !it.pro }.chunked(if (LocalDensity.current.fontScale > 1.3f) 2 else 4).forEach { chunk ->
                    Row(Modifier.fillMaxWidth().semantics { selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEach { item ->
                            val i = item.ordinal
                            val active = prefs.composition == item
                            val shape = RoundedCornerShape(20.dp)
                            Column(Modifier.weight(1f).heightIn(min = 84.dp).clip(shape).background(app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.surface.copy(alpha = if (active) 1f else .6f)).border(if (active) 2.dp else cssBorder(), if (active) ChatraColors.Accent else ChatraColors.white(.95f), shape).selectable(active, role = Role.RadioButton) { update { it.copy(composition = item) } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                                Box(Modifier.size(38.dp).clip(CircleShape).background(if (i == 3) Brush.horizontalGradient(0f to Color.Black, .5f to Color.Black, .5f to Color.White, 1f to Color.White) else Brush.linearGradient(swatches[i])).border(cssBorder(), ChatraColors.ink(if (i == 3) .3f else .1f), CircleShape))
                                BasicText(item.label, style = chatraText(12f, if (active) 600 else 500))
                            }
                        }
                    }
                }
                if (entitlement) {
                    AppearanceComposition.entries.filter { it.pro }.forEach { item ->
                        val active = prefs.composition == item
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(active, role = Role.RadioButton) {
                                update { it.copy(composition = item) }
                            },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val palette = AppearanceTokens(composition = item.name)
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(palette.background, palette.surface))))
                            BasicText(item.label, style = chatraText(16f, if (active) 600 else 400))
                        }
                    }
                } else if (billing != null) {
                    TextAction("Bursztyn i Las w Chatra Pro", onPro)
                }
            }
            GlassSurface(GlassStyles.Card.copy(from = ChatraColors.white(.82f), to = ChatraColors.white(.58f), border = ChatraColors.white(.9f)), RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Card)) {
                Column(Modifier.padding(cssBorder())) {
                    SettingRow("Tryb") { stacked ->
                        Segments(listOf("Jasny", "Ciemny", "Auto"), prefs.mode.ordinal, "Tryb kolorów", expanded = stacked) { index -> update { it.copy(mode = ThemeMode.entries[index]) } }
                    }
                    SettingRow("Gęstość", line = true) { stacked ->
                        Segments(listOf("Standardowa", "Kompaktowa"), prefs.density.ordinal, "Gęstość listy", expanded = stacked) { index -> update { it.copy(density = ListDensity.entries[index]) } }
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
private fun SettingRow(label: String, line: Boolean = false, control: @Composable (Boolean) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (line) Modifier.drawTopLine(ChatraColors.Ink06) else Modifier).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
        if (fontScale > 1.1f || maxWidth < 310.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(label, style = chatraText(16f))
                control(true)
            }
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(label, Modifier.weight(1f), style = chatraText(16f))
            control(false)
        }
    }
}

@Composable
private fun Segments(labels: List<String>, selected: Int, label: String, expanded: Boolean = false, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val requiredWidth = labels.size * (labels.maxOfOrNull { measurer.measure(it, chatraText(14f, 600)).size.width } ?: 0) +
        with(density) { (labels.size * 24 + (labels.size - 1) * 2 + 6).dp.toPx() }
    BoxWithConstraints(Modifier.then(if (expanded) Modifier.fillMaxWidth() else Modifier)) {
        val group = Modifier.clip(RoundedCornerShape(12.dp)).background(ChatraColors.Ink06).padding(3.dp)
            .semantics { contentDescription = label; selectableGroup() }
        if (expanded && with(density) { maxWidth.toPx() } < requiredWidth) {
            Column(group.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                labels.forEachIndexed { i, text -> SegmentButton(text, i == selected, { onSelect(i) }, Modifier.fillMaxWidth()) }
            }
        } else Row(group, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { i, text ->
                SegmentButton(text, i == selected, { onSelect(i) }, if (expanded) Modifier.weight(1f) else Modifier)
            }
        }
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(9.dp)
    Box(modifier.heightIn(min = 32.dp)
        .then(if (selected) Modifier.cssShadow(shape, CssShadow(1.dp, 4.dp, Color(27,35,74).copy(alpha = .15f))) else Modifier)
        .clip(shape).then(if (selected) Modifier.background(app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.surface) else Modifier)
        .selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, maxLines = 1, style = chatraText(14f, if (selected) 600 else 400,
            color = if (selected) ChatraColors.TextPrimary else ChatraColors.TextTertiary))
    }
}

@Composable
private fun TextSlider(step: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val track = ChatraColors.ink(.2f)
    val accent = ChatraColors.Accent
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
        drawLine(track, Offset(radius, y), Offset(size.width - radius, y), 4.dp.toPx())
        drawLine(accent, Offset(radius, y), Offset(x, y), 4.dp.toPx())
        drawCircle(accent, radius, Offset(x, y))
    }
}

@Composable
private fun ConversationPreview(prefs: AppearancePrefs) {
    val dark = prefs.mode == ThemeMode.Dark || (prefs.mode == ThemeMode.Auto && isSystemInDarkTheme())
    val ink = if (dark) Color.White else ChatraColors.TextPrimary
    val matte = prefs.reduceTransparency || prefs.increaseContrast || prefs.composition == AppearanceComposition.Contrast
    val tokens = app.chatra.core.designsystem.theme.LocalAppearanceTokens.current
    val surface = tokens.surface.copy(alpha = if (matte) 1f else .85f)
    val base = if (prefs == AppearancePrefs.Default) Color(0xFFF2F4FA) else tokens.background
    val scale = if (LocalContainer.current.referenceFrame) prefs.textScale else 1f
    val background = CssBackground(base, if (dark || prefs.composition != AppearanceComposition.Glass) emptyList() else listOf(RadialLayer(.8f, .6f, 0f, 0f, Color(0xFF9CC3FF)), RadialLayer(.7f, .6f, 1f, 1f, Color(0xFFFFC2AE))))
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().height(196.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)).cssShadow(shape, CssShadow(10.dp, 30.dp, Color(27,35,74).copy(alpha = .12f))).clip(shape).background(base).border(cssBorder(), ChatraColors.white(.95f), shape).semantics { contentDescription = "Podgląd rozmowy w wybranym wyglądzie" }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCssBackground(background) }
        Column(Modifier.fillMaxSize().padding(14.dp + cssBorder()), verticalArrangement = Arrangement.spacedBy(if (prefs.density == ListDensity.Compact) 3.dp else 6.dp)) {
            Row(Modifier.align(Alignment.CenterHorizontally).heightIn(min = 32.dp).clip(RoundedCornerShape(15.dp)).background(surface).border(cssBorder(), ChatraColors.white(.95f), RoundedCornerShape(15.dp)).padding(start = 4.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(AvatarTones.Rose.top, AvatarTones.Rose.bottom))))
                BasicText("Ola Nowak", style = chatraText(12f * scale, 600, color = ink))
            }
            val incoming = RoundedCornerShape(16.dp,16.dp,16.dp,5.dp)
            BasicText("Dotarły, wyglądają super!", Modifier.clip(incoming).background(surface).border(cssBorder(), ChatraColors.white(.95f), incoming).padding(horizontal = 12.dp + cssBorder(), vertical = 7.dp + cssBorder()), style = chatraText(14f * scale, color = ink))
            BasicText("To w sobotę o 18:00?", Modifier.align(Alignment.End).clip(RoundedCornerShape(16.dp,16.dp,5.dp,16.dp)).background(if (prefs.increaseContrast) Color(0xFF0046AA) else ChatraColors.Accent).padding(horizontal = 12.dp, vertical = 7.dp), style = chatraText(14f * scale, color = Color.White))
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(RoundedCornerShape(17.dp)).background(surface).border(cssBorder(), ChatraColors.white(.95f), RoundedCornerShape(17.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicText("Wiadomość SMS", style = chatraText(13f * scale, color = if (dark) Color.White else ChatraColors.TextSecondary))
            }
        }
    }
}
