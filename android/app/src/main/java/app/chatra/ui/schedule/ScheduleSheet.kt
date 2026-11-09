package app.chatra.ui.schedule

import app.chatra.core.designsystem.theme.cssBorder
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.component.CssShadow
import app.chatra.core.designsystem.component.cssShadow
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.model.Clock
import app.chatra.core.model.ExactAlarmGate
import app.chatra.core.model.LatenessPolicy
import app.chatra.core.model.ScheduleShortcut
import app.chatra.core.model.ScheduleValidation
import app.chatra.core.model.SimChoice
import app.chatra.core.model.SubscriptionId
import app.chatra.core.model.scheduleShortcuts
import app.chatra.core.model.validateSchedule
import app.chatra.di.SchedulePreset
import app.chatra.ui.common.PolishDates
import app.chatra.ui.common.PrimaryButton
import app.chatra.ui.common.Radio
import app.chatra.ui.common.SheetHandle
import app.chatra.ui.common.SheetSurface
import app.chatra.ui.conversation.ConversationViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val Secondary = ChatraColors.TextSecondary
private val ValueColor = Color(0xFF0050C8)
private val ValueBackground = Color(10, 108, 255).copy(alpha = 0.1f)
private val SegmentShadow = CssShadow(1.dp, 4.dp, Color(27, 35, 74).copy(alpha = 0.15f))

/**
 * S05 Zaplanuj wysyłkę (CH014). Arkusz nad rozmową: treść i odbiorcy ze szkicu rozmowy, skróty liczone
 * od bieżącej daty, data/godzina, SIM i polityka spóźnienia. Walidacja (termin przeszły, zmiana czasu,
 * brak SIM, brak treści, brak zgody na dokładne alarmy) widoczna przed potwierdzeniem.
 * Pickery daty i godziny to tymczasowo dialogi systemowe — projekty w CH020.
 */
@Composable
fun ScheduleSheet(
    model: ConversationViewModel,
    preset: SchedulePreset?,
    alarms: ExactAlarmGate,
    onBack: () -> Unit,
    onScheduled: () -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val clock = model.clock
    val zone = clock.zone()
    val shortcuts = remember { scheduleShortcuts(clock.now(), zone) }
    var local by rememberSaveable { mutableStateOf(preset?.local ?: shortcuts.first().local) }
    var policy by rememberSaveable { mutableStateOf(LatenessPolicy.Ask) }
    // Zgoda na alarmy może się zmienić w ustawieniach systemu — odczyt przy każdym powrocie na ekran.
    var alarmsAllowed by remember { mutableStateOf(alarms.canScheduleExact()) }
    LifecycleResumeEffect(alarms) {
        alarmsAllowed = alarms.canScheduleExact()
        onPauseOrDispose { }
    }
    val context = LocalContext.current

    val body = preset?.text ?: state.text
    val recipient = state.summary?.title.orEmpty()
    val validation = validateSchedule(local, zone, clock.now())
    val sendLocal = (validation as? ScheduleValidation.Gap)?.suggestedLocal ?: local
    val blocker = blocker(body, state.sim, validation)

    val pickDate = {
        DatePickerDialog(context, { _, y, m, d -> local = LocalDateTime.of(LocalDate.of(y, m + 1, d), local.toLocalTime()) },
            local.year, local.monthValue - 1, local.dayOfMonth).apply {
            datePicker.minDate = clock.now().toEpochMilli() - 1000
        }.show()
    }
    val pickTime = {
        TimePickerDialog(context, { _, h, min -> local = local.with(LocalTime.of(h, min)) }, local.hour, local.minute, true).show()
    }

    SheetSurface(120.dp, "Zaplanuj wysyłkę") {
        SheetHandle()
        Header(onBack)
        // Środek przewija się przy dużym foncie; CTA zostaje przypięte (flex: 1 nad nim we wzorcu).
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RecipientCard(recipient, body)
            Shortcuts(shortcuts, local, onPick = { local = it }, onOther = pickDate)
            Fields(
                date = PolishDates.weekdayDayMonthYear(local.toLocalDate()),
                time = PolishDates.time(local.atZone(zone)),
                state.sims.filter { it.available }.map { it.id to it.label },
                (state.sim as? SimChoice.Ready)?.sim?.id,
                onDate = pickDate,
                onTime = pickTime,
                onSim = model::selectSim,
            )
            Policy(policy) { policy = it }
            Info()
            Notices(validation, blocker, alarmsAllowed, sendLocal)
        }
        val label = when {
            blocker != null -> blocker.cta
            !alarmsAllowed -> "Zezwól na dokładne alarmy"
            else -> "Zaplanuj na ${ctaWhen(sendLocal, clock)}"
        }
        PrimaryButton(label, onClick = {
            when {
                blocker != null -> Unit
                !alarmsAllowed -> alarms.openSettings()
                else -> model.schedule(body, fromComposer = preset == null, validation.instant!!, sendLocal, policy, onScheduled)
            }
        }, enabled = blocker == null)
    }
}

/** Przeszkoda blokująca zapis: komunikat i etykieta wyłączonego CTA. */
private enum class Blocker(val message: String, val cta: String) {
    NoText("W rozmowie nie ma treści do zaplanowania. Napisz wiadomość i wróć tutaj.", "Brak treści"),
    NoSim("Brak aktywnej karty SIM — wiadomości nie da się zaplanować.", "Brak karty SIM"),
    PickSim("Wybierz kartę SIM, z której wyjdzie wiadomość.", "Wybierz kartę SIM"),
    Past("Ten termin już minął. Wybierz późniejszą datę lub godzinę.", "Wybierz przyszły termin"),
}

private fun blocker(body: String, sim: SimChoice, validation: ScheduleValidation): Blocker? = when {
    body.isBlank() -> Blocker.NoText
    sim is SimChoice.NoSim -> Blocker.NoSim
    sim is SimChoice.NeedsConfirmation -> Blocker.PickSim
    validation is ScheduleValidation.Past -> Blocker.Past
    else -> null
}

/** „sob., 10:00” w najbliższym tygodniu, dalej „7 lis, 10:00”. */
internal fun ctaWhen(local: LocalDateTime, clock: Clock): String {
    val today = clock.now().atZone(clock.zone()).toLocalDate()
    val d = local.toLocalDate()
    val time = "%d:%02d".format(local.hour, local.minute)
    return if (ChronoUnit.DAYS.between(today, d) in 0..6) "${PolishDates.weekday(d)}, $time" else "${PolishDates.dayMonth(d, today)}, $time"
}

internal fun shortcutLabel(s: ScheduleShortcut): String {
    val time = "%d:%02d".format(s.local.hour, s.local.minute)
    return when (s.kind) {
        ScheduleShortcut.Kind.Tomorrow -> "Jutro $time"
        ScheduleShortcut.Kind.Saturday -> "Sob. $time"
        ScheduleShortcut.Kind.Monday -> "Pon. $time"
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(ChatraColors.Ink07)
                .semantics { role = Role.Button; contentDescription = "Wróć" }.pressable(onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(ChatraIcons.ChevronLeft, 18.dp, ChatraColors.TextTertiary, 2.4f) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicText("Zaplanuj wysyłkę", Modifier.semantics { heading() }, style = chatraText(17f, 700))
        }
        Spacer(Modifier.width(36.dp))
    }
}

@Composable
private fun RecipientCard(recipient: String, body: String) {
    val shape = RoundedCornerShape(18.dp)
    // padding 12/14 + obramowanie 1 (content-box).
    val b = cssBorder()
    Column(
        Modifier.fillMaxWidth().clip(shape).background(ChatraColors.white(0.6f)).border(b, ChatraColors.white(0.95f), shape)
            .padding(horizontal = 14.dp + b, vertical = 12.dp + b),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("Do: $recipient".uppercase(java.util.Locale.forLanguageTag("pl-PL")), style = chatraText(12f, 600, letterSpacingEm = 0.04f, color = Secondary))
        BasicText(body.ifBlank { "—" }, style = chatraText(15f, 400, 20f), maxLines = 6)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shortcuts(shortcuts: List<ScheduleShortcut>, current: LocalDateTime, onPick: (LocalDateTime) -> Unit, onOther: () -> Unit) {
    val matched = shortcuts.indexOfFirst { it.local == current }
    FlowRow(
        Modifier.fillMaxWidth().semantics { contentDescription = "Szybki wybór terminu" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shortcuts.forEachIndexed { i, s -> Chip(shortcutLabel(s), i == matched) { onPick(s.local) } }
        Chip("Inny…", selected = false, onClick = onOther)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val base = Modifier.height(36.dp).clip(shape)
    val surface = if (selected) base.background(ChatraColors.Accent)
    else base.background(ChatraColors.white(0.6f)).border(1.dp, ChatraColors.white(0.95f), shape)
    Box(
        surface.semantics { role = Role.Button; this.selected = selected }.pressable(onClick)
            // border-box: wybrany bez obramowania, pozostałe 1 + padding 14.
            .padding(horizontal = if (selected) 14.dp else 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = if (selected) chatraText(14f, 600, color = Color.White) else chatraText(14f, 500))
    }
}

@Composable
private fun Fields(
    date: String,
    time: String,
    sims: List<Pair<SubscriptionId, String>>,
    selectedSim: SubscriptionId?,
    onDate: () -> Unit,
    onTime: () -> Unit,
    onSim: (SubscriptionId) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)) {
        FieldRow("Data", divider = true) { ValueButton(date, "Data: $date, zmień", onDate) }
        FieldRow("Godzina", divider = true) { ValueButton(time, "Godzina: $time, zmień", onTime, tabular = true) }
        FieldRow("Wyślij z", divider = false) {
            if (sims.isEmpty()) BasicText("Brak karty SIM", style = chatraText(15f, 500, color = ChatraColors.Decision))
            else SimSegment(sims, selectedSim, onSim)
        }
    }
}

@Composable
private fun FieldRow(label: String, divider: Boolean, value: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, Modifier.padding(end = 12.dp), style = chatraText(16f))
            Spacer(Modifier.weight(1f))
            value()
        }
        // border-bottom 1 w content-box: wiersz 50 + linia 1.
        if (divider) Box(Modifier.fillMaxWidth().height(cssBorder()).background(ChatraColors.Ink07))
    }
}

@Composable
private fun ValueButton(text: String, description: String, onClick: () -> Unit, tabular: Boolean = false) {
    val style = chatraText(16f, 600, color = ValueColor).let { if (tabular) it.copy(fontFeatureSettings = "tnum") else it }
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(ValueBackground)
            .semantics { role = Role.Button; contentDescription = description }.pressable(onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = style) }
}

@Composable
private fun SimSegment(sims: List<Pair<SubscriptionId, String>>, selected: SubscriptionId?, onSim: (SubscriptionId) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(ChatraColors.Ink06).padding(3.dp)
            .semantics { contentDescription = "Karta SIM" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        sims.forEach { (id, label) ->
            val on = id == selected
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier.height(30.dp)
                    .then(if (on) Modifier.cssShadow(shape, SegmentShadow).clip(shape).background(Color.White) else Modifier.clip(shape))
                    .semantics { role = Role.Button; this.selected = on }.pressable({ onSim(id) })
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = if (on) chatraText(13f, 600) else chatraText(13f, 500, color = ChatraColors.TextTertiary), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Policy(policy: LatenessPolicy, onPolicy: (LatenessPolicy) -> Unit) {
    Column {
        BasicText("Gdy termin minie, a telefon był wyłączony", Modifier.padding(bottom = 6.dp).semantics { heading() }, style = chatraText(15f, 600))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PolicyOption("Zapytaj mnie przed wysłaniem", policy == LatenessPolicy.Ask) { onPolicy(LatenessPolicy.Ask) }
            PolicyOption("Wyślij, jeśli spóźnienie nie przekracza 1 h", policy == LatenessPolicy.SendWithinHour) { onPolicy(LatenessPolicy.SendWithinHour) }
        }
    }
}

@Composable
private fun PolicyOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape)
            .background(ChatraColors.white(if (selected) 0.7f else 0.45f))
            .semantics(mergeDescendants = true) { role = Role.RadioButton; this.selected = selected }
            .pressable(onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Radio(selected, width = 20.dp)
        BasicText(label, Modifier.weight(1f), style = chatraText(15f))
    }
}

@Composable
private fun Info() {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(ChatraIcons.Info, 18.dp, Secondary, 2f, Modifier.padding(top = 1.dp))
        BasicText(
            "Wiadomość wyśle ten telefon. Musi być włączony i mieć zasięg sieci komórkowej.",
            Modifier.weight(1f),
            style = chatraText(13f, 400, 18f, color = Secondary),
        )
    }
}

/** Stany spoza wzorca (CH020): blokady zapisu, zmiana czasu i brak zgody na dokładne alarmy. */
@Composable
private fun Notices(validation: ScheduleValidation, blocker: Blocker?, alarmsAllowed: Boolean, sendLocal: LocalDateTime) {
    val texts = buildList {
        blocker?.let { add(it.message) }
        if (validation is ScheduleValidation.Gap) {
            add("Tej godziny nie będzie z powodu zmiany czasu. Wyślemy o ${"%d:%02d".format(sendLocal.hour, sendLocal.minute)}.")
        }
        if (validation is ScheduleValidation.Overlap) {
            add("Ta godzina wystąpi dwa razy (zmiana czasu). Wyślemy przy pierwszej, jeszcze w czasie letnim.")
        }
        if (blocker == null && !alarmsAllowed) {
            add("Bez zgody na dokładne alarmy system może opóźnić wysyłkę. Zezwól w ustawieniach, aby wyszła o czasie.")
        }
    }
    if (texts.isEmpty()) return
    Column(
        Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        texts.forEach { BasicText(it, style = chatraText(13f, 500, 18f, color = ChatraColors.Decision)) }
    }
}
