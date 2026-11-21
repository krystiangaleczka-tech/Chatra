package app.chatra.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.model.*
import app.chatra.core.testing.Fixture
import app.chatra.di.*
import app.chatra.navigation.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic demo data only; no SMS role, physical recipient, network or real contact access. */
class ConversationDetailsTest {
    @get:Rule val ui = createComposeRule()

    private fun launch(id: ConversationId, wrap: (AppContainer) -> AppContainer = { it }): Pair<AppContainer, Navigator> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val base = createContainer(app, referenceFrame = true)
        val c = wrap(object : AppContainer by base {
            override val notifications = InMemoryStore(NotificationPrefs())
        })
        val nav = Navigator(initialStack = listOf(Route.Inbox, Route.Conversation(id)))
        ui.setContent { ProvideChrome(false) { ChatraApp(c, nav, onExit = {}) } }
        val title = Fixture.inbox.single { it.id == id }.title
        ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription("Wiadomość SMS do $title").fetchSemanticsNodes().isNotEmpty() }
        return c to nav
    }

    @Test fun detailsKeepDraftAndDoNotReadMessagesCoveredByThePanel() {
        val shown = AtomicInteger()
        val readRefs = java.util.concurrent.ConcurrentHashMap.newKeySet<MessageRef>()
        val newRef = MessageRef.Provider(ProviderRef(ProviderTransport.SMS, 999999))
        val newBody = "Nowa wiadomość QA pod panelem"
        lateinit var messages: MutableStateFlow<List<Message>>
        val (c, nav) = launch(Fixture.Ids.Ola) { base ->
            messages = MutableStateFlow(Fixture.messages.getValue(Fixture.Ids.Ola))
            object : AppContainer by base {
                override val conversations = object : ConversationRepository by base.conversations {
                    override fun messages(id: ConversationId) = if (id == Fixture.Ids.Ola) messages else base.conversations.messages(id)
                    override suspend fun markShown(id: ConversationId, refs: Set<MessageRef>) { shown.incrementAndGet(); readRefs.addAll(refs) }
                }
            }
        }
        ui.onNodeWithContentDescription("Wiadomość SMS do Ola Nowak").performTextReplacement("Szkic zachowany po szczegółach")
        ui.onNodeWithContentDescription("Szczegóły rozmowy").performClick()
        ui.onNodeWithText("Kontakt i numery").assertExists()
        val callsBefore = shown.get()
        assertTrue("The visible conversation must exercise read tracking first", callsBefore > 0)
        ui.runOnIdle {
            val incoming = messages.value.first { it.direction == Direction.Incoming }
            messages.value = messages.value + incoming.copy(ref = newRef, body = newBody,
                timestamp = messages.value.maxOf { it.timestamp }.plusSeconds(1), read = false)
        }
        ui.waitForIdle()
        assertEquals("A covered message must not be marked read", callsBefore, shown.get())
        assertFalse(readRefs.contains(newRef))
        ui.onNodeWithText("Odepnij rozmowę").performScrollTo().performClick()
        ui.onNodeWithText("Przypnij rozmowę").assertExists()
        ui.onNodeWithText("Wycisz rozmowę").performScrollTo().performClick()
        ui.onNodeWithText("Na godzinę").performClick()
        ui.waitUntil { c.notifications.value.value.mutedUntil[Fixture.Ids.Ola.value] != null }
        ui.onNodeWithText("Wróć do wiadomości").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Wiadomość SMS do Ola Nowak").assertTextContains("Szkic zachowany po szczegółach")
        ui.runOnIdle { assertNull(nav.sheet) }
        // A new item must not steal the saved history position. Explicitly reveal it first.
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(newBody))
        ui.onNodeWithText(newBody).assertIsDisplayed()
        ui.waitUntil(5_000) { readRefs.contains(newRef) }
    }

    @Test fun alphanumericSenderCannotDialAndTrashRequiresConfirmation() {
        val (_, nav) = launch(Fixture.Ids.Paczka24)
        ui.onNodeWithContentDescription("Szczegóły rozmowy").performClick()
        ui.onNodeWithText("Zadzwoń:", substring = true).assertDoesNotExist()
        ui.onNodeWithText("Ten nadawca nie udostępnia numeru", substring = true).assertExists()
        ui.onNodeWithText("Przenieś do kosza…").performScrollTo().performClick()
        ui.onNodeWithText("Anuluj").performClick()
        ui.runOnIdle { assertEquals(Route.ConversationDetails(Fixture.Ids.Paczka24), nav.sheet) }
        ui.onNodeWithText("Wróć do wiadomości").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(Route.Conversation(Fixture.Ids.Paczka24), nav.top) }
    }

    @Test fun groupShowsEveryRecipientAndBlockingCanBeCancelled() {
        val (c, _) = launch(Fixture.Ids.Brygada)
        ui.onNodeWithContentDescription("Szczegóły rozmowy").performClick()
        for (p in Fixture.brygada) ui.onNodeWithText("Zadzwoń: ${p.label}").assertExists().assertIsNotEnabled()
        ui.onNodeWithText("Zablokuj: Kuba").performScrollTo().performClick()
        ui.onNodeWithText("Anuluj").performClick()
        assertFalse(c.organization.blocked.value.any { it.id == recipientKey(Fixture.kuba.address.raw) })
        ui.onNodeWithText("Kontakt i numery").assertDoesNotExist()
        if (InstrumentationRegistry.getArguments().getString("captureChatraQa") != "true") return
        ui.onNodeWithText("Szczegóły rozmowy").performScrollTo()
        val image = ui.onRoot().captureToImage().asAndroidBitmap()
        // Stream to the ADB shell. UTP removes the app's files when it uninstalls the APK.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val path = "/data/local/tmp/CH065-details.png"
        // UiAutomation launches commands directly, so no shell redirection is available.
        val (stdout, stdin) = automation.executeShellCommandRw("dd of=$path")
        ParcelFileDescriptor.AutoCloseInputStream(stdout).use { output ->
            ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { input ->
                assertTrue("Could not encode the panel as PNG", image.compress(Bitmap.CompressFormat.PNG, 100, input))
            }
            output.readBytes() // Wait for dd to close the output file.
        }
        val stat = automation.executeShellCommand("stat -c %s $path")
        val size = ParcelFileDescriptor.AutoCloseInputStream(stat).use { it.bufferedReader().readText().trim().toLongOrNull() }
        assertTrue("The exported panel PNG is empty or missing", size != null && size > 0)
    }
}
