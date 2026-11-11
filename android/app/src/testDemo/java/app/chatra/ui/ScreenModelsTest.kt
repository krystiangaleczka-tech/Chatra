package app.chatra.ui

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.saveable.SaverScope
import app.chatra.core.model.*
import app.chatra.core.testing.*
import app.chatra.di.*
import app.chatra.navigation.*
import app.chatra.ui.newmessage.NewMessageViewModel
import app.chatra.ui.search.SearchViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenModelsTest {
    private fun container(scope: CoroutineScope, queueOverride: ((InMemoryChatra) -> SendQueue)? = null): AppContainer {
        val repo = InMemoryChatra(FixedClock(), scope, frozen = true)
        return object : AppContainer {
            override val conversations = repo
            override val drafts = repo
            override val sims = repo
            override val contacts = repo
            override val sendQueue = queueOverride?.invoke(repo) ?: repo
            override val schedules = repo
            override val search = repo
            override val clock = FixedClock()
            override val smsRole = FakeSmsRoleGate.scenario("held", 0)
            override val exactAlarms = FakeExactAlarmGate(true)
            override val schedulePreset: SchedulePreset? = null
            override val appearance = InMemoryStore(AppearancePrefs.Default)
            override val sending = InMemoryStore(SendingPrefs(defaultSubscriptionId = Fixture.SIM1.id))
            override val housekeeping = MutableStateFlow(Housekeeping(0, 0, 30))
            override fun avatarTone(id: ConversationId): String? = null
            override val referenceFrame = true
            override val isDemo = true
        }
    }

    @Test fun `compose keeps draft removes one chip and double tap enqueues each recipient once`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val c = container(backgroundScope)
            val saved = SavedStateHandle()
            val model = NewMessageViewModel(saved, c, Fixture.newMessageRecipients)
            model.remove(1); model.text("Spotkanie jutro")
            runCurrent()
            assertEquals(listOf("Mama", "Tomek W."), model.state.value.recipients.map { it.label })
            val restored = NewMessageViewModel(saved, c, emptyList())
            runCurrent()
            assertEquals("Spotkanie jutro", restored.state.value.text)
            var completed = 0
            restored.send { completed++ }; restored.send { completed++ }
            runCurrent()
            assertEquals(1, completed)
            assertEquals(2, c.sendQueue.operations().first().count { it.body == "Spotkanie jutro" })
            assertEquals("", restored.state.value.text)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `failed second enqueue retries only recipients not already accepted`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            var attempt = 0
            val c = container(backgroundScope) { repo -> object : SendQueue by repo {
                override suspend fun enqueue(draft: Draft, undoSeconds: Int): OperationId {
                    attempt++
                    if (attempt == 2) error("test failure")
                    return repo.enqueue(draft, undoSeconds)
                }
            } }
            val model = NewMessageViewModel(SavedStateHandle(), c, Fixture.newMessageRecipients)
            model.text("Ponowienie"); runCurrent()
            model.send {}; runCurrent()
            assertEquals(2, model.state.value.recipients.size)
            assertFalse(model.state.value.recipients.any { it.address == Fixture.mama.address })
            model.send {}; runCurrent()
            val sent = c.sendQueue.operations().first().filter { it.body == "Ponowienie" }
            assertEquals(3, sent.size)
            assertEquals(3, sent.flatMap { it.recipients }.distinctBy { it.normalized }.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `empty compose is disabled invalid typed address blocks send and duplicate contacts do not add chips`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val c = container(backgroundScope)
            val model = NewMessageViewModel(SavedStateHandle(), c, emptyList())
            runCurrent()
            val before = c.sendQueue.operations().first().size
            model.send { fail("empty draft sent") }; runCurrent()
            assertEquals(before, c.sendQueue.operations().first().size)
            model.add(Fixture.mama); model.add(Fixture.mama.copy(address = Address("+48600100200")))
            model.text("Treść"); runCurrent()
            assertEquals(1, model.state.value.recipients.size)
            assertTrue(model.state.value.canSend)
            model.input("Paczka24"); model.addInput(); runCurrent()
            assertFalse(model.state.value.canSend)
            assertNotNull(model.state.value.error)
            model.input(""); runCurrent()
            assertTrue(model.state.value.canSend)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `search state is restored and failures offer retry`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            var attempts = 0
            val repo = object : SearchRepository {
                override fun search(query: SearchQuery): Flow<SearchResults> = flow {
                    attempts++
                    if (attempts == 1) error("read failed") else emit(SearchResults())
                }
            }
            val saved = SavedStateHandle()
            val model = SearchViewModel(saved, repo, "paczk", "parcels")
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
            runCurrent(); assertTrue(model.state.value.error)
            model.retry(); runCurrent(); assertFalse(model.state.value.error)
            model.text("ŁÓDŹ"); model.filter(SearchFilter.Codes); model.sender(Fixture.Ids.Ola)
            runCurrent()
            val restored = SearchViewModel(saved, repo, "", null)
            assertEquals("ŁÓDŹ", restored.query.value)
            assertEquals(SearchFilter.Codes.name, restored.filter.value)
            assertEquals(Fixture.Ids.Ola.value, restored.sender.value)
            collector.cancel(); runCurrent(); advanceTimeBy(5_001); runCurrent()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `navigation restores all tabs with query reserved characters and message anchor`() {
        val nav = Navigator()
        nav.push(Route.Search("a|b\nŁÓDŹ", "parcels"))
        val anchor = MessageRef.Provider(ProviderRef(ProviderTransport.SMS, 42))
        nav.push(Route.Conversation(Fixture.Ids.Paczka24, anchor))
        nav.select(Tab.Settings); nav.push(Route.Appearance)
        val saved = with(Navigator.Saver) { SaverScope { true }.save(nav)!! }
        val restored = Navigator.Saver.restore(saved)!!
        assertEquals(Route.Appearance, restored.top)
        restored.select(Tab.Conversations)
        assertEquals(Route.Conversation(Fixture.Ids.Paczka24, anchor), restored.top)
        restored.back()
        assertEquals(Route.Search("a|b\nŁÓDŹ", "parcels"), restored.top)
    }
}
