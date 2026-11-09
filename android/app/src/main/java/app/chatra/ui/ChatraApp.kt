package app.chatra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.createSavedStateHandle
import app.chatra.ui.search.SearchScreen
import app.chatra.ui.search.SearchViewModel
import app.chatra.ui.newmessage.NewMessageScreen
import app.chatra.ui.newmessage.NewMessageViewModel
import app.chatra.ui.appearance.AppearanceScreen
import app.chatra.ui.common.TextAction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.LocalReduceTransparency
import app.chatra.core.designsystem.component.ProvideBackdrop
import app.chatra.core.designsystem.glass.backdropSource
import app.chatra.core.designsystem.glass.rememberBackdrop
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.di.AppContainer
import app.chatra.navigation.Navigator
import app.chatra.navigation.Route
import app.chatra.navigation.Tab
import app.chatra.ui.common.LocalAvatarTones
import app.chatra.ui.common.LocalClock
import app.chatra.ui.common.MainDock
import app.chatra.ui.conversation.ConversationScreen
import app.chatra.ui.conversation.ConversationViewModel
import app.chatra.ui.extras.ExtrasSheet
import app.chatra.ui.schedule.ScheduleSheet
import app.chatra.ui.scheduled.ScheduledScreen
import app.chatra.ui.scheduled.ScheduledViewModel
import app.chatra.ui.inbox.InboxScreen
import app.chatra.ui.inbox.InboxViewModel
import app.chatra.ui.start.StartScreen
import app.chatra.ui.start.StartViewModel

val LocalContainer = androidx.compose.runtime.staticCompositionLocalOf<AppContainer> { error("container") }

/** [forceMatte]: demo/QA — panele matowe bez rozmycia, do porównania kosztu szkła (CH003). */
@Composable
fun ChatraApp(container: AppContainer, initial: Navigator?, onExit: () -> Unit, forceMatte: Boolean = false) {
    val navigator = rememberSaveable(saver = Navigator.Saver) { initial ?: Navigator(Tab.Conversations, startStack(container)) }
    val appearance by container.appearance.value.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = LocalChrome.current.ime > 0.dp
    // Back: najpierw IME, potem arkusz, potem ekran (specyfikacja 5).
    BackHandler {
        focus.clearFocus()
        keyboard?.hide()
        if (!imeVisible && !navigator.back()) onExit()
    }
    CompositionLocalProvider(
        LocalContainer provides container,
        LocalClock provides container.clock,
        LocalAvatarTones provides container::avatarTone,
        LocalReduceTransparency provides (appearance.reduceTransparency || forceMatte),
    ) {
        val screenBackdrop = rememberBackdrop()
        val listStates = remember { mutableMapOf<Tab, LazyListState>() }
        val savedScreens = rememberSaveableStateHolder()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().backdropSource(screenBackdrop)) {
                savedScreens.SaveableStateProvider(navigator.screen.toString()) { Screen(navigator.screen, navigator, listStates) }
            }
            navigator.sheet?.let { sheet ->
                Box(Modifier.fillMaxSize().background(Backgrounds.SheetScrim))
                ProvideBackdrop(screenBackdrop) { Sheet(sheet, navigator) }
            }
        }
    }
}

/** Start pojawia się, dopóki nie ma roli SMS (wariant full); demo zaczyna od Skrzynki (S01: `--es screen S01`). */
private fun startStack(container: AppContainer): List<Route> =
    if (container.isDemo || container.smsRole.isHeld()) listOf(Route.Inbox) else listOf(Route.Start)

@Composable
private fun Screen(route: Route, navigator: Navigator, listStates: MutableMap<Tab, LazyListState>) {
    val c = LocalContainer.current
    val dock = MainDock(navigator.tab, navigator::select) { navigator.push(Route.NewMessage()) }
    when (route) {
        Route.Start -> {
            val model = viewModel { StartViewModel(c.smsRole) }
            // Po nadaniu roli Start nie zostaje pod Skrzynką.
            StartScreen(model, { navigator.resetTo(Route.Inbox) }, { navigator.push(Route.Privacy) }, { navigator.push(Route.Help) })
        }
        Route.Inbox -> {
            val model = viewModel { InboxViewModel(c.conversations) }
            val list = listStates.getOrPut(Tab.Conversations) { rememberLazyListState() }
            InboxScreen(model, list, { navigator.push(Route.Conversation(it)) }, { navigator.push(Route.Search()) }, {}, dock)
        }
        is Route.Conversation -> {
            val model = conversationModel(c, route.id)
            ConversationScreen(model, { navigator.back() }, {}, { navigator.push(Route.Extras(route.id)) }, {}, target = route.message)
        }
        Route.Scheduled -> {
            val model = viewModel { ScheduledViewModel(c.schedules, c.sims, c.clock) }
            val list = listStates.getOrPut(Tab.Scheduled) { rememberLazyListState() }
            ScheduledScreen(model, c.exactAlarms, list, { navigator.push(Route.Conversation(it)) }, dock)
        }
        is Route.Search -> {
            val model = viewModel(key = "search") { SearchViewModel(createSavedStateHandle(), c.search, route.initialQuery, route.initialFilter) }
            SearchScreen(model, { navigator.back() }) { navigator.push(Route.Conversation(it.conversationId, it.ref)) }
        }
        Route.Appearance -> AppearanceScreen(c.appearance) { navigator.back() }
        // Pełna plansza S10 w CH019; na razie dostęp do S09 z korzenia Ustawień.
        Route.Settings -> ChatraScreen(Backgrounds.Settings, floating = { dock.Render(Modifier.align(Alignment.BottomStart)) }) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = LocalChrome.current.top(54.dp))) {
                BasicText("Ustawienia", style = ChatraType.ListTitle)
                TextAction("Wygląd", { navigator.push(Route.Appearance) })
            }
        }
        else -> Placeholder(route.toString())
    }
}

@Composable
private fun Sheet(sheet: Route.Sheet, navigator: Navigator) {
    val c = LocalContainer.current
    when (sheet) {
        is Route.Extras -> {
            val model = conversationModel(c, sheet.id)
            // Zaplanuj nad Dodatkami: „Wróć” w S05 wraca do Dodatków (wzorzec), Back systemowy tak samo.
            ExtrasSheet(model, navigator::popSheets) { navigator.push(Route.Schedule(sheet.id)) }
        }
        is Route.Schedule -> {
            val model = conversationModel(c, sheet.id)
            ScheduleSheet(model, if (sheet.preset) c.schedulePreset else null, c.exactAlarms, onBack = { navigator.back() }) {
                navigator.popSheets()
                navigator.select(Tab.Scheduled)
            }
        }
        is Route.NewMessage -> {
            val model = viewModel(key = "new-message") { NewMessageViewModel(createSavedStateHandle(), c, if (sheet.preset) c.newMessagePreset else emptyList()) }
            NewMessageScreen(model, navigator::popSheets) { id -> navigator.popSheets(); navigator.push(Route.Conversation(id)) }
        }
    }
}

/** Rozmowa i jej arkusze dzielą jeden ViewModel (szkic, SIM, zdjęcia). */
@Composable
private fun conversationModel(c: AppContainer, id: app.chatra.core.model.ConversationId) = viewModel(key = "conv-${id.value}") {
    ConversationViewModel(id, c.conversations, c.drafts, c.sims, c.sendQueue, c.schedules, c.sending.value, c.clock)
}

@Composable
internal fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText(name, style = ChatraType.Body) }
}
