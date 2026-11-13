package app.chatra.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.model.ProOffer
import app.chatra.core.model.proSummary
import app.chatra.online.*
import app.chatra.ui.common.ChoiceDialog
import app.chatra.ui.common.TextAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProControls(billing: ProBilling?, fallback: ProOffer, demo: Boolean) {
    val activity = LocalActivity.current
    val state = billing?.state?.collectAsStateWithLifecycle()?.value
    BasicText(
        if (state?.entitled == true) "Kompozycje Bursztyn i Las są dostępne w ustawieniach wyglądu." else proSummary(state?.offer ?: fallback, demo),
        style = ChatraType.Body,
    )
    BasicText("SMS/MMS, wyszukiwanie, harmonogram i dostępność są bezpłatne.", style = ChatraType.Preview)
    val message = when (state?.status) {
        ProStatus.Checking -> "Sprawdzamy zakup w Google Play…"
        ProStatus.Active -> "Zakup Pro został potwierdzony."
        ProStatus.Pending -> "Płatność oczekuje na potwierdzenie."
        ProStatus.Cancelled -> "Zakup anulowano."
        ProStatus.Invalid -> "Google Play nie potwierdza ważnego zakupu."
        ProStatus.VerificationFailed -> "Nie udało się zweryfikować zakupu. Spróbuj ponownie po odzyskaniu połączenia."
        ProStatus.Unavailable -> "Google Play lub oferta są niedostępne."
        ProStatus.Available -> null
        null -> if (demo) "W demo zakupy są wyłączone." else "Zakup i przywrócenie są obecnie niedostępne."
    }
    message?.let { BasicText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = ChatraType.Preview) }
    if (billing != null && state != null) {
        val price = (state.offer as? ProOffer.Available)?.formattedPrice
        if (price != null && activity != null && state.status in setOf(ProStatus.Available, ProStatus.Cancelled)) {
            TextAction("Kup jednorazowo · $price", { billing.buy(activity) })
        }
        TextAction("Przywróć zakup", billing::refresh, enabled = state.status != ProStatus.Checking)
    }
}

@Composable
internal fun HdControls(hd: HdPilot) {
    val identity by hd.identity.collectAsStateWithLifecycle()
    val transfers by hd.transfers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reverify by remember { mutableStateOf(false) }
    var confirmDetach by remember { mutableStateOf(false) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Nie udało się wykonać operacji. Sprawdź kod, połączenie lub spróbuj później." }
            finally { busy = false }
        }
    }
    BasicText("Pilot HD przygotowuje pliki na serwerze. Dostarczenie odbiorcy i linki będą dostępne w kolejnych etapach.", style = ChatraType.Body)
    if (identity.installation == null || reverify || identity.challenge != null) {
        BasicText("Po Twojej zgodzie wyślemy numer do usługi weryfikacji SMS Twilio. Odkrywalność możesz włączyć osobno.", style = ChatraType.Preview)
        if (identity.challenge == null || reverify) {
            HdField("Twój numer telefonu", number, { number = it.take(24) }, KeyboardType.Phone)
            TextAction("Wyślij kod weryfikacyjny", { action { hd.requestCode(number); number = ""; reverify = false } }, enabled = !busy && hdPhone(number) != null)
        }
        if (identity.challenge != null) {
            HdField("Kod z SMS", code, { code = it.filter(Char::isDigit).take(10) }, KeyboardType.NumberPassword)
            TextAction("Potwierdź kod", { action { hd.verifyCode(code); code = ""; reverify = false } }, enabled = !busy && code.length >= 4)
            if (!reverify && identity.installation == null) TextAction("Poproś o nowy kod", { reverify = true }, enabled = !busy)
        }
    }
    if (identity.installation != null) {
        BasicText(if (identity.discoverable) "Odkrywalność włączona" else "Odkrywalność wyłączona", style = ChatraType.Preview)
        BasicText("Aby sprawdzić zgodność, obie osoby włączają odkrywalność i wybierają siebie w Chatrze.", style = ChatraType.Preview)
        TextAction(if (identity.discoverable) "Wyłącz odkrywalność" else "Włącz odkrywalność", {
            action { hd.setDiscoverable(!identity.discoverable) }
        }, enabled = !busy)
        TextAction("Zweryfikuj numer ponownie", { reverify = true }, enabled = !busy)
        TextAction("Odłącz instalację…", { confirmDetach = true }, enabled = !busy)
    }
    if (confirmDetach) ChoiceDialog(
        "Odpięcie usuwa wiązanie i transfery tej instalacji z serwera.",
        listOf("Odłącz i usuń dane"),
        { action { hd.detach(); code = ""; number = "" } },
        { confirmDetach = false },
    )
    error?.let { BasicText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = ChatraType.Preview) }
    transfers.asReversed().forEach { row ->
        if (row.status != HdStatus.Cancelled) {
            BasicText("${row.bytes / 1024} z ${row.total / 1024} KiB · ${transferStatus(row)}", style = ChatraType.Preview)
            when (row.status) {
                HdStatus.Queued, HdStatus.Uploading, HdStatus.Waiting ->
                    TextAction("Wstrzymaj", { action { hd.pause(row.id) } }, enabled = !busy)
                HdStatus.Paused, HdStatus.NeedsActivation ->
                    TextAction("Wznów", { action { hd.resume(row.id) } }, enabled = !busy && identity.installation != null)
                else -> Unit
            }
            if (row.status != HdStatus.Cancelling) {
                TextAction(if (row.status == HdStatus.Verified) "Usuń z serwera" else "Anuluj transfer", { action { hd.cancel(row.id) } }, enabled = !busy)
            }
        }
    }
}

private fun transferStatus(row: HdProgress): String = when (row.status) {
    HdStatus.Queued -> "Czeka na sieć"
    HdStatus.Uploading -> "Przesyłanie"
    HdStatus.Waiting -> if (row.detail == "storage_quota" || row.detail == "rate_limited") "Limit serwera · ponowimy później" else "Oczekuje na wznowienie"
    HdStatus.Paused -> "Wstrzymany"
    HdStatus.NeedsActivation -> "Wymaga ponownej aktywacji"
    HdStatus.Failed -> when (row.detail) {
        "file_unavailable" -> "Plik jest niedostępny"
        "file_checksum", "chunk_checksum" -> "Weryfikacja pliku się nie powiodła"
        "transfer_expired" -> "Transfer wygasł"
        "recipient_unavailable" -> "Odbiorca jest niedostępny"
        else -> "Przygotowanie transferu się nie powiodło"
    }
    HdStatus.Cancelling -> "Anulowanie · usuwanie plików z serwera"
    HdStatus.Verified -> "Pliki zweryfikowane na serwerze"
    HdStatus.Cancelled -> "Anulowany"
}

@Composable
private fun HdField(label: String, value: String, change: (String) -> Unit, type: KeyboardType) {
    BasicTextField(
        value, change, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), textStyle = ChatraType.Body,
        visualTransformation = if (type == KeyboardType.NumberPassword) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner -> Column { BasicText(label, style = ChatraType.Preview); inner() } },
    )
}
