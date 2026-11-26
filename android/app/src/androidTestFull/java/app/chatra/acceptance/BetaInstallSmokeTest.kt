package app.chatra.acceptance

import android.content.ContentValues
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Telephony
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.ChatraApplication
import app.chatra.MainActivity
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class BetaInstallSmokeTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun builtBetaFindsSystemSmsAndReopensDraftOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("betaSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.chatra", context.packageName)
        // Read constants from the APK installed on the emulator without compiler inlining.
        val config = context.classLoader.loadClass("app.chatra.BuildConfig")
        assertTrue(config.getField("DEBUG").getBoolean(null))
        assertFalse(config.getField("DEMO").getBoolean(null))
        assertFalse(config.getField("HD_PILOT").getBoolean(null))
        listOf("BACKEND_ORIGIN", "PLAY_PRODUCT", "FIREBASE_APIKEY", "FIREBASE_APPLICATIONID",
            "FIREBASE_SENDERID", "FIREBASE_PROJECTID").forEach { field ->
            assertEquals(field, "", config.getField(field).get(null))
        }
        val network = context.getSystemService(ConnectivityManager::class.java)
        runBlocking {
            withTimeout(10_000) { while (network.activeNetwork != null) delay(100) }
        }
        assertNull(network.activeNetwork)

        val app = context.applicationContext as ChatraApplication
        val container = app.container(false)
        assertTrue(container.smsRole.isHeld())
        assertNull(container.hd)
        assertNull(container.billing)
        val resolver = context.contentResolver
        val sender = "+12025550162"
        val text = "Chatra beta QA Żółwchatraqa"
        val draftText = "Szkic bez internetu — zażółć"
        val uri = requireNotNull(resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put("address", sender)
            put("thread_id", Telephony.Threads.getOrCreateThreadId(context, sender))
            put("body", text)
            put("date", System.currentTimeMillis())
            put("read", 1)
        }))
        try {
            val id = runBlocking {
                container.conversations.refresh()
                withTimeout(20_000) {
                    container.conversations.inbox()
                        .first { rows -> rows.any { row -> row.participants.any { it.address.raw == sender } } }
                        .single { row -> row.participants.any { it.address.raw == sender } }.id
                }
            }
            val openConversation = Intent(context, MainActivity::class.java)
                .setAction(SystemPlatform.ACTION_OPEN_CONVERSATION)
                .putExtra(SystemPlatform.EXTRA_CONVERSATION_ID, id.value)
            val composer = ui.onNodeWithContentDescription("Wiadomość SMS do", substring = true)
            ActivityScenario.launch<MainActivity>(openConversation).use { scenario ->
                ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                ui.onNodeWithContentDescription("Szukaj kontaktu, treści lub kodu").performClick()
                val search = ui.onNodeWithContentDescription("Szukaj w rozmowach")
                search.performClick()
                search.performTextInput("zolwchatraqa")
                search.performImeAction()
                val result = hasText(text) and hasClickAction()
                ui.waitUntil(20_000) { ui.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(result).assertIsDisplayed().performClick()
                search.assertDoesNotExist()
                ui.onNodeWithText(text).assertIsDisplayed()
                composer.performClick()
                composer.performTextInput(draftText)
            }
            runBlocking {
                withTimeout(10_000) { container.drafts.draft(id).first { it?.text == draftText } }
                assertTrue(container.sendQueue.operations().first().none { it.body == draftText })
            }
            ActivityScenario.launch<MainActivity>(openConversation).use {
                ui.waitUntil(15_000) { ui.onAllNodesWithText(draftText).fetchSemanticsNodes().isNotEmpty() }
                composer.assertIsDisplayed().assertTextEquals(draftText)
                ui.onNodeWithText(text).assertIsDisplayed()
                assertNull(network.activeNetwork)
            }
        } finally {
            runBlocking {
                container.drafts.all().first().filter { it.text == draftText }.forEach { container.drafts.delete(it.id) }
            }
            resolver.delete(uri, null, null)
        }
    }
}
