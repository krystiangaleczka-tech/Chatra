package app.chatra.ui.newmessage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.*
import app.chatra.core.designsystem.icon.*
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.ui.*
import app.chatra.ui.common.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(model: NewMessageViewModel, onCancel: () -> Unit, onSent: (ConversationId) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val chrome = LocalChrome.current
    val keyboard = LocalSoftwareKeyboardController.current
    var simPicker by remember { mutableStateOf(false) }
    ChatraScreen(Backgrounds.NewMessage, floating = {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = chrome.bottom(26.dp)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassSurface(GlassStyles.SimChip, RoundedCornerShape(16.dp), Modifier.height(34.dp).semantics { role = Role.Button; contentDescription = "Wybierz kartę SIM" }.pressable({ simPicker = true })) {
                    Row(Modifier.fillMaxHeight().padding(start = 8.dp, end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ChatraIcons.Sim, 16.dp, ChatraColors.TextPrimary)
                        BasicText((state.sim as? SimChoice.Ready)?.sim?.label ?: if (state.sim is SimChoice.NoSim) "Brak karty SIM" else "Wybierz SIM", style = chatraText(13f, 600))
                        Icon(ChatraIcons.ChevronDown, 14.dp, ChatraColors.TextPrimary)
                    }
                }
                BasicText(state.operationLabel, style = chatraText(12f, color = ChatraColors.TextTertiary))
            }
            GlassSurface(GlassStyles.Input.copy(from = ChatraColors.white(.88f), to = ChatraColors.white(.62f)), RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Control)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(state.text, model::text, Modifier.weight(1f).heightIn(max = 120.dp).semantics { contentDescription = "Treść wiadomości" }, textStyle = chatraText(16f), maxLines = 5, decorationBox = { field -> Box { if (state.text.isEmpty()) BasicText("Wiadomość SMS", style = chatraText(16f, color = ChatraColors.TextSecondary)); field() } })
                    Box(Modifier.size(40.dp).clip(CircleShape).background(if (state.canSend) ChatraColors.Accent else ChatraColors.ink(.12f)).semantics { role = Role.Button; contentDescription = "Wyślij"; if (!state.canSend) disabled() }.pressable({ keyboard?.hide(); model.send(onSent) }, state.canSend), contentAlignment = Alignment.Center) { Icon(ChatraIcons.ArrowUp, 20.dp, if (state.canSend) Color.White else ChatraColors.TextSecondary, 2.4f) }
                }
            }
        }
    }) {
        Column(Modifier.fillMaxSize().padding(bottom = chrome.bottom(26.dp) + 120.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = chrome.top(20.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SheetHandle()
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextAction("Anuluj", { keyboard?.hide(); onCancel() }, Modifier.width(70.dp))
                BasicText("Nowa wiadomość", Modifier.semantics { heading() }, style = chatraText(17f, 700))
                Spacer(Modifier.width(70.dp))
            }
            GlassSurface(GlassStyles.Input.copy(from = ChatraColors.white(.88f), to = ChatraColors.white(.62f)), RoundedCornerShape(22.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Search)) {
                FlowRow(Modifier.padding(start = 14.dp + cssBorder(), end = 10.dp + cssBorder(), top = 8.dp + cssBorder(), bottom = 8.dp + cssBorder()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    BasicText("Do:", Modifier.padding(end = 2.dp), style = chatraText(16f, color = ChatraColors.TextSecondary))
                    state.recipients.forEachIndexed { i, p ->
                        Row(Modifier.widthIn(max = 300.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(ChatraColors.Accent.copy(alpha = .12f)).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(p.label, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = chatraText(15f, 600, color = Color(0xFF0050C8)))
                            Box(Modifier.size(24.dp).semantics { role = Role.Button; contentDescription = "Usuń: ${p.label}" }.pressable({ model.remove(i) }), contentAlignment = Alignment.Center) { Icon(ChatraIcons.Close, 12.dp, Color(0xFF0050C8), 2.4f) }
                        }
                    }
                    BasicTextField(state.input, model::input, Modifier.widthIn(min = 100.dp).weight(1f).heightIn(min = 30.dp).semantics { contentDescription = "Dodaj odbiorcę" }, singleLine = true, textStyle = chatraText(16f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { model.addInput() }), decorationBox = { field -> Box(contentAlignment = Alignment.CenterStart) { if (state.input.isEmpty()) BasicText("Imię lub numer", style = chatraText(16f, color = ChatraColors.TextSecondary)); field() } })
                }
            }
            if (state.input.isNotBlank()) TextAction("Dodaj numer", model::addInput)
            state.error?.let { BasicText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = chatraText(14f, color = ChatraColors.ErrorText)) }
            if (state.recipients.size > 1) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText("Jak wysłać do ${state.recipients.size} osób?", Modifier.padding(start = 4.dp), style = chatraText(15f, 600))
                        SendOption(state.mode != RecipientMode.GroupMms, "Wyślij osobno do każdego", "Każdy dostanie własny SMS. Odpowiedzi wrócą do osobnych rozmów. Nikt nie zobaczy numerów pozostałych.") { model.mode(RecipientMode.SeparateSms) }
                        SendOption(state.mode == RecipientMode.GroupMms, "Rozmowa grupowa MMS", "Wszyscy zobaczą odpowiedzi i swoje numery telefonów. Działanie zależy od operatorów i telefonów uczestników.") { model.mode(RecipientMode.GroupMms) }
                    }
            }
            if (state.suggestions.isNotEmpty()) GlassSurface(GlassStyles.Card, RoundedCornerShape(22.dp), Modifier.fillMaxWidth()) {
                Column(Modifier.padding(cssBorder())) {
                    SectionLabel("Sugerowane", Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
                    state.suggestions.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { role = Role.Button }.pressable({ model.add(p) }).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (i == state.suggestions.lastIndex) 12.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(AvatarContent.Initials(p.initials()), if (i == 0) AvatarTones.Lilac else AvatarTones.Slate, 40.dp, chatraText(15f, 600))
                            Column(Modifier.weight(1f)) {
                                BasicText(p.label, style = chatraText(16f, 500))
                                BasicText(if (p.label == "Marek (praca)" && LocalContainer.current.isDemo) "służbowy · zwykle z SIM 2" else "komórka · ${p.address.raw}", style = chatraText(13f, color = ChatraColors.TextSecondary))
                            }
                            Icon(ChatraIcons.PlusCircle, 20.dp, ChatraColors.Accent)
                        }
                    }
                }
            }
        }
    }
    if (simPicker) ChoiceDialog("Karta SIM", state.sims.filter { it.available }.map { it.label }, { model.sim(state.sims.filter { it.available }[it].id) }, { simPicker = false })
}

@Composable
private fun SendOption(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    RadioOption(selected, onClick, radius = 20.dp, verticalPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(title, style = chatraText(16f, 600))
            BasicText(detail, style = chatraText(13f, lineHeight = 18f, color = ChatraColors.TextSecondary))
        }
    }
}
