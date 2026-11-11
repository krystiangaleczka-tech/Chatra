package app.chatra.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.Avatar
import app.chatra.core.designsystem.component.GlassCircleButton
import app.chatra.core.designsystem.component.GlassStyles
import app.chatra.core.designsystem.component.GlassSurface
import app.chatra.core.designsystem.component.Shadows
import app.chatra.core.designsystem.component.cssShadow
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.component.topHighlight
import app.chatra.core.designsystem.glass.cssLinearGradient
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.model.DeliveryStatus
import app.chatra.core.model.Direction
import app.chatra.core.model.Message
import app.chatra.core.model.SimChoice
import app.chatra.ui.ChatraScreen
import app.chatra.ui.LocalChrome
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.LocalClock
import app.chatra.ui.common.PolishDates
import app.chatra.ui.common.avatarFor
import app.chatra.ui.common.formatDaySeparator
import java.time.LocalDate
import kotlin.math.ceil

private const val ContentTop = 108
private const val ContentBottom = 150

/** S03 Rozmowa. */
@Composable
fun ConversationScreen(
    model: ConversationViewModel,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    onExtras: () -> Unit,
    onPickSim: () -> Unit,
    target: app.chatra.core.model.MessageRef? = null,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val chrome = LocalChrome.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val nativeDrafts = !LocalContainer.current.isDemo
    val savedDrafts by model.savedDrafts.collectAsStateWithLifecycle()
    var draftPicker by remember { mutableStateOf(false) }
    var composerHeight by remember { mutableIntStateOf(0) }
    val top = chrome.top(50.dp) + (ContentTop - 50).dp
    val bottom = maxOf(chrome.bottom(26.dp) + (ContentBottom - 26).dp, chrome.bottom(26.dp) + composerHeight.dp + 12.dp)
    ChatraScreen(
        Backgrounds.Conversation,
        floating = {
            Header(state, { model.leave(onBack) }, onDetails, Modifier.align(Alignment.TopStart).padding(top = chrome.top(50.dp), start = 12.dp, end = 12.dp))
            Composer(state, model, onExtras, onPickSim, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = chrome.bottom(26.dp)).onSizeChanged { composerHeight = (it.height / density).toInt() })
        },
    ) {
        val list = rememberLazyListState()
        val rows = remember(state.messages) { buildRows(state.messages) }
        LaunchedEffect(list, rows, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow {
                    val layout = list.layoutInfo
                    val viewport = layout.viewportEndOffset - layout.viewportStartOffset
                    layout.visibleItemsInfo.filter { viewport > 0 && minOf(it.offset + it.size, layout.viewportEndOffset) - maxOf(it.offset, layout.viewportStartOffset) >= minOf(it.size, viewport / 2) }
                        .mapNotNull { (rows.asReversed().getOrNull(it.index) as? Row.Bubble)?.message }
                        .filter { it.direction == Direction.Incoming }.map { it.ref }.toSet()
                }.distinctUntilChanged().collect { if (it.isNotEmpty()) model.onShown(it) }
            }
        }
        var anchored by remember(target) { androidx.compose.runtime.mutableStateOf(false) }
        LaunchedEffect(target, rows) {
            if (target != null && !anchored) {
                val index = rows.asReversed().indexOfFirst { it is Row.Bubble && it.message.ref == target }
                if (index >= 0) { list.scrollToItem(index); anchored = true }
            }
        }
        // Wzorzec przycina historię do obszaru 108…(844−150); nagłówek nie leży nad wiadomościami.
        LazyColumn(
            Modifier.fillMaxSize().padding(top = top, bottom = bottom).clipToBounds(),
            state = list,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        ) {
            items(rows.asReversed(), key = { it.key }) { row -> RowContent(row, state, model) }
            if (nativeDrafts) item("drafts") { app.chatra.ui.common.TextAction("Szkice", { draftPicker = true }) }
            if (state.hasOlder) item("older") { app.chatra.ui.common.TextAction("Wczytaj wcześniejsze wiadomości", model::loadOlder) }
        }
    }
    if (draftPicker) {
        val choices = savedDrafts.filter { it.conversationId == state.summary?.id && (it.text.isNotEmpty() || it.attachmentIds.isNotEmpty()) }
        app.chatra.ui.common.ChoiceDialog("Szkice rozmowy", choices.map { it.text.take(80) }.ifEmpty { listOf("Brak zapisanych szkiców") },
            { index -> choices.getOrNull(index)?.let(model::restore) }, { draftPicker = false })
    }
}

private sealed interface Row {
    val key: String
    data class Day(val date: LocalDate, val first: Boolean) : Row { override val key = "day-$date" }
    data class Bubble(val message: Message, val showStatus: Boolean) : Row { override val key = "m-${message.ref}" }
}

private fun buildRows(messages: List<Message>): List<Row> {
    val out = mutableListOf<Row>()
    var day: LocalDate? = null
    messages.forEachIndexed { i, m ->
        val d = m.timestamp.atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate()
        if (d != day) {
            out += Row.Day(d, day == null)
            day = d
        }
        val next = messages.getOrNull(i + 1)
        // Status pod ostatnią wysłaną w ciągu o tym samym stanie (jak na planszy).
        val show = m.direction == Direction.Outgoing && (next == null || next.direction == Direction.Incoming || next.status != m.status)
        out += Row.Bubble(m, show)
    }
    return out
}

@Composable
private fun RowContent(row: Row, state: ConversationState, model: ConversationViewModel) {
    when (row) {
        is Row.Day -> Box(Modifier.fillMaxWidth().padding(top = if (row.first) 0.dp else 6.dp), contentAlignment = Alignment.Center) {
            GlassSurface(GlassStyles.DayChip, RoundedCornerShape(12.dp)) {
                BasicText(formatDaySeparator(row.date, LocalClock.current), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = chatraText(12f, 600, color = ChatraColors.TextTertiary))
            }
        }
        is Row.Bubble -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MessageBubble(row.message)
            row.message.attachments.forEach { app.chatra.ui.common.ProviderThumbnail(it) }
            if (row.showStatus || row.message.status in setOf(DeliveryStatus.Failed, DeliveryStatus.PendingUndo, DeliveryStatus.Uncertain, DeliveryStatus.PartiallySent)) {
                Status(row.message, state, model)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: Message) {
    val incoming = m.direction == Direction.Incoming
    Box(Modifier.fillMaxWidth(), contentAlignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd) {
        when {
            m.hd != null -> HdCard(m)
            incoming -> GlassSurface(GlassStyles.Bubble, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp), Modifier.widthIn(max = 270.dp), shadows = listOf(Shadows.Bubble)) {
                BasicText(m.body, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = chatraText(16f, lineHeight = 21f))
            }
            m.status == DeliveryStatus.Failed -> {
                val shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                BasicText(
                    m.body,
                    Modifier.widthIn(max = 270.dp).clip(shape).background(ChatraColors.white(0.7f)).border(1.5.dp, ChatraColors.Error, shape)
                        .padding(horizontal = 14.5.dp, vertical = 9.5.dp),
                    style = chatraText(16f, lineHeight = 21f),
                )
            }
            else -> {
                val shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                BasicText(
                    m.body,
                    Modifier.widthIn(max = 270.dp)
                        .alpha(if (m.status == DeliveryStatus.PendingUndo) 0.62f else 1f)
                        .then(if (m.status == DeliveryStatus.PendingUndo) Modifier else Modifier.cssShadow(shape, Shadows.BubbleOut))
                        .clip(shape).background(ChatraColors.Accent)
                        .then(if (m.status == DeliveryStatus.PendingUndo) Modifier else Modifier.topHighlight(shape, ChatraColors.white(0.35f)))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    style = chatraText(16f, lineHeight = 21f, color = Color.White),
                )
            }
        }
    }
}

/** Karta HD szerokości 248: trzy miniatury (+N), plakietka „HD” w wadze 700 (G01). */
@Composable
private fun HdCard(m: Message) {
    val hd = m.hd ?: return
    val tiles = listOf(
        Triple(Color(0xFFF3C6A8), Color(0xFFC98E7A), RoundedCornerShape(16.dp, 6.dp, 6.dp, 6.dp)),
        Triple(Color(0xFFA9C7E8), Color(0xFF6C8FB8), RoundedCornerShape(6.dp)),
        Triple(Color(0xFFC9D8B0), Color(0xFF8DA674), RoundedCornerShape(6.dp, 16.dp, 6.dp, 6.dp)),
    )
    GlassSurface(GlassStyles.HdCard, RoundedCornerShape(22.dp), Modifier.width(248.dp), shadows = listOf(Shadows.HdCard)) {
        Column(Modifier.padding(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tiles.forEachIndexed { i, (a, b, shape) ->
                    val more = hd.itemCount - 3
                    val label = if (i == 2 && more > 0) "Zdjęcie 3 i jeszcze $more" else "Zdjęcie ${i + 1}"
                    Box(
                        Modifier.weight(1f).height(76.dp).clip(shape)
                            .drawGradient(150f, a, b)
                            .semantics { role = Role.Image; contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == 2 && more > 0) {
                            // CSS text-shadow: 0 1px 4px rgba(0,0,0,0.45)
                            val px = LocalDensity.current.density
                            BasicText("+$more", style = chatraText(17f, 700, color = Color.White).copy(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, px), 4f * px)))
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HdBadge()
                Column(Modifier.weight(1f)) {
                    BasicText("${hd.itemCount} ${photosWord(hd.itemCount)} · oryginał", style = chatraText(14f, 600))
                    BasicText("${formatMegabytes(hd.totalBytes)} · przesłano przez internet", style = chatraText(12f, color = ChatraColors.TextSecondary))
                }
            }
        }
    }
}

@Composable
internal fun HdBadge(vertical: androidx.compose.ui.unit.Dp = 3.dp) {
    BasicText(
        "HD",
        Modifier.clip(RoundedCornerShape(6.dp)).background(ChatraColors.TextPrimary).padding(horizontal = 6.dp, vertical = vertical),
        style = chatraText(11f, 700, letterSpacingEm = 0.04f, color = Color.White),
    )
}

internal fun photosWord(n: Int) = when {
    n == 1 -> "zdjęcie"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "zdjęcia"
    else -> "zdjęć"
}

/** „38,2 MB” (polski separator dziesiętny). */
internal fun formatMegabytes(bytes: Long): String = "%.1f MB".format(bytes / 1_000_000.0).replace('.', ',')

private fun Modifier.drawGradient(angle: Float, a: Color, b: Color) = drawBehind { drawRect(cssLinearGradient(angle, size, a, b)) }

@Composable
private fun Status(m: Message, state: ConversationState, model: ConversationViewModel) {
    val sim = state.sims.firstOrNull { it.id == m.subscriptionId }?.shortLabel
    val operation = m.operationId?.let { state.operations[it] }
    var retryPicker by remember { mutableStateOf(false) }
    var copyWarning by remember { mutableStateOf(false) }
    val demo = LocalContainer.current.isDemo
    val clock = LocalClock.current
    val zone = clock.zone()
    val tertiary = chatraText(12f, color = ChatraColors.TextTertiary)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        when (m.status) {
            DeliveryStatus.Failed -> Row(Modifier.widthIn(max = 330.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "Nie udało się wysłać. Wybierz kartę SIM i spróbuj ponownie.",
                    Modifier.weight(1f, fill = false),
                    style = chatraText(12f, 600, 16f, color = ChatraColors.ErrorText).copy(textAlign = TextAlign.End),
                )
                if (demo || operation?.canRetry == true) SmallPill("Ponów", ChatraColors.ErrorText, Color.White, ChatraColors.white(0.9f)) {
                    if (demo) m.operationId?.let { model.retry(it) } else retryPicker = true
                } else SmallPill("Sprawdź", ChatraColors.Link, ChatraColors.white(.85f), null) { m.operationId?.let(model::reconcile) }
            }
            DeliveryStatus.PendingUndo -> {
                val op = m.operationId?.let { state.operations[it] }
                var now by remember { mutableLongStateOf(clock.now().toEpochMilli()) }
                var elapsed by remember { mutableLongStateOf(clock.elapsedRealtimeMillis()) }
                LaunchedEffect(op?.undoDeadline) {
                    while (true) {
                        now = clock.now().toEpochMilli(); elapsed = clock.elapsedRealtimeMillis()
                        kotlinx.coroutines.delay(250)
                    }
                }
                val left = op?.undoElapsedDeadline?.let { ceil((it - elapsed) / 1000.0).toInt().coerceAtLeast(0) } ?: op?.undoDeadline?.let { ceil((it.toEpochMilli() - now) / 1000.0).toInt().coerceAtLeast(0) } ?: 0
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ChatraIcons.Clock, 14.dp, ChatraColors.TextTertiary, 2.2f)
                    BasicText("Wyślę za $left s", style = tertiary)
                    SmallPill("Anuluj", ChatraColors.Link, ChatraColors.white(0.85f), null) { m.operationId?.let(model::cancel) }
                }
            }
            DeliveryStatus.Uncertain, DeliveryStatus.PartiallySent -> Column(Modifier.widthIn(max = 340.dp), horizontalAlignment = Alignment.End) {
                BasicText(if (operation?.state == app.chatra.core.model.SendState.MISSED_NEEDS_DECISION) "Wysyłka wstrzymana po restarcie. Wybierz dalsze działanie."
                    else if (m.status == DeliveryStatus.PartiallySent) "Wysłano tylko część wiadomości. Ponowna wysyłka może ją powielić."
                    else "Nie wiadomo, czy operator przyjął wiadomość. Ponowna wysyłka może ją powielić.", style = tertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPill("Sprawdź status", ChatraColors.Link, ChatraColors.white(.85f), null) { m.operationId?.let(model::reconcile) }
                    if (operation?.canRetry == true) SmallPill("Ponów nieudane", ChatraColors.Link, ChatraColors.white(.85f), null) { retryPicker = true }
                    else SmallPill("Kopia do szkicu", ChatraColors.Link, ChatraColors.white(.85f), null) { copyWarning = true }
                }
            }
            else -> {
                val text = when {
                    m.hd?.downloadedBy != null -> "Pobrane przez ${m.hd?.downloadedBy}" + (sim?.let { " · $it" } ?: "")
                    m.status == DeliveryStatus.Delivered -> "Doręczono ${PolishDates.time((m.statusAt ?: m.timestamp).atZone(zone))}" +
                        (sim?.let { " · $it" } ?: "") + " · to nie oznacza odczytu"
                    m.status == DeliveryStatus.Sent -> "Wysłano" + (sim?.let { " · $it" } ?: "")
                    m.status == DeliveryStatus.Sending -> "Wysyłanie…"
                    m.status == DeliveryStatus.Uncertain -> "Nie wiadomo, czy operator przyjął wiadomość"
                    else -> null
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (text != null) BasicText(text, Modifier.padding(end = 6.dp), style = tertiary)
                    if (operation?.deliveryFailed == true) BasicText("Operator zgłosił błąd doręczenia. SMS został wysłany.", style = tertiary)
                    if (!demo && m.status == DeliveryStatus.Sending) SmallPill("Sprawdź status", ChatraColors.Link, ChatraColors.white(.85f), null) { m.operationId?.let(model::reconcile) }
                }
            }
        }
    }
    if (retryPicker) app.chatra.ui.common.SimPicker(state.sims, m.subscriptionId, { selected -> m.operationId?.let { model.retry(it, selected) } }, { retryPicker = false })
    if (copyWarning) app.chatra.ui.common.ChoiceDialog("Kopia może spowodować ponowne doręczenie tej samej wiadomości", listOf("Zapisz kopię do szkicu"),
        { m.operationId?.let(model::copyToDraft) }, { copyWarning = false })
}

@Composable
private fun SmallPill(text: String, color: Color, background: Color, border: Color?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.heightIn(min = 32.dp).clip(shape).background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .semantics { role = Role.Button }
            .pressable(onClick)
            .padding(horizontal = if (border != null) 13.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = chatraText(13f, 700, color = color)) }
}

@Composable
private fun Header(state: ConversationState, onBack: () -> Unit, onDetails: () -> Unit, modifier: Modifier) {
    val s = state.summary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(ChatraIcons.ChevronLeft, "Wróć do rozmów", onBack, strokeWidth = 2.2f)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (s != null) GlassSurface(GlassStyles.Control, RoundedCornerShape(24.dp), Modifier.height(48.dp), shadows = listOf(Shadows.Control)) {
                Row(Modifier.fillMaxHeight().padding(start = 5.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (content, tone) = avatarFor(s)
                    Avatar(content, tone, 38.dp, chatraText(14f, 600), 18.dp)
                    Column {
                        BasicText(s.title, style = chatraText(15f, 600, 19f), maxLines = 1)
                        BasicText(subtitle(s), style = chatraText(11f, lineHeight = 14f, color = ChatraColors.TextTertiary), maxLines = 1)
                    }
                }
            }
        }
        GlassCircleButton(ChatraIcons.Info, "Szczegóły rozmowy", onDetails)
    }
}

private fun subtitle(s: app.chatra.core.model.ConversationSummary): String = when {
    s.isGroupMms -> "Grupowy MMS · ${s.participants.size + 1} osoby"
    s.participants.firstOrNull()?.address?.canReply == false -> "Nadawca bez możliwości odpowiedzi"
    s.hdCapable -> "SMS · MMS · ma Chatra HD"
    else -> "SMS · MMS"
}

@Composable
private fun Composer(state: ConversationState, model: ConversationViewModel, onExtras: () -> Unit, onPickSim: () -> Unit, modifier: Modifier) {
    val reference = LocalContainer.current.referenceFrame
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!reference) {
            state.error?.let { BasicText(it, style = chatraText(13f, color = ChatraColors.ErrorText)) }
            if (!state.sendingAvailable && state.summary?.isGroupMms == true) BasicText("Wysyłanie grupowego MMS nie jest jeszcze dostępne.", style = chatraText(13f, color = ChatraColors.TextSecondary))
            if (!state.counter.preservesText) BasicText("System zmieniłby treść. Sprawdź podgląd licznika.", style = chatraText(13f, color = ChatraColors.ErrorText))
            if (state.text.length > 16384) BasicText("Wiadomość jest zbyt długa. Podziel ją przed wysłaniem.", style = chatraText(13f, color = ChatraColors.ErrorText))
            app.chatra.ui.settings.PermissionPrompt(app.chatra.platform.AccessFeature.Sms)
        }
        Row(Modifier.fillMaxWidth().padding(start = 52.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SimChip(state.sim, onPickSim)
            Spacer(Modifier.weight(1f))
            if (state.counterEnabled && state.text.isNotEmpty() && state.summary?.isGroupMms != true) {
                // Fixture S03 zachowuje literalny, błędny licznik ze wzorca (CH006, wyjątek).
                val label = if (reference && state.text == "Kupię kwiaty dla Ani") "21/160 · 1 SMS" else state.counter.label
                if (reference) BasicText(label, style = chatraText(12f, color = ChatraColors.TextTertiary).copy(fontFeatureSettings = "tnum"))
                else app.chatra.ui.common.SmsCounterAction(state.text, (state.sim as? SimChoice.Ready)?.sim?.id, model::onText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            GlassCircleButton(ChatraIcons.Plus, "Dodatki: zdjęcie, aparat, zaplanuj, szablony", onExtras, strokeWidth = 2.2f)
            GlassSurface(GlassStyles.Input, RoundedCornerShape(22.dp), Modifier.weight(1f).heightIn(min = 44.dp), shadows = listOf(Shadows.Control)) {
                Row(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val name = state.summary?.title.orEmpty()
                    Box(Modifier.weight(1f)) {
                        if (state.text.isEmpty()) BasicText("Wiadomość SMS", style = chatraText(16f, color = ChatraColors.TextSecondary))
                        BasicTextField(
                            state.text, model::onText,
                            Modifier.fillMaxWidth().semantics { contentDescription = "Wiadomość SMS do $name" },
                            textStyle = chatraText(16f),
                            cursorBrush = SolidColor(ChatraColors.Accent),
                            maxLines = 6,
                        )
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (state.canSend) ChatraColors.Accent else ChatraColors.Accent.copy(alpha = 0.4f))
                            .semantics { role = Role.Button; contentDescription = "Wyślij" }
                            .pressable(model::send, enabled = state.canSend),
                        contentAlignment = Alignment.Center,
                    ) { Icon(ChatraIcons.ArrowUp, 20.dp, Color.White, 2.4f) }
                }
            }
        }
    }
}

@Composable
private fun SimChip(choice: SimChoice, onPick: () -> Unit) {
    val label = when (choice) {
        is SimChoice.Ready -> choice.sim.label
        is SimChoice.NeedsConfirmation -> "Wybierz kartę SIM"
        SimChoice.NoSim -> "Brak karty SIM"
    }
    GlassSurface(
        GlassStyles.SimChip, RoundedCornerShape(16.dp),
        Modifier.height(32.dp).semantics { role = Role.Button; contentDescription = "Wyślij z karty $label, zmień" }.pressable(onPick),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 8.dp, end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ChatraIcons.Sim, 16.dp, ChatraColors.TextPrimary, 1.9f)
            BasicText(label, style = chatraText(13f, 600))
            Icon(ChatraIcons.ChevronDown, 14.dp, ChatraColors.TextPrimary, 2.2f)
        }
    }
}

