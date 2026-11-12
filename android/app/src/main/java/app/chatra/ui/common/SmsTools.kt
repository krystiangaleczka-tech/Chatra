package app.chatra.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.platform.AccessFeature
import app.chatra.ui.LocalContainer
import app.chatra.ui.settings.PermissionCard

@Composable
fun SimPicker(cards: List<SimCard>, selected: SubscriptionId?, onChoose: (SubscriptionId) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.surface, RoundedCornerShape(24.dp)).heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Karta SIM", Modifier.semantics { heading() }, style = chatraText(20f, 600))
            PermissionCard(AccessFeature.Sims)
            if (cards.none { it.available }) BasicText("Brak dostępnych kart. Szkic pozostaje zachowany.", style = ChatraType.Preview)
            cards.filter { it.available }.forEach { card ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.RadioButton; this.selected = card.id == selected }.pressable({ onChoose(card.id); onDismiss() }).padding(10.dp)) { BasicText(card.label, style = ChatraType.Body) }
            }
            TextAction("Anuluj", onDismiss)
        }
    }
}

/** Licznik otwiera jawny podgląd transformacji. Edytor nie zmienia tekstu samodzielnie. */
@Composable
fun SmsCounterAction(text: String, sim: SubscriptionId?, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val analyzer = LocalContainer.current.smsText
    val count = analyzer.count(text, sim)
    var opened by remember { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf<String?>(null) }
    val label = count.label + if (count.encoding == SmsCounter.Encoding.Ucs2) " · Unicode" else ""
    BasicText(label, modifier.heightIn(min = 32.dp).semantics { role = Role.Button; contentDescription = "Licznik SMS: $label. Podgląd kodowania i tekstu" }.pressable({ opened = true }).padding(vertical = 6.dp), style = chatraText(12f, color = ChatraColors.TextSecondary))
    if (opened) Dialog(onDismissRequest = { opened = false }) {
        Column(Modifier.background(app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.surface, RoundedCornerShape(24.dp)).heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText("Licznik SMS", Modifier.semantics { heading() }, style = chatraText(20f, 600))
            BasicText("$label. Kodowanie: ${if (count.encoding == SmsCounter.Encoding.Gsm7) "GSM 7-bit" else "Unicode (UTF-16)"}. Emoji może zajmować więcej niż jedną jednostkę. Operator może rozliczyć każdą część osobno.", style = ChatraType.Preview)
            if (!count.preservesText) BasicText("System zmieniłby treść przy podziale. Wysyłanie jest zablokowane; zmień tekst świadomie i sprawdź podgląd.", style = ChatraType.Preview)
            BasicText(text, style = ChatraType.Body)
            val converted = SmsCounter.transliteratePolish(text)
            if (converted != text) {
                BasicText("Podgląd bez polskich znaków: ${analyzer.count(converted, sim).label}", style = ChatraType.Preview)
                BasicText(converted, style = ChatraType.Body)
                TextAction("Użyj wersji bez polskich znaków", { original = text; onText(converted); opened = false })
            }
            original?.let { previous ->
                BasicText("Poprzednia treść: $previous", style = ChatraType.Preview)
                TextAction("Przywróć oryginał", { onText(previous); original = null; opened = false })
            }
            TextAction("Zachowaj aktualną treść", { opened = false })
        }
    }
}
