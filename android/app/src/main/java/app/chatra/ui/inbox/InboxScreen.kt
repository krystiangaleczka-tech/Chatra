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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
    ChatraScreen(
        Backgrounds.Inbox,
        floating = { dock.Render(Modifier.align(Alignment.BottomStart)) },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top(52.dp), bottom = chrome.bottom(26.dp) + 64.dp + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("header") { Header(onMore) }
            if (!container.isDemo) item("development") {
                Column {
                    BasicText("Wersja testowa: SMS i MMS. Obsługa MMS czeka na sprawdzenie u operatorów. Harmonogram działa lokalnie i wymaga dokładnych alarmów. Chatra HD jest jeszcze niedostępna.", style = chatraText(14f, 600, color = ChatraColors.ErrorText))
                    app.chatra.ui.settings.PermissionPrompt(app.chatra.platform.AccessFeature.Sms)
                    if (state.history is app.chatra.core.model.HistoryStatus.Error) {
                        BasicText((state.history as app.chatra.core.model.HistoryStatus.Error).message, style = ChatraType.Preview)
                        app.chatra.ui.common.TextAction("Odśwież historię", model::refresh)
                    }
                }
            }
            item("search") { SearchField(onSearch) }
            item("filter") { Filter(state.filter, state.unreadConversations, model::setFilter) }
            item("list") { ConversationCard(state, onOpen) }
        }
    }
}

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
private fun ConversationCard(state: InboxState, onOpen: (ConversationId) -> Unit) {
    GlassSurface(GlassStyles.Card, RoundedCornerShape(28.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Card)) {
        Column(Modifier.clip(RoundedCornerShape(28.dp))) {
            when {
                state.loading -> EmptyLine("Wczytujemy rozmowy z telefonu…")
                state.pinned.isEmpty() && state.others.isEmpty() ->
                    EmptyLine(if (state.filter == InboxFilter.Unread) "Wszystko przeczytane." else "Nie masz jeszcze rozmów.")
                else -> {
                    if (state.pinned.isNotEmpty()) {
                        GroupLabel("Przypięte", divider = false)
                        state.pinned.forEach { ConversationRow(it, onOpen) }
                    }
                    if (state.others.isNotEmpty()) {
                        GroupLabel("Pozostałe", divider = state.pinned.isNotEmpty())
                        state.others.forEach { ConversationRow(it, onOpen) }
                    }
                }
            }
        }
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
