package app.chatra.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    LaunchedEffect(state) { if (state == SetupState.Done) onDone() }
    // Po powrocie z ustawień systemu rola mogła zostać nadana poza aplikacją.
    LifecycleResumeEffect(model) {
        model.onResume()
        onPauseOrDispose { }
    }
    StartContent(state, model::onPrimary, onPrivacy, onHelp)
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
internal fun StartContent(state: SetupState, onPrimary: () -> Unit, onPrivacy: () -> Unit, onHelp: () -> Unit) {
    val chrome = LocalChrome.current
    ChatraScreen(Backgrounds.Start, sceneDecoration = { drawStartCircles() }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            // Treść przewija się na małym ekranie i przy dużym foncie; na wzorcu 844 dp mieści się z odstępem (flex: 1).
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewport)
                        .padding(start = 20.dp, end = 20.dp, top = chrome.top(60.dp), bottom = chrome.bottom(28.dp)),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Logo()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicText("Chatra", Modifier.semantics { heading() }, style = chatraText(36f, 700, 40f, -0.02f))
                            BasicText(
                                "Spokojne SMS i MMS. Bez reklam w rozmowach i bez zakładania konta.",
                                style = chatraText(17f, 400, 23f, color = Secondary),
                            )
                        }
                        ListCard("Co działa", Works, ChatraIcons.Check, 16f, Color.White, ChatraColors.Accent)
                        ListCard("Czego nie obsługuje", Unsupported, ChatraIcons.Minus, 14f, ChatraColors.ink(0.08f), Secondary)
                        BasicText(History, Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp), style = chatraText(13f, 400, 18f, color = Secondary))
                        Notice(state)
                    }
                    // gap 14 + flex: 1 + gap 14
                    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PrimaryButton(state, onPrimary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                            LinkButton("Prywatność", onPrivacy)
                            LinkButton("Pomoc i kontakt", onHelp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Logo() {
    GlassSurface(LogoGlass, RoundedCornerShape(19.dp), Modifier.size(60.dp), shadows = listOf(LogoShadow), contentAlignment = Alignment.Center) {
        Icon(ChatraIcons.Chat, 30.dp, ChatraColors.Accent, 1.8f)
    }
}

@Composable
private fun ListCard(title: String, items: List<String>, icon: ChatraIcon, iconSize: Float, bulletColor: Color, iconColor: Color) {
    GlassSurface(GlassStyles.Control, RoundedCornerShape(26.dp), Modifier.fillMaxWidth(), shadows = listOf(CardShadow)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
private fun PrimaryButton(state: SetupState, onClick: () -> Unit) {
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
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
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
