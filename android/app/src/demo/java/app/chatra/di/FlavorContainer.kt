package app.chatra.di

import app.chatra.local.*
import android.app.Application
import app.chatra.core.model.AppearancePrefs
import app.chatra.core.model.Clock
import app.chatra.core.model.ConversationId
import app.chatra.core.model.SendingPrefs
import app.chatra.core.model.ProOffer
import app.chatra.core.testing.FakeExactAlarmGate
import app.chatra.core.testing.FakeSmsRoleGate
import app.chatra.core.testing.FixedClock
import app.chatra.core.testing.Fixture
import app.chatra.core.testing.InMemoryChatra
import app.chatra.prefs.DataStorePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId

/** Wariant demo: fixture 29.10.2026, bez prawdziwej wysyłki, kontaktów i zakupów (CH006). */
/** [roleScenario]: scenariusz fałszywej roli SMS dla S01 (`--es role cancelled|denied|unavailable|no-telephony|held`). */
fun createContainer(app: Application, referenceFrame: Boolean, roleScenario: String? = null, exactAlarms: Boolean = true): AppContainer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val clock: Clock = if (referenceFrame) FixedClock() else DemoClock()
    val repo = InMemoryChatra(clock, scope, frozen = referenceFrame)
    val notificationPrefs = LocalNotificationPrefs(app)
    val prefs = DataStorePreferences(app, scope, if (referenceFrame) "demo-reference" else "demo")
    val organization = MemoryOrganizationPreview(true,
        (1..Fixture.BlockedCount).map { BlockedPreview("blocked-$it", "+48 500 000 00$it") },
        (1..Fixture.TrashCount).map { TrashPreview(ConversationId("demo-trash-$it"), "Rozmowa demonstracyjna $it") })
    return object : AppContainer {
        override val templates = LocalTemplates(app)
        override val media = PrivateMediaStore(app)
        override val notifications = notificationPrefs
        override val conversations = repo
        override val drafts = repo
        override val sims = repo
        override val contacts = repo
        override val sendQueue = repo
        override val schedules = repo
        override val search = repo
        override val clock = clock
        override val exactAlarms = FakeExactAlarmGate(exactAlarms)
        override val schedulePreset = SchedulePreset(Fixture.ScheduleFormBody, Fixture.ScheduleFormDate)
        override val newMessagePreset = Fixture.newMessageRecipients
        override val smsRole = FakeSmsRoleGate.scenario(roleScenario, delayMillis = if (referenceFrame) 0 else 600)
        // Tryb referencyjny zawsze startuje z ustawień domyślnych wzorca.
        override val conversationAppearance = if (referenceFrame) InMemoryStore(emptyMap<String, app.chatra.core.model.AppearanceOverride>()) else prefs.threads
        override val appearance = if (referenceFrame) InMemoryStore(AppearancePrefs.Default) else prefs.appearance
        override val sending = if (referenceFrame) InMemoryStore(SendingPrefs(defaultSubscriptionId = Fixture.SIM1.id)) else prefs.sending
        override val organization = organization
        override val proOffer = if (referenceFrame) ProOffer.DemoPlaceholder else ProOffer.Unavailable
        override val housekeeping = combine(organization.blocked, organization.trash) { blocked, trash -> Housekeeping(blocked.size, trash.size, Fixture.TrashDays) }
        override fun avatarTone(id: ConversationId) = Fixture.avatarTones[id]
        override val referenceFrame = referenceFrame
        override val isDemo = true
    }
}

/** Zegar demo: startuje z chwili fixture i płynie w czasie rzeczywistym. */
private class DemoClock : Clock {
    private val startElapsed = SystemClock.elapsedRealtime()
    override fun now(): Instant = FixedClock.FIXTURE_NOW.plusMillis(SystemClock.elapsedRealtime() - startElapsed)
    override fun zone(): ZoneId = FixedClock.WARSAW
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

class InMemoryStore<T>(initial: T) : PreferenceStore<T> {
    override val value = MutableStateFlow(initial)
    override suspend fun update(transform: (T) -> T) {
        value.value = transform(value.value)
    }
}
