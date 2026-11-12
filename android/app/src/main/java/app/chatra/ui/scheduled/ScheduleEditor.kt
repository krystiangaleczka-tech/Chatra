package app.chatra.ui.scheduled

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.ui.common.*
import java.time.*

@Composable
fun ScheduleEditor(item: ScheduledItem, cards: List<SimCard>, clock: app.chatra.core.model.Clock, busy: Boolean, error: String?,
    onSave: (String, SubscriptionId, LocalDateTime, LatenessPolicy) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var body by rememberSaveable(item.schedule.id.value) { mutableStateOf(item.operation.body) }
    var date by rememberSaveable(item.schedule.id.value) { mutableStateOf(item.schedule.instant.atZone(clock.zone()).toLocalDate().toString()) }
    var time by rememberSaveable(item.schedule.id.value) { mutableStateOf(item.schedule.instant.atZone(clock.zone()).toLocalTime().toString()) }
    var sim by rememberSaveable(item.schedule.id.value) { mutableStateOf(item.operation.subscriptionId?.value) }
    var policy by rememberSaveable(item.schedule.id.value) { mutableStateOf(item.schedule.latenessPolicy) }
    var chooseSim by remember { mutableStateOf(false) }
    val local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
    val validation = validateSchedule(local, clock.zone(), clock.now())
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("Edytuj zaplanowaną wiadomość", style = chatraText(20f, 700))
            BasicText(item.title, style = ChatraType.Preview)
            BasicTextField(body, { if (!busy && it.length <= 16384) body = it }, Modifier.fillMaxWidth().heightIn(min = 90.dp).semantics { contentDescription = "Treść zaplanowanej wiadomości" }, textStyle = ChatraType.Body)
            TextAction("Data: $date", {
                val current = LocalDate.parse(date)
                DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, current.year, current.monthValue - 1, current.dayOfMonth).show()
            }, enabled = !busy)
            TextAction("Godzina: ${LocalTime.parse(time).withSecond(0).withNano(0)}", {
                val current = LocalTime.parse(time)
                TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString() }, current.hour, current.minute, true).show()
            }, enabled = !busy)
            TextAction("SIM: ${cards.firstOrNull { it.id.value == sim }?.label ?: "Wybierz ponownie"}", { chooseSim = true }, enabled = !busy)
            TextAction(if (policy == LatenessPolicy.Ask) "Po spóźnieniu: zapytaj" else "Po spóźnieniu: wyślij do 1 h", { policy = if (policy == LatenessPolicy.Ask) LatenessPolicy.SendWithinHour else LatenessPolicy.Ask }, enabled = !busy)
            if (validation is ScheduleValidation.Gap) BasicText("Ta godzina nie istnieje. Zapiszemy ${validation.suggestedLocal}.", style = ChatraType.Preview)
            if (validation is ScheduleValidation.Overlap) BasicText("Ta godzina występuje dwukrotnie. Wybrane jest pierwsze wystąpienie.", style = ChatraType.Preview)
            if (validation is ScheduleValidation.Past) BasicText("Wybierz późniejszy termin.", style = ChatraType.Preview)
            error?.let { BasicText(it, style = chatraText(14f, color = ChatraColors.ErrorText)) }
            PrimaryButton("Zapisz zmiany", {
                val stored = if (validation is ScheduleValidation.Gap) validation.suggestedLocal else local
                sim?.let { onSave(body, SubscriptionId(it), stored, policy) }
            }, enabled = !busy && validation !is ScheduleValidation.Past && cards.any { it.available && it.id.value == sim } && (body.isNotBlank() || item.operation.attachmentIds.isNotEmpty()))
            TextAction("Anuluj edycję", onDismiss, enabled = !busy)
        }
    }
    if (chooseSim) SimPicker(cards, sim?.let(::SubscriptionId), { sim = it.value }, { chooseSim = false })
}
