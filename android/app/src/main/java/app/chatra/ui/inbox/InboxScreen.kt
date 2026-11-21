package app.chatra.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import app.chatra.core.designsystem.glass.GlassStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.Avatar
import app.chatra.core.designsystem.component.GlassCircleButton
import app.chatra.core.designsystem.component.GlassStyles
import app.chatra.core.designsystem.component.GlassSurface
import app.chatra.core.designsystem.component.Shadows
import app.chatra.core.designsystem.component.SmallTag
import app.chatra.core.designsystem.component.UnreadBadge
import app.chatra.core.designsystem.component.cssShadow
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.model.ConversationId
import app.chatra.core.model.ConversationSummary
import app.chatra.core.model.ConversationSummary.Preview
import app.chatra.ui.ChatraScreen
import app.chatra.ui.LocalChrome
import app.chatra.ui.common.MainDock
import app.chatra.ui.common.avatarFor
import app.chatra.ui.common.drawTopLine
import app.chatra.ui.common.formatListTime

/** S02 Skrzynka. */
@Composable
fun InboxScreen(
    model: InboxViewModel,
    listState: LazyListState,
    onOpen: (ConversationId) -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    dock: MainDock,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val chrome = LocalChrome.current
    val container = app.chatra.ui.LocalContainer.current
    // Elementy listy przed kartą rozmów: nagłówek, [wersja testowa], wyszukiwarka, filtr.
    val cardStart = if (container.isDemo) 3 else 4
    ChatraScreen(
        Backgrounds.Inbox,
        floating = { dock.Render(Modifier.align(Alignment.BottomStart)) },
    ) {
        // Własny Box: zIndex szkła porządkuje je tylko względem listy, nie względem tła ekranu.
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top(52.dp), bottom = chrome.bottom(26.dp) + 64.dp + 16.dp),
            ) {
                item("header") { Box(Modifier.padding(bottom = SectionGap)) { Header(onMore) } }
                if (!container.isDemo) item("development") {
                    Column(Modifier.padding(bottom = SectionGap)) {
                        BasicText("Wersja testowa: SMS i MMS. Obsługa MMS czeka na sprawdzenie u operatorów. Harmonogram działa lokalnie i wymaga dokładnych alarmów. Chatra HD jest jeszcze niedostępna.", style = chatraText(14f, 600, color = ChatraColors.ErrorText))
                        app.chatra.ui.settings.PermissionPrompt(app.chatra.platform.AccessFeature.Sms)
                        if (state.history is app.chatra.core.model.HistoryStatus.Error) {
                            BasicText((state.history as app.chatra.core.model.HistoryStatus.Error).message, style = ChatraType.Preview)
                            app.chatra.ui.common.TextAction("Odśwież historię", model::refresh)
                        }
                    }
                }
                item("search") { Box(Modifier.padding(bottom = SectionGap)) { SearchField(onSearch) } }
                item("filter") { Box(Modifier.padding(bottom = SectionGap)) { Filter(state.filter, state.unreadConversations, model::setFilter) } }
                conversationCard(state, onOpen)
            }
            // Mierzone po liście (czyta jej świeży układ w tym samym przebiegu), rysowane pod nią.
            ConversationCardGlass(listState, cardStart, Modifier.zIndex(-1f))
        }
    }
}

private val SectionGap = 12.dp
private val CardShape = RoundedCornerShape(28.dp)

/** Zapas tła karty poza ekranem: większy niż promień narożnika, więc ucięta krawędź nigdy nie jest widoczna. */
private val CardOverscan = 64.dp
private val CardInset = 16.dp

/**
 * Wiersze karty jako osobne elementy listy: komponowane i mierzone są tylko widoczne rozmowy,
 * a nie wszystkie naraz (setki wierszy w jednym elemencie dawały klatki po kilka sekund).
 */
private fun LazyListScope.conversationCard(state: InboxState, onOpen: (ConversationId) -> Unit) {
    when {
        state.loading -> item("card-status", contentType = "status") { EmptyLine("Wczytujemy rozmowy z telefonu…") }
        state.pinned.isEmpty() && state.others.isEmpty() -> item("card-status", contentType = "status") {
            EmptyLine(if (state.filter == InboxFilter.Unread) "Wszystko przeczytane." else "Nie masz jeszcze rozmów.")
        }
        else -> {
            if (state.pinned.isNotEmpty()) {
                item("label-pinned", contentType = "label") { GroupLabel("Przypięte", divider = false) }
                items(state.pinned, key = { "c:" + it.id.value }, contentType = { "row" }) { ConversationRow(it, onOpen) }
            }
            if (state.others.isNotEmpty()) {
                item("label-others", contentType = "label") { GroupLabel("Pozostałe", divider = state.pinned.isNotEmpty()) }
                items(state.others, key = { "c:" + it.id.value }, contentType = { "row" }) { ConversationRow(it, onOpen) }
            }
        }
    }
}

/** Położenie karty w układzie listy (px, względem góry kontenera); karta to elementy od [cardStart] do końca. */
private class CardGeometry(
    /** Rysowany fragment tła karty: jej widoczne brzegi, a poza nimi zapas za krawędzią ekranu. */
    val top: Int, val bottom: Int,
    /** Cała karta: dokładnie, gdy jej brzeg jest widoczny, inaczej szacunek ze średniej wysokości wiersza. */
    val cardTop: Float, val cardHeight: Float,
)

private fun cardGeometry(info: LazyListLayoutInfo, cardStart: Int, overscan: Int): CardGeometry? {
    val visible = info.visibleItemsInfo.filter { it.index >= cardStart }
    if (visible.isEmpty()) return null
    val first = visible.first()
    val last = visible.last()
    val lastIndex = info.totalItemsCount - 1
    val origin = info.beforeContentPadding
    val average = visible.sumOf { it.size }.toFloat() / visible.size
    val cardTop = origin + first.offset - (first.index - cardStart) * average
    val cardBottom = origin + last.offset + last.size + (lastIndex - last.index) * average
    val exactTop = origin + first.offset
    val exactBottom = origin + last.offset + last.size
    // Gdy jeden brzeg karty jest poza ekranem, fragment ma stałą wysokość (ekran + zapas z obu stron) i tylko
    // się przesuwa. Zmiana wysokości w każdej klatce przewijania przy górze listy wymuszała ponowne
    // wygenerowanie dużego rozmytego cienia karty — na A15 ≈ 16 % spóźnionych klatek.
    val span = info.viewportSize.height + 2 * overscan
    val topVisible = first.index == cardStart
    val bottomVisible = last.index == lastIndex
    val top: Int
    val bottom: Int
    when {
        topVisible && bottomVisible -> { top = exactTop; bottom = exactBottom }
        topVisible -> { top = exactTop; bottom = exactTop + span }
        bottomVisible -> { top = exactBottom - span; bottom = exactBottom }
        else -> { top = -overscan; bottom = info.viewportSize.height + overscan }
    }
    if (bottom <= top) return null
    return CardGeometry(top, bottom, cardTop, (cardBottom - cardTop).coerceAtLeast((bottom - top).toFloat()))
}

/**
 * Szkło karty rozmów rysowane raz, tylko pod widocznym fragmentem karty — rozmycie i cień
 * nie obejmują już całej wysokości wszystkich rozmów. Gradient powierzchni jest przycięty do
 * tego fragmentu, więc długa karta wygląda tak samo jak wcześniej jedna wysoka powierzchnia.
 *
 * Całość (cień, rozmycie, gradient, refleks, obramowanie) leży w jednej warstwie wielkości listy,
 * którą HWUI trzyma jako gotową teksturę. Przy przewijaniu środka listy karta stoi w miejscu,
 * więc w każdej klatce GPU tylko ją kopiuje zamiast malować pięć przebiegów od nowa (na A15 kilka ms
 * na klatkę). Warstwa obejmuje cały obszar listy, więc cień karty nie jest przycinany.
 */
@Composable
private fun ConversationCardGlass(list: LazyListState, cardStart: Int, modifier: Modifier) {
    val density = LocalDensity.current
    val overscan = with(density) { CardOverscan.roundToPx() }
    val inset = with(density) { CardInset.roundToPx() }
    val base = GlassStyles.Card
    val style by remember(list, cardStart, overscan, inset, base) {
        derivedStateOf {
            val info = list.layoutInfo
            val geometry = cardGeometry(info, cardStart, overscan) ?: return@derivedStateOf base
            val width = (info.viewportSize.width - 2 * inset).toFloat()
            val (from, to) = cardGradient(base, width, geometry)
            base.copy(from = from, to = to)
        }
    }
    GlassSurface(
        style, CardShape,
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val geometry = cardGeometry(list.layoutInfo, cardStart, overscan)
                ?: return@layout layout(width, height) {}
            val placeable = measurable.measure(Constraints.fixed((width - 2 * inset).coerceAtLeast(0), geometry.bottom - geometry.top))
            layout(width, height) { placeable.place(inset, geometry.top) }
        },
        shadows = listOf(Shadows.Card),
    )
}

/**
 * Kolory gradientu CSS (kąt stylu) całej karty w punktach, w których wypada gradient widocznego
 * fragmentu. Kwantyzowane do 1/255, żeby przewijanie nie przebudowywało stylu w każdej klatce.
 */
private fun cardGradient(style: GlassStyle, width: Float, g: CardGeometry): Pair<Color, Color> {
    // Jak cssLinearGradient: 0° w górę, zgodnie z ruchem wskazówek; półdługość z rzutu prostokąta.
    val a = style.angleDegrees * PI.toFloat() / 180f
    val dx = sin(a)
    val dy = -cos(a)
    val partHeight = (g.bottom - g.top).toFloat()
    val halfCard = (abs(width * dx) + abs(g.cardHeight * dy)) / 2f
    val halfPart = (abs(width * dx) + abs(partHeight * dy)) / 2f
    if (halfCard <= 0f) return style.from to style.to
    // Rzut środka fragmentu na oś gradientu karty, liczony od jej środka (te same krawędzie poziome).
    val center = (g.top + partHeight / 2f - (g.cardTop + g.cardHeight / 2f)) * dy
    fun at(t: Float): Color {
        val s = (t - .5f) * 2f * halfPart + center
        val fraction = (s / (2f * halfCard) + .5f).coerceIn(0f, 1f)
        return quantize(lerp(style.from, style.to, fraction))
    }
    return at(0f) to at(1f)
}

private fun quantize(c: Color): Color = Color(
    (c.red * 255f).roundToInt(), (c.green * 255f).roundToInt(), (c.blue * 255f).roundToInt(), (c.alpha * 255f).roundToInt(),
)

@Composable
private fun Header(onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText("Rozmowy", Modifier.weight(1f).semantics { heading() }, style = ChatraType.ListTitle)
        GlassCircleButton(ChatraIcons.More, "Więcej opcji", onMore)
    }
}

@Composable
private fun SearchField(onSearch: () -> Unit) {
    GlassSurface(
        GlassStyles.Control, RoundedCornerShape(23.dp),
        Modifier.fillMaxWidth().height(46.dp).semantics { role = Role.Button; contentDescription = "Szukaj kontaktu, treści lub kodu" }.pressable(onSearch),
        shadows = listOf(Shadows.Search),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 15.dp, end = 15.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ChatraIcons.Search, 19.dp, ChatraColors.TextSecondary, 2f)
            BasicText("Szukaj kontaktu, treści lub kodu", style = chatraText(17f, color = ChatraColors.TextSecondary), maxLines = 1)
        }
    }
}

@Composable
private fun Filter(filter: InboxFilter, unread: Int, onSelect: (InboxFilter) -> Unit) {
    GlassSurface(
        GlassStyles.Segment, RoundedCornerShape(20.dp),
        Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = "Filtr rozmów" },
    ) {
        Row(Modifier.fillMaxSize().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SegmentButton("Wszystkie", filter == InboxFilter.All) { onSelect(InboxFilter.All) }
            SegmentButton("Nieprzeczytane · $unread", filter == InboxFilter.Unread) { onSelect(InboxFilter.Unread) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SegmentButton(text: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(if (active) Modifier.cssShadow(shape, Shadows.SegmentThumb).clip(shape).background(app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.surface) else Modifier)
            .semantics { role = Role.Tab; selected = active }
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = chatraText(15f, if (active) 600 else 500, color = if (active) ChatraColors.TextPrimary else ChatraColors.TextTertiary))
    }
}

@Composable
private fun GroupLabel(text: String, divider: Boolean) {
    Box(
        Modifier.fillMaxWidth()
            .then(if (divider) Modifier.drawTopLine(ChatraColors.Ink07) else Modifier)
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
    ) { BasicText(text.uppercase(), style = ChatraType.SectionLabel) }
}

@Composable
private fun EmptyLine(text: String) {
    BasicText(text, Modifier.padding(18.dp), style = ChatraType.Preview)
}

@Composable
private fun ConversationRow(row: ConversationSummary, onOpen: (ConversationId) -> Unit) {
    val unread = row.isUnread
    val container = app.chatra.ui.LocalContainer.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .combinedClickable(interactionSource = null, indication = null, onClick = { onOpen(row.id) }, onLongClickLabel = "Akcje rozmowy", onLongClick = { menu = true })
            .padding(horizontal = 16.dp, vertical = if (app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.compact) 4.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (content, tone) = avatarFor(row)
        Avatar(content, tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(row.title, Modifier.weight(1f, fill = false), style = if (unread) ChatraType.ContactNameUnread else ChatraType.ContactName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.pinned) Box(Modifier.semantics { contentDescription = "Przypięta" }) { Icon(ChatraIcons.Pin, 13.dp, ChatraColors.TextSecondary, 2f) }
                if (ConversationSummary.Tag.Code in row.tags) SmallTag("Kod")
                Spacer(Modifier.weight(1f))
                BasicText(
                    formatListTime(row.lastActivity),
                    style = if (unread) chatraText(13f, 600) else chatraText(13f, color = ChatraColors.TextSecondary),
                )
            }
            if (!container.referenceFrame && row.isUnknownSender) BasicText("Nieznany nadawca", style = ChatraType.Meta)
            Preview(row, unread)
        }
    }
    if (menu) app.chatra.ui.common.ChoiceDialog(row.title, listOf(if (unread) "Oznacz jako przeczytane" else "Oznacz jako nieprzeczytane", if (row.pinned) "Odepnij" else "Przypnij", "Anuluj"), { option ->
        scope.launch {
            runCatching {
                when (option) {
                    0 -> if (unread) container.conversations.markRead(row.id) else container.conversations.markUnread(row.id)
                    1 -> container.conversations.setPinned(row.id, !row.pinned)
                }
            }
        }
    }, { menu = false })
}

@Composable
private fun Preview(row: ConversationSummary, unread: Boolean) {
    when (val p = row.preview) {
        is Preview.SendFailed -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ChatraIcons.Alert, 15.dp, ChatraColors.Error, 2.2f)
            BasicText("Nie wysłano. Dotknij, aby ponowić.", style = chatraText(14f, 600, 19f, color = ChatraColors.Error))
        }
        is Preview.Draft -> BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = ChatraColors.Draft, fontWeight = FontWeight.W600)) { append("Szkic: ") }
                append(p.text)
            },
            style = ChatraType.Preview, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        is Preview.Text -> {
            val text = if (p.senderPrefix != null) "${p.senderPrefix}: ${p.text}" else p.text
            val singleLine = row.isGroupMms || row.isUnknownSender
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (row.isUnknownSender) {
                        SmallTag("Nieznany numer")
                        Spacer(Modifier.width(6.dp))
                    }
                    BasicText(
                        text,
                        style = if (unread) ChatraType.PreviewUnread else ChatraType.Preview,
                        maxLines = if (singleLine) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (unread) UnreadBadge(row.unreadCount, if (row.unreadCount == 1) "1 nieprzeczytana" else "${row.unreadCount} nieprzeczytane")
            }
        }
    }
}
