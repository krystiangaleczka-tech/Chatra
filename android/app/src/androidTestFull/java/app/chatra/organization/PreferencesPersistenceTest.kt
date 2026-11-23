package app.chatra.organization

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.model.*
import app.chatra.prefs.DataStorePreferences
import app.chatra.local.LocalNotificationPrefs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PreferencesPersistenceTest {
    @Test fun inboxGesturePreferenceSurvivesRestart() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(app.cacheDir, "prefs-qa-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(app) {
            override fun getFilesDir(): java.io.File = directory
        }
        try {
            val prefs = LocalNotificationPrefs(context)
            prefs.update { it.copy(inboxSwipes = true) }
            val restarted = LocalNotificationPrefs(context)
            assertTrue(restarted.value.value.inboxSwipes)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun everyAppearanceAndSendingOptionSurvivesStoreRecreationAndReset() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.filesDir, "qa-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            fun store() = DataStorePreferences(context, scope, "qa", PreferenceDataStoreFactory.create(scope = scope) { file })
            var prefs = store()
            val appearance = AppearancePrefs(Composition.Graphite, ThemeMode.Auto, Density.Compact, 7, true, true)
            val sending = SendingPrefs(SubscriptionId(17), 10, false, MediaVariant.NoLocationCopy, "QA SIM", false)
            val overrides = mapOf(
                "thread-a" to AppearanceOverride(composition = Composition.Mist, background = ThreadBackground.Sand,
                    accent = ThreadColor.Teal, bubble = ThreadColor.Plum, notificationPreview = false),
                "thread-b" to AppearanceOverride(mode = ThemeMode.Dark, background = ThreadBackground.Photo,
                    wallpaper = UUID.randomUUID().toString(), notificationPreview = true),
            )
            prefs.appearance.update { appearance }; prefs.sending.update { sending }
            prefs.threads.update { overrides }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            prefs = store()
            assertEquals(appearance, prefs.appearance.value.value)
            assertEquals(sending, prefs.sending.value.value)
            assertEquals(overrides, prefs.threads.value.value)
            prefs.appearance.update { AppearancePrefs.Default }
            prefs.threads.update { it - "thread-a" }
            assertEquals(sending, prefs.sending.value.value)
            val remaining = mapOf("thread-b" to overrides.getValue("thread-b"))
            withTimeout(5000) { prefs.threads.value.first { it == remaining } }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            prefs = store()
            assertEquals(AppearancePrefs.Default, prefs.appearance.value.value)
            assertEquals(sending, prefs.sending.value.value)
            assertEquals(remaining, prefs.threads.value.value)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
