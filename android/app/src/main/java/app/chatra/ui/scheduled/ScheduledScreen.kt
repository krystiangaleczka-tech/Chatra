package app.chatra.ui.scheduled

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.GlassStyles
import app.chatra.core.designsystem.component.GlassSurface
import app.chatra.core.designsystem.component.Shadows
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.designsystem.theme.cssBorder
import app.chatra.core.model.Clock
import app.chatra.core.model.ConversationId
import app.chatra.core.model.DecisionReason
import app.chatra.core.model.ExactAlarmGate
import app.chatra.core.model.LastScheduledSent
import app.chatra.core.model.RecipientMode
import app.chatra.core.model.ScheduleId
import app.chatra.core.model.ScheduledItem
import app.chatra.core.model.SimCard
import app.chatra.core.model.SubscriptionId
import app.chatra.core.model.Transport
import app.chatra.core.model.decisionReason
import app.chatra.core.model.simForSendNow
import app.chatra.ui.ChatraScreen
import app.chatra.ui.LocalChrome
import app.chatra.ui.common.LocalClock
import app.chatra.ui.common.MainDock
import app.chatra.ui.common.PolishDates
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val Secondary @Composable get() = ChatraColors.TextSecondary
private val CardShape = RoundedCornerShape(26.dp)

/** S06 Zaplanowane (CH015): decyzje nad listą oczekujących, stopka z ostatnią wysyłką i dock. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScheduledScreen(
    model: ScheduledViewModel,
    alarms: ExactAlarmGate,
    listState: LazyListState,
    onOpen: (ConversationId) -> Unit,
    dock: MainDock,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val chrome = LocalChrome.current
    val clock = LocalClock.current
    var alarmsAllowed by remember { mutableStateOf(alarms.canScheduleExact()) }
    LifecycleResumeEffect(alarms) {
        alarmsAllowed = alarms.canScheduleExact()
        onPauseOrDispose { }
    }
    val native = !app.chatra.ui.LocalContainer.current.isDemo
    var editing by remember { mutableStateOf<ScheduledItem?>(null) }
    var deleting by remember { mutableStateOf<ScheduledItem?>(null) }
    val reschedule = rememberReschedulePicker(clock) { id, local, version -> model.reschedule(id, local, if (native) version else null) }

    ChatraScreen(Backgrounds.Scheduled, floating = { dock.Render(Modifier.align(Alignment.BottomStart)) }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top(52.dp), bottom = chrome.bottom(26.dp) + 64.dp + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("header") { Header() }
            if (!alarmsAllowed && (state.pending.isNotEmpty() || state.decisions.isNotEmpty())) {
                item("alarms") { AlarmNotice(alarms::openSettings) }
            }
            items(state.decisions, key = { "d-${it.schedule.id.value}" }) { item ->
                DecisionCard(
                    item, state.sims, clock,
                    busy = item.schedule.id in state.busy,
                    notice = state.notices[item.schedule.id],
                    onSend = { sim -> if (native && item.decisionReason() == DecisionReason.Uncertain) item.operation.conversationId?.let(onOpen) else model.sendNow(item.schedule.id, sim, if (native) item.schedule.version else null) },
                    onReschedule = { reschedule(item) },
                    onDelete = { if (native) deleting = item else model.delete(item.schedule.id) },
                )
            }
            if (native) items(state.decisions.filter { it.decisionReason() == DecisionReason.Missed }, key = { "edit-${it.schedule.id.value}" }) { item ->
                app.chatra.ui.common.TextAction("Edytuj: ${item.title}", { editing = item })
            }
            if (!state.loading) item("pending") { PendingCard(state.pending, state.sims, clock, onOpen) { item ->
                if (native) Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        app.chatra.ui.common.TextAction("Edytuj", { editing = item })
                        app.chatra.ui.common.TextAction("Wyślij teraz", { model.sendNow(item.schedule.id, item.operation.subscriptionId, item.schedule.version) })
                        app.chatra.ui.common.TextAction("Usuń", { deleting = item })
                    }
                    state.notices[item.schedule.id]?.let { BasicText(it, style = ChatraType.Preview) }
                }
            } }
            state.lastSent?.let { last -> item("last") { LastSentLine(last, clock) } }
        }
    }
    editing?.let { item -> ScheduleEditor(item, state.sims, clock, item.schedule.id in state.busy, state.notices[item.schedule.id],
        { body, sim, local, policy -> model.edit(item, body, sim, local, policy) { editing = null } }, { editing = null }) }
    deleting?.let { item -> app.chatra.ui.common.ChoiceDialog("Usunąć zaplanowaną wiadomość dla ${item.title}? Rozmowa zostanie zachowana.", listOf("Usuń zlecenie"),
        { model.delete(item.schedule.id, item.schedule.version) }, { deleting = null }) }

}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("Zaplanowane", Modifier.semantics { heading() }, style = ChatraType.ListTitle)
        BasicText("Wysyła ten telefon — musi być włączony i w zasięgu.", style = chatraText(14f, 400, 19f, color = Secondary))
    }
}

@Composable
private fun AlarmNotice(onAllow: () -> Unit) {
    val b = cssBorder()
    GlassSurface(GlassStyles.Card, CardShape, Modifier.fillMaxWidth(), shadows = listOf(Shadows.Card)) {
        Column(Modifier.padding(horizontal = 16.dp + b, vertical = (if (app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.compact) 8.dp else 14.dp) + b), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ChatraIcons.Clock, 16.dp, ChatraColors.Decision, 2.2f)
                BasicText("Brak dokładnych alarmów", style = chatraText(15f, 600))
            }
            BasicText(
                "Zlecenia są zapisane. Przyznaj dostęp do dokładnych alarmów, aby telefon mógł je wybudzić o terminie.",
                style = chatraText(13f, 400, 18f, color = Secondary),
            )
            ActionButton("Zezwól w ustawieniach", primary = false, onClick = onAllow, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DecisionCard(
    item: ScheduledItem,
    sims: List<SimCard>,
    clock: Clock,
    busy: Boolean,
    notice: String?,
    onSend: (SubscriptionId?) -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit,
) {
    val reason = item.decisionReason() ?: return
    val b = cssBorder()
    val saved = item.simForSendNow(sims)
    // Utracona SIM: użytkownik wybiera kartę świadomie (bez cichego zastępstwa).
    var chosen by remember(item.schedule.id) { mutableStateOf<SubscriptionId?>(null) }
    val sim = saved?.id ?: chosen
    val (label, color, icon) = when (reason) {
        DecisionReason.Failed -> Triple("Nie wysłano", ChatraColors.ErrorText, ChatraIcons.Alert)
        else -> Triple("Wymaga decyzji", ChatraColors.Decision, ChatraIcons.Alert)
    }
    GlassSurface(
        GlassStyles.Decision, CardShape,
        Modifier.fillMaxWidth().semantics { contentDescription = "$label: ${item.title}" },
        shadows = listOf(Shadows.Decision),
    ) {
        Column(Modifier.padding(horizontal = 16.dp + b, vertical = (if (app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.compact) 8.dp else 14.dp) + b), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, 16.dp, color, 2.2f)
                BasicText(label.uppercase(PL), style = chatraText(13f, 700, letterSpacingEm = 0.04f, color = color))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText(item.title, style = chatraText(16f, 600))
                BasicText(item.operation.body, style = chatraText(15f, 400, 20f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                BasicText(decisionDetail(item, reason, clock), style = chatraText(13f, 400, 18f, color = if (reason == DecisionReason.Failed) ChatraColors.ErrorText else Secondary))
            }
            if (saved == null) SimChoice(sims, chosen) { chosen = it }
            notice?.let { BasicText(it, style = chatraText(13f, 600, 18f, color = ChatraColors.ErrorText)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sendLabel = when (reason) {
                    DecisionReason.Missed -> "Wyślij teraz"
                    DecisionReason.Failed -> "Ponów"
                    DecisionReason.Uncertain -> "Wyślij ponownie"
                }
                ActionButton(if (!app.chatra.ui.LocalContainer.current.isDemo && reason == DecisionReason.Uncertain) "Otwórz rozmowę" else sendLabel, primary = true, enabled = !busy && (sim != null || !app.chatra.ui.LocalContainer.current.isDemo && reason == DecisionReason.Uncertain), onClick = { onSend(sim) }, modifier = Modifier.weight(1f))
                if (reason == DecisionReason.Missed) {
                    ActionButton("Zmień termin", primary = false, enabled = !busy, onClick = onReschedule, modifier = Modifier.weight(1f))
                }
                DeleteButton(enabled = !busy, onClick = onDelete)
            }
        }
    }
}

private val PL = java.util.Locale.forLanguageTag("pl-PL")

private fun decisionDetail(item: ScheduledItem, reason: DecisionReason, clock: Clock): String {
    val at = item.schedule.instant.atZone(clock.zone())
    val whenText = "${PolishDates.weekday(at.toLocalDate())}, ${at.dayOfMonth} ${PolishDates.month(at.toLocalDate())}, ${PolishDates.time(at)}"
    item.registrationError?.let { return it }
    return when (reason) {
        DecisionReason.Missed -> "Miało wyjść $whenText. Telefon był wyłączony — nic nie zostało wysłane."
        DecisionReason.Failed -> "Wysyłka z $whenText nie powiodła się. Sprawdź zasięg i kartę SIM, potem ponów."
        DecisionReason.Uncertain -> "Nie wiadomo, czy wiadomość z $whenText wyszła. Ponowienie może wysłać ją drugi raz."
    }
}

@Composable
private fun SimChoice(sims: List<SimCard>, chosen: SubscriptionId?, onChoose: (SubscriptionId) -> Unit) {
    val available = sims.filter { it.available }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            if (available.isEmpty()) "Brak karty SIM — wysyłka niemożliwa." else "Karta SIM z tego zlecenia jest niedostępna. Wybierz kartę:",
            style = chatraText(13f, 600, 18f, color = ChatraColors.Decision),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            available.forEach { card ->
                val on = card.id == chosen
                val shape = RoundedCornerShape(16.dp)
                Box(
                    Modifier.height(32.dp).clip(shape)
                        .background(if (on) ChatraColors.Accent else ChatraColors.white(0.75f))
                        .semantics { role = Role.RadioButton; selected = on }
                        .pressable({ onChoose(card.id) })
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(card.label, style = chatraText(13f, 600, color = if (on) Color.White else ChatraColors.TextPrimary)) }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(22.dp)
    val b = cssBorder()
    val surface = if (primary) Modifier.background(if (enabled) ChatraColors.Accent else ChatraColors.Accent.copy(alpha = 0.4f))
    else Modifier.background(ChatraColors.white(0.75f)).border(b, ChatraColors.white(0.95f), shape)
    Box(
        modifier.height(44.dp).clip(shape).then(surface)
            .semantics { role = Role.Button }
            .pressable(onClick, enabled),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = chatraText(15f, 600, color = if (primary) Color.White else ChatraColors.TextPrimary), maxLines = 1) }
}

@Composable
private fun DeleteButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(ChatraColors.white(0.75f)).border(cssBorder(), ChatraColors.white(0.95f), CircleShape)
            .semantics { role = Role.Button; contentDescription = "Usuń zaplanowaną wiadomość" }
            .pressable(onClick, enabled),
        contentAlignment = Alignment.Center,
    ) { Icon(ChatraIcons.Trash, 20.dp, ChatraColors.ErrorText, 1.9f) }
}

@Composable
private fun PendingCard(pending: List<ScheduledItem>, sims: List<SimCard>, clock: Clock, onOpen: (ConversationId) -> Unit, actions: @Composable (ScheduledItem) -> Unit = {}) {
    val b = cssBorder()
    GlassSurface(GlassStyles.Card, CardShape, Modifier.fillMaxWidth().semantics { contentDescription = "Czeka na wysłanie" }, shadows = listOf(Shadows.Card)) {
        Column(Modifier.padding(b).clip(RoundedCornerShape(25.dp))) {
            BasicText(
                "Czeka · ${pending.size}".uppercase(PL),
                Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                style = ChatraType.SectionLabel,
            )
            if (pending.isEmpty()) {
                BasicText(
                    "Nic nie czeka na wysłanie. Wiadomość zaplanujesz w rozmowie: Dodatki → Zaplanuj.",
                    Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
                    style = chatraText(14f, 400, 19f, color = Secondary),
                )
            }
            pending.forEachIndexed { i, item ->
                PendingRow(item, sims, clock, divider = i > 0, last = i == pending.lastIndex, onOpen = onOpen)
                actions(item)
            }
        }
    }
}

@Composable
private fun PendingRow(item: ScheduledItem, sims: List<SimCard>, clock: Clock, divider: Boolean, last: Boolean, onOpen: (ConversationId) -> Unit) {
    val at = item.schedule.instant.atZone(clock.zone())
    val day = at.toLocalDate()
    val weekday = PolishDates.weekday(day)
    val time = PolishDates.time(at)
    val sim = sims.firstOrNull { it.id == item.operation.subscriptionId }
    val simMissing = sim == null || !sim.available
    val meta = listOf(PolishDates.dayMonth(day, clock.now().atZone(clock.zone()).toLocalDate()), simLabel(sim), transportLabel(item)).joinToString(" · ")
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(cssBorder()).background(ChatraColors.Ink06))
        Row(
            Modifier.fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "${item.title}, $weekday $time, $meta"
                }
                .pressable({ item.operation.conversationId?.let(onOpen) })
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = if (last) 14.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(52.dp).padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(weekday.uppercase(PL), style = chatraText(12f, 600, color = Secondary))
                // „20:00” jest szersze niż kolumna 52: w CSS wystaje symetrycznie, nie łamie się.
                BasicText(
                    time,
                    Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
                    style = chatraText(20f, 700, 24f).copy(fontFeatureSettings = "tnum"),
                    softWrap = false,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(item.title, style = chatraText(16f, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(item.operation.body, style = chatraText(14f, 400, 19f, color = ChatraColors.TextTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (simMissing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ChatraIcons.Alert, 13.dp, ChatraColors.ErrorText, 2.2f)
                        BasicText("$meta — karta niedostępna", style = chatraText(12f, 600, color = ChatraColors.ErrorText))
                    }
                } else {
                    BasicText(meta, style = chatraText(12f, color = Secondary))
                }
            }
        }
    }
}

/** „SIM 1” dla karty podstawowej, „SIM 2 Praca” dla pozostałych (wzorzec S06). */
private fun simLabel(sim: SimCard?): String = when {
    sim == null -> "brak SIM"
    sim.slotIndex == 0 -> sim.shortLabel
    else -> "${sim.shortLabel} ${sim.displayName}"
}

private fun transportLabel(item: ScheduledItem): String {
    val op = item.operation
    return when {
        op.recipientMode == RecipientMode.GroupMms -> "grupowy MMS, ${people(op.recipients.size)}"
        op.recipients.size > 1 -> "${op.recipients.size} osobne SMS"
        op.transport == Transport.MMS -> "MMS"
        op.transport == Transport.HD -> "HD"
        else -> "SMS"
    }
}

private fun people(n: Int): String {
    val few = n % 10 in 2..4 && n % 100 !in 12..14
    return "$n " + when {
        n == 1 -> "osoba"
        few -> "osoby"
        else -> "osób"
    }
}

@Composable
private fun LastSentLine(last: LastScheduledSent, clock: Clock) {
    val at = last.at.atZone(clock.zone())
    val today = clock.now().atZone(clock.zone()).toLocalDate()
    val day = at.toLocalDate()
    val whenText = if (Duration.between(day.atStartOfDay(), today.atStartOfDay()).toDays() in 0..6) {
        "${PolishDates.weekday(day)} ${PolishDates.time(at)}"
    } else {
        "${PolishDates.dayMonth(day, today)}, ${PolishDates.time(at)}"
    }
    BasicText(
        "Wysłane ostatnio: ${last.title}, $whenText — ${if (last.delivered) "doręczono" else "wysłano"}.",
        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        style = chatraText(13f, color = Secondary),
    )
}

/**
 * Zmiana terminu: tymczasowo systemowe dialogi daty i godziny (projekty pickerów w CH020),
 * walidacja i zapis w ViewModelu.
 */
@Composable
private fun rememberReschedulePicker(clock: Clock, onPicked: (ScheduleId, LocalDateTime, Int) -> Unit): (ScheduledItem) -> Unit {
    val context = LocalContext.current
    return remember(context, clock) {
        { item: ScheduledItem ->
            val now = clock.now().atZone(clock.zone()).toLocalDateTime()
            val start = maxOf(item.schedule.localDateTime, now.plusHours(1).withMinute(0))
            DatePickerDialog(context, { _, y, m, d ->
                val date = LocalDate.of(y, m + 1, d)
                TimePickerDialog(context, { _, h, min -> onPicked(item.schedule.id, LocalDateTime.of(date, LocalTime.of(h, min)), item.schedule.version) },
                    start.hour, start.minute, true).show()
            }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
                datePicker.minDate = clock.now().toEpochMilli() - 1000
            }.show()
        }
    }
}
