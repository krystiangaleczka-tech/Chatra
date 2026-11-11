package app.chatra.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
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
fun SearchScreen(model: SearchViewModel, onCancel: () -> Unit, onResult: (Message) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val sender by model.sender.collectAsStateWithLifecycle()
    val since by model.since.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val conversations by container.conversations.inbox().collectAsStateWithLifecycle(emptyList())
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val chrome = LocalChrome.current
    var picker by remember { mutableStateOf<String?>(null) }
    ChatraScreen(Backgrounds.Search) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top(54.dp), bottom = chrome.bottom(26.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassSurface(GlassStyles.Input.copy(from = Color.White.copy(alpha = .9f), to = Color.White.copy(alpha = .66f)), RoundedCornerShape(22.dp), Modifier.weight(1f).height(46.dp), shadows = listOf(Shadows.Control)) {
                        Row(Modifier.fillMaxSize().padding(start = 14.dp + cssBorder(), end = 6.dp + cssBorder()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(ChatraIcons.Search, 20.dp, ChatraColors.TextSecondary)
                            BasicTextField(query, model::text, Modifier.weight(1f).semantics { contentDescription = "Szukaj w rozmowach" }, singleLine = true, textStyle = chatraText(17f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), decorationBox = { field ->
                                Box { if (query.isEmpty()) BasicText("Szukaj w rozmowach", style = chatraText(17f, color = ChatraColors.TextSecondary)); field() }
                            })
                            Box(Modifier.size(32.dp).clip(CircleShape).background(ChatraColors.ink(.1f)).semantics { contentDescription = "Wyczyść"; role = Role.Button }.pressable({ model.text("") }), contentAlignment = Alignment.Center) { Icon(ChatraIcons.Close, 16.dp, ChatraColors.TextTertiary) }
                        }
                    }
                    TextAction("Anuluj", onCancel)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("Kody", filter == SearchFilter.Codes.name) { model.filter(SearchFilter.Codes) }
                    FilterChip("Przesyłki", filter == SearchFilter.Parcels.name) { model.filter(SearchFilter.Parcels) }
                    FilterChip("Załączniki", filter == SearchFilter.Attachments.name) { model.filter(SearchFilter.Attachments) }
                    FilterChip("Od: ${conversations.firstOrNull { it.id.value == sender }?.title ?: "wszyscy"}", false, dropdown = true) { picker = "sender" }
                    FilterChip("Kiedy: ${if (since == null) "zawsze" else "ostatni tydzień"}", false, dropdown = true) { picker = "date" }
                }
            }
            item { BasicText("„paczk” znajdzie też „paczkę” i „PACZKA” — polskie znaki i wielkość liter nie mają znaczenia. Szukamy tylko na tym telefonie.", Modifier.padding(horizontal = 4.dp), style = chatraText(13f, lineHeight = 18f, color = ChatraColors.TextSecondary)) }
            if (state.loading || state.error || state.results.indexing || state.results.hits.isEmpty()) item {
                GlassSurface(GlassStyles.Card, RoundedCornerShape(24.dp), Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        BasicText(when { state.error -> "Nie udało się odczytać wiadomości."; state.loading -> "Wyszukiwanie…"; state.results.indexing -> "Indeksowanie wiadomości…"; query.isBlank() -> "Wpisz tekst, aby wyszukać wiadomości."; else -> "Brak wyników. Zmień zapytanie lub filtry." }, style = chatraText(15f))
                        if (state.error) TextAction("Spróbuj ponownie", model::retry)
                    }
                }
            }
            if (state.results.quick.isNotEmpty()) item {
                GlassSurface(GlassStyles.Card, RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Card)) {
                    Column(Modifier.padding(6.dp + cssBorder()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.results.quick.forEachIndexed { i, hit ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).then(if (i == 0) Modifier.background(Color.White) else Modifier).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    BasicText(hit.label.uppercase(), style = chatraText(12f, 600, letterSpacingEm = .04f, color = ChatraColors.TextSecondary))
                                    BasicText(hit.value, style = if (i == 0) chatraText(24f, 700, letterSpacingEm = .06f) else chatraText(16f, 600))
                                }
                                Row(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(if (i == 0) ChatraColors.Accent else ChatraColors.white(.8f)).semantics { role = Role.Button; contentDescription = "Kopiuj: ${hit.label}" }.pressable({
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(hit.label, hit.value))
                                }).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (i == 0) Icon(ChatraIcons.Copy, 16.dp, Color.White)
                                    BasicText("Kopiuj", style = chatraText(15f, 600, color = if (i == 0) Color.White else ChatraColors.TextPrimary))
                                }
                            }
                        }
                    }
                }
            }
            if (state.results.hits.isNotEmpty()) item {
                GlassSurface(GlassStyles.Card, RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Card)) {
                    Column(Modifier.padding(cssBorder())) {
                        SectionLabel("Wiadomości · ${state.results.hits.size}", Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp))
                        state.results.hits.forEachIndexed { i, hit ->
                            Column(Modifier.fillMaxWidth().then(if (i > 0) Modifier.drawTopLine(ChatraColors.Ink06) else Modifier).semantics(mergeDescendants = true) { role = Role.Button }.pressable({ keyboard?.hide(); onResult(hit.message) }).padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = if (i == state.results.hits.lastIndex) 13.dp else 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BasicText(hit.title, Modifier.weight(1f), style = chatraText(15f, 600))
                                    BasicText(formatRelativeDay(hit.message.timestamp, container.clock), style = chatraText(13f, color = ChatraColors.TextSecondary))
                                }
                                BasicText(buildAnnotatedString { append(hit.message.body); searchRanges(hit.message.body, query).forEach { addStyle(SpanStyle(background = ChatraColors.Accent.copy(alpha = .16f), fontWeight = FontWeight.SemiBold), it.first, it.last + 1) } }, style = chatraText(14f, lineHeight = 19f, color = ChatraColors.TextTertiary))
                            }
                        }
                    }
                }
            }
        }
    }
    when (picker) {
        "sender" -> ChoiceDialog("Nadawca", listOf("Wszyscy") + conversations.map { it.title }, { model.sender(if (it == 0) null else conversations[it - 1].id) }, { picker = null })
        "date" -> ChoiceDialog("Kiedy", listOf("Zawsze", "Ostatni tydzień"), { model.since(if (it == 0) null else container.clock.now().minusSeconds(7 * 86400)) }, { picker = null })
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, dropdown: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(if (selected) ChatraColors.TextPrimary else ChatraColors.white(.6f)).then(if (!selected) Modifier.border(cssBorder(), ChatraColors.white(.95f), RoundedCornerShape(17.dp)) else Modifier).semantics { role = Role.Checkbox; this.selected = selected }.pressable(onClick).padding(start = if (selected) 8.dp else 12.dp, end = if (dropdown) 10.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(if (dropdown) 4.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
        val color = if (selected) Color.White else ChatraColors.TextPrimary
        if (selected) Icon(ChatraIcons.Check, 16.dp, color)
        BasicText(label, style = chatraText(14f, if (selected) 600 else 400, color = color))
        if (dropdown) Icon(ChatraIcons.ChevronDown, 14.dp, color)
    }
}
