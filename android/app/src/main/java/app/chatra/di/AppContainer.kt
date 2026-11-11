package app.chatra.di

import app.chatra.core.model.AppearancePrefs
import app.chatra.core.model.Clock
import app.chatra.core.model.ContactsDirectory
import app.chatra.core.model.ConversationId
import app.chatra.core.model.ConversationRepository
import app.chatra.core.model.DraftRepository
import app.chatra.core.model.ExactAlarmGate
import app.chatra.core.model.ScheduleRepository
import app.chatra.core.model.SendQueue
import app.chatra.core.model.SendingPrefs
import app.chatra.core.model.SimRepository
import app.chatra.core.model.SmsRoleGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Składanie zależności (ręczne DI). Wariant `demo` dostarcza fixture CH006, `full` — provider
 * Telephony. Ekrany znają wyłącznie ten kontrakt.
 */
interface AppContainer {
    val conversations: ConversationRepository
    val drafts: DraftRepository
    val sims: SimRepository
    val contacts: ContactsDirectory
    val sendQueue: SendQueue
    val schedules: ScheduleRepository
    val search: app.chatra.core.model.SearchRepository
    val clock: Clock

    /** Rola domyślnej aplikacji SMS (S01). Tymczasowy kontrakt CH010; adapter systemowy w CH021. */
    val smsRole: SmsRoleGate

    /** Zgoda na dokładne alarmy dla S05 (adapter AlarmManager w CH035). */
    val exactAlarms: ExactAlarmGate

    /** Treść i termin planszy S05 w trybie referencyjnym (wyjątek fixture); null poza demo. */
    val schedulePreset: SchedulePreset?
    val newMessagePreset: List<app.chatra.core.model.Participant> get() = emptyList()
    val appearance: PreferenceStore<AppearancePrefs>
    val sending: PreferenceStore<SendingPrefs>
    val housekeeping: Flow<Housekeeping>
    val organization: OrganizationPreview get() = MemoryOrganizationPreview()
    val proOffer: app.chatra.core.model.ProOffer get() = app.chatra.core.model.ProOffer.Unavailable
    /** Transporty full są dopiero w CH026/CH032; nigdy nie zastępujemy ich atrapą. */
    val canSendMessages: Boolean get() = isDemo
    val sendAccess: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(canSendMessages)
    val platform: app.chatra.platform.PlatformAccess? get() = null
    /** Wejście SENDTO/RESPOND_VIA_MESSAGE, odczytane dopiero przez edytor. */
    fun externalDraft(id: String): app.chatra.core.model.ExternalCompose? = null

    /** Odcień awatara wskazany przez dane (fixture); null → odcień z hasha uczestników. */
    fun avatarTone(id: ConversationId): String?

    /** Tryb referencyjny 390 × 844 dp (zrzuty CH006): zamrożony zegar, fontScale 1.0, bez pasków. */
    val referenceFrame: Boolean

    /** Wariant demonstracyjny: bez prawdziwej wysyłki i zakupów. */
    val isDemo: Boolean
    val smsText: app.chatra.core.model.SmsTextAnalyzer get() = app.chatra.core.model.ModelSmsTextAnalyzer
}

/** Formularz S05 ze wzorca: treść różni się od kompozytora S03, więc plansza ma własny preset. */
data class SchedulePreset(val text: String, val local: java.time.LocalDateTime)

/** Liczniki porządku do podsumowań S10 — wynikają ze stanu, nie są stałymi. */
data class Housekeeping(val blockedCount: Int, val trashCount: Int, val trashDays: Int)

interface PreferenceStore<T> {
    val value: StateFlow<T>
    suspend fun update(transform: (T) -> T)
}
