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
    val navigator = remember { initial ?: Navigator(Tab.Conversations, startStack(container)) }
    val appearance by container.appearance.value.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // Back: najpierw IME, potem arkusz, potem ekran (specyfikacja 5).
    BackHandler {
        focus.clearFocus()
        keyboard?.hide()
        if (!navigator.back()) onExit()
    }
    CompositionLocalProvider(
        LocalContainer provides container,
        LocalClock provides container.clock,
        LocalAvatarTones provides container::avatarTone,
        LocalReduceTransparency provides (appearance.reduceTransparency || forceMatte),
    ) {
        val screenBackdrop = rememberBackdrop()
        val listStates = remember { mutableMapOf<Tab, LazyListState>() }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().backdropSource(screenBackdrop)) {
                Screen(navigator.screen, navigator, listStates)
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
            ConversationScreen(model, { navigator.back() }, {}, { navigator.push(Route.Extras(route.id)) }, {})
        }
        Route.Scheduled -> {
            val model = viewModel { ScheduledViewModel(c.schedules, c.sims, c.clock) }
            val list = listStates.getOrPut(Tab.Scheduled) { rememberLazyListState() }
            ScheduledScreen(model, c.exactAlarms, list, { navigator.push(Route.Conversation(it)) }, dock)
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
        else -> Placeholder(sheet.toString())
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
