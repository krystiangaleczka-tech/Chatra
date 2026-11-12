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
    @Test fun `counter reacts with unchanged SIM and preferences do not rewrite accepted countdown`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val c = container(backgroundScope)
            val model = NewMessageViewModel(SavedStateHandle(), c, listOf(Fixture.mama))
            model.text("CH041 queue snapshot")
            runCurrent()
            val sim = model.state.value.sim
            c.sending.update { it.copy(smsCounterEnabled = false, undoSeconds = 10) }
            runCurrent()
            assertEquals(sim, model.state.value.sim)
            assertFalse(model.state.value.counterEnabled)
            model.send {}; runCurrent()
            val before = c.sendQueue.operations().first().single { it.body == "CH041 queue snapshot" }
            assertEquals(c.clock.now().plusSeconds(10), before.undoDeadline)
            c.sending.update { it.copy(smsCounterEnabled = true, undoSeconds = 0) }
            runCurrent()
            assertTrue(model.state.value.counterEnabled)
            assertEquals(before, c.sendQueue.operations().first().single { it.id == before.id })
        } finally { Dispatchers.resetMain() }
    }

    private fun container(scope: CoroutineScope, draftsOverride: DraftRepository? = null, queueOverride: ((InMemoryChatra) -> SendQueue)? = null): AppContainer {
        val repo = InMemoryChatra(FixedClock(), scope, frozen = true)
        return object : AppContainer {
            override val templates: TemplateRepository get() = error("unused in model tests")
            override val media: AttachmentStore get() = error("unused in model tests")
            override val notifications = InMemoryStore(NotificationPrefs())
            override val conversations = repo
            override val drafts = draftsOverride ?: repo
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

    @Test fun `attachment only shared draft clears attachments after acceptance and keeps the sent snapshot`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val c = container(backgroundScope)
            val attachment = AttachmentId("shared-photo")
            val external = ExternalCompose("share-qa", listOf(Fixture.mama.address), "", listOf(attachment))
            val model = NewMessageViewModel(SavedStateHandle(), c, emptyList(), external)
            runCurrent()
            assertEquals(1, model.state.value.attachmentCount)
            assertTrue(model.state.value.canSend)
            var completed = 0
            model.send { completed++ }; runCurrent()
            assertEquals(1, completed)
            assertEquals(0, model.state.value.attachmentCount)
            assertTrue(c.sendQueue.operations().first().any { attachment in it.attachmentIds })
        } finally { Dispatchers.resetMain() }
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
            model.add(Fixture.mama.copy(address = Address("600 100 200")))
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

    private class DurableDrafts : DraftRepository {
        private val rows = MutableStateFlow<List<Draft>>(emptyList())
        override val durable = true
        override fun all(): Flow<List<Draft>> = rows
        override fun draft(conversationId: ConversationId) = rows.map { list -> list.firstOrNull { it.conversationId == conversationId } }
        override suspend fun save(draft: Draft) {
            val old = rows.value.firstOrNull { it.id == draft.id }
            if (old == null || old.revision < draft.revision) rows.value = listOf(draft) + rows.value.filter { it.id != draft.id }
        }
        override suspend fun delete(id: DraftId) { rows.value = rows.value.filter { it.id != id } }
    }
    @Test fun `durable group MMS opens the whole group conversation after acceptance`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val c = container(backgroundScope, draftsOverride = DurableDrafts())
            val people = Fixture.newMessageRecipients.take(2)
            val expected = c.conversations.conversationFor(people.map { it.address })
            val model = NewMessageViewModel(SavedStateHandle(), c, people); runCurrent()
            model.mode(RecipientMode.GroupMms); model.text("Grupowy MMS"); runCurrent()
            var opened: ConversationId? = null
            model.send { opened = it }; runCurrent()
            assertEquals(expected, opened)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `fresh editor restores durable recipients names text unfinished input and SIM without saved state`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val drafts = DurableDrafts()
            val c = container(backgroundScope, draftsOverride = drafts)
            val first = NewMessageViewModel(SavedStateHandle(), c, emptyList())
            runCurrent()
            first.add(Fixture.mama); first.text("Trwały tekst"); first.input("niedokończony numer"); first.sim(Fixture.SIM2.id)
            runCurrent()
            val restored = NewMessageViewModel(SavedStateHandle(), c, emptyList())
            runCurrent()
            assertEquals("Trwały tekst", restored.state.value.text)
            assertEquals("niedokończony numer", restored.state.value.input)
            assertEquals(listOf("Mama"), restored.state.value.recipients.map { it.label })
            assertEquals(Fixture.SIM2.id, (restored.state.value.sim as SimChoice.Ready).sim.id)
            assertFalse(restored.state.value.canSend)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `accepting a durable snapshot blocks duplicate clicks and edits until enqueue completes`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val drafts = DurableDrafts()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val c = container(backgroundScope, queueOverride = { repo -> object : SendQueue by repo {
                override suspend fun enqueue(draft: Draft, undoSeconds: Int): OperationId {
                    calls++; release.await(); return repo.enqueue(draft, undoSeconds)
                }
            } }, draftsOverride = drafts)
            val model = NewMessageViewModel(SavedStateHandle(), c, emptyList()); runCurrent()
            model.add(Fixture.mama); model.text("Migawka"); runCurrent()
            model.send {}; model.send {}; model.text("Zmiana podczas zapisu"); model.remove(0); runCurrent()
            assertEquals(1, calls); assertEquals("Migawka", model.state.value.text)
            release.complete(Unit); runCurrent()
            assertEquals("", model.state.value.text)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `cancel into an empty conversation editor remains restored after a fresh view model`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val drafts = DurableDrafts()
            val conversation = Fixture.Ids.Mama
            val restored = Draft(DraftId.new(), conversation, listOf(Fixture.mama.address), "Przywrócone po anulowaniu", RecipientMode.Single,
                emptyList(), Fixture.SIM1.id, FixedClock().now(), subscriptionLabel = Fixture.SIM1.label)
            val c = container(backgroundScope, draftsOverride = drafts, queueOverride = { repo -> object : SendQueue by repo {
                override suspend fun cancel(id: OperationId): Boolean { drafts.save(restored); return true }
            } })
            fun editor() = app.chatra.ui.conversation.ConversationViewModel(conversation, c.conversations, drafts, c.sims, c.sendQueue,
                c.schedules, c.sending.value, c.clock, draftWriteScope = backgroundScope)
            val first = editor()
            val one = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.state.collect() }
            runCurrent(); first.cancel(OperationId("pending")); runCurrent()
            assertEquals(restored.text, first.state.value.text)
            assertEquals(1, drafts.all().first().size)
            val fresh = editor()
            val two = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fresh.state.collect() }
            runCurrent(); assertEquals(restored.text, fresh.state.value.text)
            assertEquals(Fixture.SIM1.id, (fresh.state.value.sim as SimChoice.Ready).sim.id)
            one.cancel(); two.cancel(); advanceTimeBy(5001); runCurrent()
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
            val model = SearchViewModel(saved, repo, "paczk", "parcels", debounceMillis = 0)
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
