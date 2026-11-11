package app.chatra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.*
import app.chatra.core.designsystem.icon.*
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.di.Housekeeping
import app.chatra.ui.*
import app.chatra.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onAction: (SettingsEntry) -> Unit, dock: MainDock) {
    val c = LocalContainer.current
    val sending by c.sending.value.collectAsStateWithLifecycle()
    val appearance by c.appearance.value.collectAsStateWithLifecycle()
    val sims by c.sims.cards().collectAsStateWithLifecycle(emptyList())
    val housekeeping by c.housekeeping.collectAsStateWithLifecycle(Housekeeping(0, 0, 30))
    val scope = rememberCoroutineScope()
    val chrome = LocalChrome.current
    val card = GlassStyles.Card.copy(from = ChatraColors.white(.82f), to = ChatraColors.white(.58f), border = ChatraColors.white(.9f))
    ChatraScreen(Backgrounds.Settings, floating = { dock.Render(Modifier.align(Alignment.BottomStart)) }) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top(52.dp), bottom = chrome.bottom(26.dp) + 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { BasicText("Ustawienia", Modifier.padding(horizontal = 4.dp).semantics { heading() }, style = ChatraType.ListTitle) }
            item {
                GlassSurface(card.copy(to = ChatraColors.white(.56f)), RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), shadows = listOf(Shadows.Card)) {
                    Row(Modifier.padding(start = 16.dp + cssBorder(), end = 14.dp + cssBorder(), top = 14.dp + cssBorder(), bottom = 14.dp + cssBorder()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText("Chatra Pro", style = chatraText(16f, 700))
                            BasicText(proSummary(c.proOffer, c.isDemo), style = chatraText(13f, lineHeight = 18f, color = ChatraColors.TextSecondary))
                        }
                        Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(ChatraColors.Accent).semantics { role = Role.Button; contentDescription = "Więcej o Chatra Pro" }.pressable({ onAction(SettingsEntry.Pro) }).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            BasicText("Więcej", style = chatraText(15f, 600, color = Color.White))
                        }
                    }
                }
            }
            item {
                SettingsCard(card, "Wysyłanie") {
                    SettingsRow("Karty SIM", simSettingsSummary(sims, sending.defaultSubscriptionId), ChatraIcons.Sim, ChatraColors.Accent, first = true, onClick = { onAction(SettingsEntry.Sims) })
                    SettingsRow("Opóźnienie wysłania", if (sending.undoSeconds == 0) "Bez okna anulowania" else "${sending.undoSeconds} s na anulowanie przed wysłaniem", ChatraIcons.Clock, Color(0xFFC2570C), onClick = { onAction(SettingsEntry.Delay) })
                    SettingsRow("Licznik części SMS", "Wersję bez polskich znaków tylko na życzenie", ChatraIcons.Lines, Color(0xFF5B6275), trailing = {
                        ChatraSwitch(sending.smsCounterEnabled, { value -> scope.launch { c.sending.update { it.copy(smsCounterEnabled = value) } } }, "Licznik części SMS")
                    })
                    SettingsRow("Chatra HD", when (sending.hdDefault) {
                        MediaVariant.OriginalHd -> "Domyślnie oryginał · bez Chatra: pytaj o link"
                        MediaVariant.NoLocationCopy -> "Domyślnie kopia bez lokalizacji"
                        MediaVariant.Mms -> "Domyślnie MMS · w limicie operatora"
                    }, null, ChatraColors.TextPrimary, last = true, onClick = { onAction(SettingsEntry.Hd) })
                }
            }
            item {
                SettingsCard(card, "Wygląd i porządek") {
                    SettingsRow("Wygląd", appearanceSummary(appearance), ChatraIcons.Appearance, Color(0xFF6A4FD8), first = true, onClick = { onAction(SettingsEntry.Appearance) })
                    SettingsRow("Nieznani nadawcy", "Zostają w skrzynce z oznaczeniem · to nie spam", ChatraIcons.Person, Color(0xFF5B6275), onClick = { onAction(SettingsEntry.Unknown) })
                    SettingsRow("Zablokowani", polishCount(housekeeping.blockedCount, "numer", "numery", "numerów") + " · możesz przywrócić", ChatraIcons.Blocked, ChatraColors.Error, onClick = { onAction(SettingsEntry.Blocked) })
                    SettingsRow("Kosz", polishCount(housekeeping.trashCount, "rozmowa", "rozmowy", "rozmów") + " · odzyskasz przez ${housekeeping.trashDays} dni", ChatraIcons.Trash, Color(0xFF5B6275), last = true, onClick = { onAction(SettingsEntry.Trash) })
                }
            }
            item {
                SettingsCard(card, null, shadow = false) {
                    SettingsRow("Prywatność", "Bez konta · indeks wyszukiwania na telefonie", ChatraIcons.Lock, ChatraColors.Success, first = true, onClick = { onAction(SettingsEntry.Privacy) })
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(style: app.chatra.core.designsystem.glass.GlassStyle, title: String?, shadow: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(style, RoundedCornerShape(24.dp), Modifier.fillMaxWidth(), shadows = if (shadow) listOf(Shadows.Card) else emptyList()) {
        Column(Modifier.padding(cssBorder())) {
            if (title != null) SectionLabel(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(title: String, summary: String, icon: ChatraIcon?, color: Color, first: Boolean = false, last: Boolean = false, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (!first) Modifier.drawTopLine(ChatraColors.Ink06) else Modifier)
        .then(if (onClick != null) Modifier.semantics(mergeDescendants = true) { role = Role.Button }.pressable(onClick) else Modifier)
        .padding(start = 16.dp, end = 14.dp, top = 6.dp, bottom = if (last) 8.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color), contentAlignment = Alignment.Center) {
            if (icon == null) BasicText("HD", style = chatraText(10f, 700, letterSpacingEm = .04f, color = Color.White)) else Icon(icon, 18.dp, Color.White, 1.9f)
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = chatraText(16f))
            BasicText(summary, style = chatraText(13f, color = ChatraColors.TextSecondary))
        }
        if (trailing != null) trailing() else Icon(ChatraIcons.ChevronRight, 16.dp, ChatraColors.TextSecondary)
    }
}
