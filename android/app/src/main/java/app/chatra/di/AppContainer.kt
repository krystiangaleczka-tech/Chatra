package app.chatra.di

import app.chatra.core.model.AppearancePrefs
import app.chatra.core.model.Clock
import app.chatra.core.model.ContactsDirectory
import app.chatra.core.model.ConversationId
import app.chatra.core.model.ConversationRepository
import app.chatra.core.model.DraftRepository
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
    val clock: Clock

    /** Rola domyślnej aplikacji SMS (S01). Tymczasowy kontrakt CH010; adapter systemowy w CH021. */
    val smsRole: SmsRoleGate
    val appearance: PreferenceStore<AppearancePrefs>
    val sending: PreferenceStore<SendingPrefs>
    val housekeeping: Flow<Housekeeping>

    /** Odcień awatara wskazany przez dane (fixture); null → odcień z hasha uczestników. */
    fun avatarTone(id: ConversationId): String?

    /** Tryb referencyjny 390 × 844 dp (zrzuty CH006): zamrożony zegar, fontScale 1.0, bez pasków. */
    val referenceFrame: Boolean

    /** Wariant demonstracyjny: bez prawdziwej wysyłki i zakupów. */
    val isDemo: Boolean
}

/** Liczniki porządku do podsumowań S10 — wynikają ze stanu, nie są stałymi. */
data class Housekeeping(val blockedCount: Int, val trashCount: Int, val trashDays: Int)

interface PreferenceStore<T> {
    val value: StateFlow<T>
    suspend fun update(transform: (T) -> T)
}
