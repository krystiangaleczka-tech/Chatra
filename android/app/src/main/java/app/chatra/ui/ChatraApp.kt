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
import app.chatra.ui.inbox.InboxScreen
import app.chatra.ui.inbox.InboxViewModel

val LocalContainer = androidx.compose.runtime.staticCompositionLocalOf<AppContainer> { error("container") }

@Composable
fun ChatraApp(container: AppContainer, initial: Navigator?, onExit: () -> Unit) {
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
        LocalReduceTransparency provides appearance.reduceTransparency,
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

/** Start pojawia się, dopóki nie ma roli SMS (wariant full); demo zaczyna od Skrzynki. */
private fun startStack(container: AppContainer): List<Route> = listOf(Route.Inbox)

@Composable
private fun Screen(route: Route, navigator: Navigator, listStates: MutableMap<Tab, LazyListState>) {
    val c = LocalContainer.current
    val dock = MainDock(navigator.tab, navigator::select) { navigator.push(Route.NewMessage()) }
    when (route) {
        Route.Inbox -> {
            val model = viewModel { InboxViewModel(c.conversations) }
            val list = listStates.getOrPut(Tab.Conversations) { rememberLazyListState() }
            InboxScreen(model, list, { navigator.push(Route.Conversation(it)) }, { navigator.push(Route.Search()) }, {}, dock)
        }
        is Route.Conversation -> {
            val model = conversationModel(c, route.id)
            ConversationScreen(model, { navigator.back() }, {}, { navigator.push(Route.Extras(route.id)) }, {})
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
            ExtrasSheet(model, navigator::popSheets) { navigator.replaceTop(Route.Schedule(sheet.id)) }
        }
        else -> Placeholder(sheet.toString())
    }
}

/** Rozmowa i jej arkusze dzielą jeden ViewModel (szkic, SIM, zdjęcia). */
@Composable
private fun conversationModel(c: AppContainer, id: app.chatra.core.model.ConversationId) = viewModel(key = "conv-${id.value}") {
    ConversationViewModel(id, c.conversations, c.drafts, c.sims, c.sendQueue, c.sending.value, c.clock)
}

@Composable
internal fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText(name, style = ChatraType.Body) }
}
