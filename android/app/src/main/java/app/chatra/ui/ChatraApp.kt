package app.chatra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
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
import app.chatra.ui.settings.*
import app.chatra.core.model.SettingsEntry
import app.chatra.core.model.ObservableSmsRoleGate
import app.chatra.platform.ActivityPlatform
import app.chatra.ui.common.ChoiceDialog
import app.chatra.ui.conversation.color
import app.chatra.ui.conversation.conversationBackground
import app.chatra.core.model.HistoryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

val LocalContainer = androidx.compose.runtime.staticCompositionLocalOf<AppContainer> { error("container") }

/** [forceMatte]: demo/QA — panele matowe bez rozmycia, do porównania kosztu szkła (CH003). */
@Composable
fun ChatraApp(container: AppContainer, initial: Navigator?, onExit: () -> Unit, forceMatte: Boolean = false) {
    val navigator = rememberSaveable(saver = Navigator.Saver) { initial ?: Navigator(Tab.Conversations, startStack(container)) }
    val globalAppearance by container.appearance.value.collectAsStateWithLifecycle()
    val overridesFlow = remember(container) { container.conversationAppearance?.value ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }
    val overrides by overridesFlow.collectAsStateWithLifecycle()
    val pro = container.billing?.state?.collectAsStateWithLifecycle()?.value?.entitled == true
    val threadAppearance = (navigator.screen as? Route.Conversation)?.id?.let { overrides[it.value] }
    val appearance = app.chatra.core.model.resolveAppearance(globalAppearance, threadAppearance, proAllowed = pro)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // Czytane w chwili cofnięcia, nie w kompozycji — inaczej korzeń przebudowuje się w każdej klatce animacji IME.
    val chrome = LocalChrome.current
    val observableRole = container.smsRole as? ObservableSmsRoleGate
    val roleState = remember(container) { observableRole?.held ?: kotlinx.coroutines.flow.MutableStateFlow(true) }
    val held by roleState.collectAsStateWithLifecycle()
    val platform = container.platform as? ActivityPlatform
    val incomingState = remember(container) { platform?.incomingDraftId ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
    val incoming by incomingState.collectAsStateWithLifecycle()
    val conversationState = remember(container) { platform?.incomingConversationId ?: kotlinx.coroutines.flow.MutableStateFlow(null) }
    val incomingConversation by conversationState.collectAsStateWithLifecycle()
    val scheduledRouteState = remember(container) { platform?.incomingScheduled ?: kotlinx.coroutines.flow.MutableStateFlow(false) }
    val incomingScheduled by scheduledRouteState.collectAsStateWithLifecycle()
    LaunchedEffect(held, incoming, incomingConversation, incomingScheduled, navigator.screen) {
        if (!container.isDemo && !held && navigator.screen !in listOf(Route.Start, Route.Help, Route.Privacy, Route.DesignCatalog)) {
            navigator.select(Tab.Conversations)
            navigator.resetTo(Route.Start)
        } else if (held && incomingScheduled && platform != null) {
            navigator.select(Tab.Scheduled)
            platform.acknowledgeScheduled()
        } else if (held && incomingConversation != null && platform != null) {
            navigator.select(Tab.Conversations)
            navigator.resetTo(Route.Inbox, Route.Conversation(incomingConversation!!, fromExternal = true))
            platform.acknowledgeConversation(incomingConversation!!)
        } else if (held && incoming != null && platform != null && navigator.screen != Route.Start) {
            navigator.push(Route.NewMessage(externalId = incoming))
            platform.acknowledgeCompose(incoming!!)
        }
    }
    // Back: najpierw IME, potem arkusz, potem ekran (specyfikacja 5).
    BackHandler {
        val imeVisible = chrome.ime > 0.dp
        focus.clearFocus()
        keyboard?.hide()
        if (!imeVisible && !navigator.back()) onExit()
    }
    val dark = appearance.mode == app.chatra.core.model.ThemeMode.Dark ||
        (appearance.mode == app.chatra.core.model.ThemeMode.Auto && androidx.compose.foundation.isSystemInDarkTheme())
    val tokens = app.chatra.core.designsystem.theme.AppearanceTokens(
        dark = dark, contrast = appearance.increaseContrast || app.chatra.core.model.Composition.Contrast in listOf(globalAppearance.composition, appearance.composition),
        matte = forceMatte || appearance.reduceTransparency || appearance.increaseContrast ||
            app.chatra.core.model.Composition.Contrast in listOf(globalAppearance.composition, appearance.composition),
        composition = appearance.composition.name, compact = appearance.density == app.chatra.core.model.Density.Compact,
        accentOverride = threadAppearance?.accent.color(), bubbleOverride = threadAppearance?.bubble.color(),
    )
    val activityContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.SideEffect {
        (activityContext as? androidx.activity.ComponentActivity)?.let { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        app.chatra.core.designsystem.theme.LocalAppearanceTokens provides tokens,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            androidx.compose.ui.platform.LocalDensity.current.density,
            if (container.referenceFrame) 1f else androidx.compose.ui.platform.LocalDensity.current.fontScale * appearance.textScale,
        ),
        LocalContainer provides container,
        LocalClock provides container.clock,
        LocalAvatarTones provides container::avatarTone,
        LocalReduceTransparency provides (appearance.reduceTransparency || forceMatte),
    ) {
        val screenBackdrop = rememberBackdrop()
        val listStates = remember { mutableMapOf<Tab, LazyListState>() }
        val savedScreens = rememberSaveableStateHolder()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().backdropSource(screenBackdrop).then(
                if (navigator.sheet != null) Modifier.clearAndSetSemantics {} else Modifier,
            )) {
                savedScreens.SaveableStateProvider(navigator.screen.toString()) { Screen(navigator.screen, navigator, listStates, threadAppearance) }
            }
            navigator.sheet?.let { sheet ->
                Box(Modifier.fillMaxSize().background(Backgrounds.SheetScrim)
                    .clickable(interactionSource = null, indication = null) {}
                    .clearAndSetSemantics {})
                ProvideBackdrop(screenBackdrop) {
                    Box(Modifier.fillMaxSize().semantics { paneTitle = when (sheet) {
                        is Route.NewMessage -> "Nowa wiadomość"
                        is Route.Extras -> "Dodatki"
                        is Route.Schedule -> "Zaplanuj wysyłkę"
                        is Route.ConversationDetails -> "Szczegóły rozmowy"
                        is Route.ConversationGallery -> "Wspólne"
                        is Route.ThreadAppearance -> "Wygląd tej rozmowy"
                        is Route.ConversationExport -> "Udostępnianie i eksport"
                    } }) { Sheet(sheet, navigator) }
                }
            }
        }
    }
}

/** Start pojawia się, dopóki nie ma roli SMS (wariant full); demo zaczyna od Skrzynki (S01: `--es screen S01`). */
private fun startStack(container: AppContainer): List<Route> =
    if (container.isDemo || container.smsRole.isHeld()) listOf(Route.Inbox) else listOf(Route.Start)

@Composable
private fun Screen(
    route: Route, navigator: Navigator, listStates: MutableMap<Tab, LazyListState>,
    threadAppearance: app.chatra.core.model.AppearanceOverride?,
) {
    val c = LocalContainer.current
    val dock = MainDock(navigator.tab, navigator::select) { navigator.push(Route.NewMessage()) }
    val scope = rememberCoroutineScope()
    var inboxMenu by remember { mutableStateOf(false) }
    var confirmRead by remember { mutableStateOf(false) }
    var configureGestures by remember { mutableStateOf(false) }
    var inboxError by remember { mutableStateOf<String?>(null) }
    when (route) {
        Route.Start -> {
            val model = viewModel { StartViewModel(c.smsRole) }
            // Po nadaniu roli Start nie zostaje pod Skrzynką.
            StartScreen(model, { navigator.resetTo(Route.Inbox) }, { navigator.push(Route.Privacy) }, { navigator.push(Route.Help) })
        }
        Route.Inbox -> {
            val model = viewModel { InboxViewModel(c.conversations) }
            val list = listStates.getOrPut(Tab.Conversations) { rememberLazyListState() }
            InboxScreen(model, list, { navigator.push(Route.Conversation(it)) }, { navigator.push(Route.Search()) }, { inboxMenu = true }, dock,
                onDetails = { id -> navigator.push(Route.Conversation(id)); navigator.push(Route.ConversationDetails(id)) },
                onMessage = { id, ref -> navigator.push(Route.Conversation(id, ref)) })
        }
        is Route.Conversation -> {
            val model = conversationModel(c, route.id)
            var simPicker by remember { mutableStateOf(false) }
            val state by model.state.collectAsStateWithLifecycle()
            val statusFlow = remember(c) { c.conversations.historyStatus() }
            val status by statusFlow.collectAsStateWithLifecycle(HistoryStatus.Loading)
            val unavailable = state.summary == null || state.summary?.trashed == true ||
                (route.fromExternal && state.messages.isEmpty())
            if (state.historyLoaded && unavailable && status == HistoryStatus.Ready) {
                DetailPage("Rozmowa niedostępna", { navigator.back() }) {
                    BasicText("Rozmowa mogła zostać usunięta lub przeniesiona do kosza.", style = ChatraType.Body)
                }
            } else {
                ConversationScreen(model, { navigator.back() }, { navigator.push(Route.ConversationDetails(route.id)) },
                    { navigator.push(Route.Extras(route.id)) }, { simPicker = true }, target = route.message,
                    contentVisible = navigator.sheet == null, anchorRequest = navigator.messageJump,
                    sceneDecoration = conversationBackground(threadAppearance))
            }
            if (simPicker) app.chatra.ui.common.SimPicker(state.sims, (state.sim as? app.chatra.core.model.SimChoice.Ready)?.sim?.id, model::selectSim, { simPicker = false })
        }
        Route.Scheduled -> {
            val model = viewModel { ScheduledViewModel(c.schedules, c.sims, c.clock) }
            val list = listStates.getOrPut(Tab.Scheduled) { rememberLazyListState() }
            ScheduledScreen(model, c.exactAlarms, list, { navigator.push(Route.Conversation(it)) }, dock)
        }
        is Route.Search -> {
            val model = viewModel(key = "search-${route.conversation?.value.orEmpty()}") { SearchViewModel(createSavedStateHandle(), c.search, route.initialQuery, route.initialFilter, route.conversation) }
            SearchScreen(model, { navigator.back() }) { navigator.push(Route.Conversation(it.conversationId, it.ref)) }
        }
        Route.Appearance -> AppearanceScreen(c.appearance, onPro = { navigator.push(Route.SettingsDetail(Route.SettingsDetail.Kind.Pro)) }) { navigator.back() }
        Route.Settings -> SettingsScreen({ action ->
            when (action) {
                SettingsEntry.Appearance -> navigator.push(Route.Appearance)
                SettingsEntry.Privacy -> navigator.push(Route.Privacy)
                SettingsEntry.Counter -> Unit
                else -> navigator.push(Route.SettingsDetail(Route.SettingsDetail.Kind.valueOf(action.name)))
            }
        }, dock, onHelp = { navigator.push(Route.Help) })
        is Route.SettingsDetail -> SettingsDetailScreen(route.kind, { navigator.back() }, { navigator.select(Tab.Conversations) })
        Route.Privacy -> PrivacyScreen { navigator.back() }
        Route.Help -> HelpScreen({ navigator.back() }, { navigator.push(Route.Privacy) }, { navigator.push(Route.DesignCatalog) })
        Route.DesignCatalog -> DesignCatalogScreen { navigator.back() }
        else -> Placeholder(route.toString())
    }
    if (inboxMenu) ChoiceDialog("Rozmowy", listOf("Oznacz wszystkie jako przeczytane", "Ustawienia", "Pomoc", "Projekty uzupełniające",
        "Gesty skrzynki"), { option ->
        when (option) {
            0 -> confirmRead = true
            1 -> navigator.select(Tab.Settings)
            2 -> navigator.push(Route.Help)
            3 -> navigator.push(Route.DesignCatalog)
            4 -> configureGestures = true
        }
    }, { inboxMenu = false })
    if (configureGestures) {
        val notifications by c.notifications.value.collectAsStateWithLifecycle()
        val enabled = notifications.inboxSwipes
        ChoiceDialog("Gesty skrzynki. W prawo zmieniasz przypięcie, w lewo zmieniasz wyciszenie.",
            listOf(if (enabled) "Wyłącz gesty" else "Włącz gesty"), {
                scope.launch {
                    try { c.notifications.update { it.copy(inboxSwipes = !enabled) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { inboxError = "Nie udało się zmienić ustawienia gestów." }
                }
            }, { configureGestures = false })
    }
    if (confirmRead) ChoiceDialog("Oznaczyć wszystkie rozmowy jako przeczytane?", listOf("Oznacz wszystkie"), {
        scope.launch {
            try { c.conversations.markAllRead() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { inboxError = "Nie udało się oznaczyć wszystkich rozmów." }
        }
    }, { confirmRead = false })
    inboxError?.let { message -> ChoiceDialog(message, emptyList(), {}, { inboxError = null }) }
}

@Composable
private fun Sheet(sheet: Route.Sheet, navigator: Navigator) {
    val c = LocalContainer.current
    when (sheet) {
        is Route.ConversationDetails -> {
            val model = conversationModel(c, sheet.id)
            app.chatra.ui.conversation.ConversationDetailsScreen(model,
                onBack = { navigator.back() },
                onSearch = { navigator.popSheets(); navigator.push(Route.Search(conversation = sheet.id)) },
                onSchedule = { navigator.replaceTop(Route.Schedule(sheet.id)) },
                onGallery = { navigator.push(Route.ConversationGallery(sheet.id)) },
                onTrashed = { navigator.popSheets(); navigator.back() },
                onAppearance = { navigator.push(Route.ThreadAppearance(sheet.id)) },
                onExport = { navigator.push(Route.ConversationExport(sheet.id)) },
                onMessage = { navigator.revealMessage(sheet.id, it) },
            )
        }
        is Route.ThreadAppearance -> app.chatra.ui.conversation.ThreadAppearanceScreen(sheet.id) { navigator.back() }
        is Route.ConversationExport -> app.chatra.ui.conversation.ConversationExportScreen(sheet.id) { navigator.back() }
        is Route.ConversationGallery -> {
            val gallery = c.gallery
            if (gallery == null) Placeholder("Galeria jest niedostępna.")
            else {
                val model = viewModel(key = "gallery-${sheet.id.value}") {
                    app.chatra.ui.gallery.GalleryViewModel(sheet.id, gallery)
                }
                app.chatra.ui.gallery.ConversationGalleryScreen(model, gallery, onBack = { navigator.back() }) { source ->
                    navigator.revealMessage(sheet.id, source)
                }
            }
        }
        is Route.Extras -> {
            val model = conversationModel(c, sheet.id)
            // Zaplanuj nad Dodatkami: „Wróć” w S05 wraca do Dodatków (wzorzec), Back systemowy tak samo.
            if (c.isDemo) ExtrasSheet(model, navigator::popSheets) { navigator.push(Route.Schedule(sheet.id)) }
            else app.chatra.ui.extras.MediaExtrasSheet(model, navigator::popSheets) { navigator.push(Route.Schedule(sheet.id)) }
        }
        is Route.Schedule -> {
            val model = conversationModel(c, sheet.id)
            ScheduleSheet(model, if (sheet.preset) c.schedulePreset else null, c.exactAlarms, onBack = { navigator.back() }) {
                navigator.popSheets()
                navigator.select(Tab.Scheduled)
            }
        }
        is Route.NewMessage -> {
            val model = viewModel(key = "new-message${sheet.externalId?.let { "-$it" }.orEmpty()}") { NewMessageViewModel(createSavedStateHandle(), c, if (sheet.preset) c.newMessagePreset else emptyList(), sheet.externalId?.let(c::externalDraft)) }
            NewMessageScreen(model, navigator::popSheets) { id -> navigator.popSheets(); navigator.push(Route.Conversation(id)) }
        }
    }
}

/** Rozmowa i jej arkusze dzielą jeden ViewModel (szkic, SIM, zdjęcia). */
@Composable
private fun conversationModel(c: AppContainer, id: app.chatra.core.model.ConversationId) = viewModel(key = "conv-${id.value}") {
    ConversationViewModel(id, c.conversations, c.drafts, c.sims, c.sendQueue, c.schedules, c.sending.value, c.clock, c.smsText, c.mmsAvailable, c.sendAccess, c.draftWriteScope)
}

@Composable
internal fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText(name, style = ChatraType.Body) }
}
