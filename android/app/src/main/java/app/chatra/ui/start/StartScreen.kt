package app.chatra.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.CssShadow
import app.chatra.core.designsystem.component.GlassStyles
import app.chatra.core.designsystem.component.GlassSurface
import app.chatra.core.designsystem.component.cssShadow
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.component.topHighlight
import app.chatra.core.designsystem.glass.GlassStyle
import app.chatra.core.designsystem.glass.cssLinearGradient
import app.chatra.core.designsystem.icon.ChatraIcon
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.model.SetupState
import app.chatra.core.model.SmsCapability
import app.chatra.ui.ChatraScreen
import app.chatra.ui.LocalChrome
import java.util.Locale

/** S01 Start i ograniczenia (CH010). Wzorzec: szablony/S01.html, golden CH002_Android/run1/android-S01.png. */
@Composable
fun StartScreen(model: StartViewModel, onDone: () -> Unit, onPrivacy: () -> Unit, onHelp: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == SetupState.Done && model.isRoleHeld()) onDone() }
    // Po powrocie z ustawień systemu rola mogła zostać nadana poza aplikacją.
    LifecycleResumeEffect(model) {
        model.onResume()
        onPauseOrDispose { }
    }
    StartContent(state, model::onPrimary, onPrivacy, onHelp,
        if (!app.chatra.ui.LocalContainer.current.isDemo) "Wersja testowa. Obsługuje historię oraz wysyłanie SMS/MMS; MMS czeka na odbiór u operatorów. Harmonogram działa lokalnie i wymaga dokładnych alarmów. Chatra HD jest jeszcze niedostępna. Nadanie roli zastąpi obecną aplikację SMS. Używaj tej wersji tylko do testów." else null)
}

private val Polish = Locale.forLanguageTag("pl-PL")
private val Secondary = ChatraColors.TextSecondary

private val LogoGlass = GlassStyle(28.dp, 1.7f, 160f, Color.White.copy(0.8f), Color.White.copy(0.5f), Color.White.copy(0.9f), topHighlight = Color.White.copy(0.95f))
private val LogoShadow = CssShadow(10.dp, 30.dp, Color(27, 35, 74).copy(alpha = 0.14f))
private val CardShadow = CssShadow(10.dp, 30.dp, Color(27, 35, 74).copy(alpha = 0.12f))
private val CtaShadow = CssShadow(10.dp, 24.dp, Color(10, 60, 160).copy(alpha = 0.28f))

private val Works = listOf(
    "SMS i MMS z każdym numerem, także z iPhone'em",
    "Zdjęcia i filmy w oryginale przez Chatra HD",
    "Wyszukiwanie, planowanie i pewny wybór karty SIM",
)
private val Unsupported = listOf(
    "Czatów RCS — np. „pisze…” i potwierdzeń odczytu",
    "iMessage oraz połączeń głosowych i wideo",
)
private const val History =
    "Historia SMS i MMS zostaje na telefonie. Rozmowy RCS z poprzedniej aplikacji mogą się nie przenieść. " +
        "O aparat i zdjęcia zapytamy dopiero, gdy będą potrzebne."

@Composable
internal fun StartContent(state: SetupState, onPrimary: () -> Unit, onPrivacy: () -> Unit, onHelp: () -> Unit, developmentNotice: String? = null) {
    val chrome = LocalChrome.current
    ChatraScreen(Backgrounds.Start, sceneDecoration = { drawStartCircles() }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            // Treść przewija się na małym ekranie i przy dużym foncie; na wzorcu 844 dp mieści się z odstępem (flex: 1).
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                FlexColumn(viewport, chrome.top(60.dp), chrome.bottom(28.dp), 14.dp, Modifier.padding(horizontal = 20.dp)) {
                        // height: 60px + border 1px (content-box) → 62 na zewnątrz; waga kurczenia = 60.
                        Logo(Modifier.layoutId(FlexItem.Shrink(62.dp, weight = 60f)))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // line-height 40 < naturalna wysokość Inter 36 (43,6): CSS centruje glif w linii 40, Compose by jej nie skrócił.
                            Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) {
                                BasicText("Chatra", Modifier.wrapContentHeight(unbounded = true).semantics { heading() }, style = chatraText(36f, 700, 40f, -0.02f))
                            }
                            BasicText(
                                "Spokojne SMS i MMS. Bez reklam w rozmowach i bez zakładania konta.",
                                style = chatraText(17f, 400, 23f, color = Secondary),
                            )
                        }
                        ListCard("Co działa", Works, ChatraIcons.Check, 16f, Color.White, ChatraColors.Accent)
                        ListCard("Czego nie obsługuje", Unsupported, ChatraIcons.Minus, 14f, ChatraColors.ink(0.08f), Secondary)
                        BasicText(History, Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp), style = chatraText(13f, 400, 18f, color = Secondary))
                        Notice(state)
                        if (developmentNotice != null) BasicText(developmentNotice, style = chatraText(14f, 600, 19f, color = ChatraColors.ErrorText))
                        Spacer(Modifier.layoutId(FlexItem.Grow))
                        PrimaryButton(state, onPrimary, Modifier.layoutId(FlexItem.Shrink(54.dp, weight = 54f)))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            LinkButton("Prywatność", onPrivacy)
                            LinkButton("Pomoc i kontakt", onHelp)
                        }
                }
            }
        }
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    GlassSurface(LogoGlass, RoundedCornerShape(19.dp), modifier.width(62.dp), shadows = listOf(LogoShadow), contentAlignment = Alignment.Center) {
        Icon(ChatraIcons.Chat, 30.dp, ChatraColors.Accent, 1.8f)
    }
}

@Composable
private fun ListCard(title: String, items: List<String>, icon: ChatraIcon, iconSize: Float, bulletColor: Color, iconColor: Color) {
    GlassSurface(GlassStyles.Control, RoundedCornerShape(26.dp), Modifier.fillMaxWidth(), shadows = listOf(CardShadow)) {
        // padding 16/18 + border 1px (content-box): treść 312 × …, karta o 2 wyższa niż sama treść z paddingiem.
        Column(Modifier.padding(horizontal = 19.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(title.uppercase(Polish), Modifier.semantics { heading() }, style = chatraText(13f, 600, 16f, 0.05f, Secondary))
            items.forEach { text ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(26.dp).background(bulletColor, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, iconSize.dp, iconColor, 2.4f)
                    }
                    BasicText(text, Modifier.weight(1f), style = chatraText(15f, 400, 20f))
                }
            }
        }
    }
}

/** Stany spoza wzorca (CH020): anulowanie, odmowa, brak telefonii. Na planszy referencyjnej brak komunikatu. */
@Composable
private fun Notice(state: SetupState) {
    val (text, color) = when (state) {
        SetupState.Ready(SetupState.Notice.Cancelled) ->
            "Chatra nie jest jeszcze domyślną aplikacją SMS, więc nie wyśle ani nie odbierze wiadomości. Możesz spróbować ponownie w każdej chwili." to Secondary
        SetupState.Ready(SetupState.Notice.Denied) ->
            "System nie pokazuje już prośby o zmianę aplikacji SMS. Wybierz Chatrę w Ustawieniach: Aplikacje → Aplikacje domyślne → SMS." to ChatraColors.Decision
        SetupState.Blocked(SmsCapability.NoTelephony) ->
            "To urządzenie nie ma modułu telefonii, więc nie może wysyłać ani odbierać SMS i MMS." to ChatraColors.Decision
        is SetupState.Blocked ->
            "System tego urządzenia nie pozwala zmienić domyślnej aplikacji SMS." to ChatraColors.Decision
        else -> return
    }
    BasicText(
        text,
        Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = chatraText(13f, 500, 18f, color = color),
    )
}

@Composable
private fun PrimaryButton(state: SetupState, onClick: () -> Unit, modifier: Modifier) {
    val label = when (state) {
        SetupState.Requesting -> "Czekam na odpowiedź systemu…"
        SetupState.Ready(SetupState.Notice.Denied) -> "Otwórz ustawienia systemu"
        else -> "Ustaw jako domyślną aplikację SMS"
    }
    val enabled = state is SetupState.Ready
    // Sprawdzanie trwa ułamek sekundy — bez przyciemniania, żeby plansza nie migała.
    val dimmed = state is SetupState.Requesting || state is SetupState.Blocked
    val shape = RoundedCornerShape(27.dp)
    Box(
        modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.55f else 1f)
            .cssShadow(shape, CtaShadow)
            .background(ChatraColors.Accent, shape)
            .topHighlight(shape, Color.White.copy(alpha = 0.45f))
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .pressable(onClick, enabled)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = chatraText(17f, 600, color = Color.White).copy(textAlign = TextAlign.Center))
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.pressable(onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = chatraText(15f, color = ChatraColors.Link))
    }
}

/** Rola elementu w [FlexColumn]: `flex-shrink` z bazową wysokością CSS albo `flex: 1`. */
private sealed interface FlexItem {
    /** [base] — wysokość zewnętrzna; [weight] — `flex-shrink` × wewnętrzna baza CSS (bez obramowania). */
    data class Shrink(val base: Dp, val weight: Float) : FlexItem
    data object Grow : FlexItem
}

/**
 * Kolumna jak `display: flex; flex-direction: column; gap` korzenia S01 o wysokości ekranu.
 * Gdy treść się nie mieści, Chrome kurczy elementy o stałej wysokości (logo 60, CTA 54) proporcjonalnie
 * do bazy, najwyżej do ich min-content — golden S01 ma przez to logo ≈47 i CTA ≈41. Nadmiar miejsca
 * trafia do [FlexItem.Grow]. Gdy nawet po skurczeniu się nie mieści, kolumna rośnie i ekran się przewija.
 */
@Composable
private fun FlexColumn(viewport: Dp, top: Dp, bottom: Dp, gap: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val free = Constraints(maxWidth = width)
        val roles = measurables.map { it.layoutId as? FlexItem }
        // Stałe elementy mierzone raz; dla Shrink min-content z intrynsyki, a baza to max(CSS, min-content).
        val fixed = measurables.mapIndexed { i, m -> if (roles[i] == null) m.measure(free) else null }
        val natural = measurables.mapIndexed { i, m ->
            when (roles[i]) {
                null -> fixed[i]!!.height
                is FlexItem.Shrink -> m.minIntrinsicHeight(width)
                FlexItem.Grow -> 0
            }
        }
        val minimum = natural.toMutableList()
        val heights = measurables.indices.map { i ->
            when (val r = roles[i]) {
                is FlexItem.Shrink -> maxOf(r.base.roundToPx(), natural[i])
                FlexItem.Grow -> 0
                null -> natural[i]
            }
        }.toMutableList()
        val gaps = gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)
        val available = viewport.roundToPx() - top.roundToPx() - bottom.roundToPx()
        // Algorytm CSS: kurczenie ważone bazą, z zamrażaniem elementów, które osiągnęły min-content.
        val frozen = BooleanArray(measurables.size) { roles[it] !is FlexItem.Shrink }
        repeat(measurables.size) {
            val overflow = heights.sum() + gaps - available
            val open = measurables.indices.filter { !frozen[it] }
            if (overflow <= 0 || open.isEmpty()) return@repeat
            val weights = open.sumOf { (roles[it] as FlexItem.Shrink).weight.toDouble() }
            open.forEach { i ->
                val target = (heights[i] - overflow * (roles[i] as FlexItem.Shrink).weight / weights).toInt()
                if (target <= minimum[i]) {
                    heights[i] = minimum[i]
                    frozen[i] = true
                } else {
                    heights[i] = target
                }
            }
        }
        val rest = available - heights.sum() - gaps
        roles.indexOf(FlexItem.Grow).takeIf { it >= 0 && rest > 0 }?.let { heights[it] = rest }
        val placeables = measurables.mapIndexed { i, m ->
            fixed[i] ?: m.measure(Constraints(maxWidth = width, minHeight = heights[i], maxHeight = heights[i]))
        }
        val contentHeight = top.roundToPx() + placeables.sumOf { it.height } + gaps + bottom.roundToPx()
        layout(width, maxOf(viewport.roundToPx(), contentHeight)) {
            var y = top.roundToPx()
            placeables.forEach {
                it.place(0, y)
                y += it.height + gap.roundToPx()
            }
        }
    }
}

/** Dwa dekoracyjne koła ze wzorca — część sceny, więc karty je rozmywają. */
private fun DrawScope.drawStartCircles() {
    gradientCircle(Offset(size.width - 160.dp.toPx(), 150.dp.toPx()), 220.dp.toPx(), Color(0xFF5E9BFF), Color(0xFFB18CFF), 0.55f)
    gradientCircle(Offset(-70.dp.toPx(), 470.dp.toPx()), 200.dp.toPx(), Color(0xFFFFB199), Color(0xFFFF8FB8), 0.5f)
}

private fun DrawScope.gradientCircle(topLeft: Offset, diameter: Float, from: Color, to: Color, alpha: Float) {
    translate(topLeft.x, topLeft.y) {
        drawCircle(cssLinearGradient(135f, Size(diameter, diameter), from, to), diameter / 2f, Offset(diameter / 2f, diameter / 2f), alpha)
    }
}
