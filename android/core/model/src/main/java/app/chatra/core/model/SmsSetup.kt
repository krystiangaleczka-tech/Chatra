package app.chatra.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Czy urządzenie w ogóle może zostać klientem SMS (S01, specyfikacja 7). */
enum class SmsCapability { Available, NoTelephony, RoleUnavailable }

/** Wynik prośby o rolę domyślnej aplikacji SMS. */
enum class RoleRequestResult { Granted, Cancelled, Denied, Unavailable }

/**
 * Tymczasowy kontrakt konfiguracji S01 (CH010). Rzeczywisty adapter `RoleManager.ROLE_SMS`
 * z uprawnieniami dostarcza CH021; demo i testy używają fałszywego adaptera.
 * Ekran startowy nie prosi o aparat ani zdjęcia — te uprawnienia są kontekstowe (CH031).
 */
interface SmsRoleGate {
    suspend fun capability(): SmsCapability
    fun isHeld(): Boolean
    suspend fun request(): RoleRequestResult

    /** Ustawienia systemu → aplikacje domyślne, gdy system nie pokazuje już prośby. */
    fun openSystemSettings()
}

sealed interface SetupState {
    data object Checking : SetupState
    data class Ready(val notice: Notice? = null) : SetupState
    data object Requesting : SetupState
    data class Blocked(val reason: SmsCapability) : SetupState
    data object Done : SetupState

    enum class Notice { Cancelled, Denied }
}

/**
 * Maszyna stanów S01: sprawdzenie → gotowość → prośba → sukces / anulowanie / odmowa / brak telefonii.
 * Prośba nie jest powtarzana, dopóki poprzednia trwa (wielokrotne kliknięcie CTA).
 */
class SmsSetup(private val gate: SmsRoleGate) {
    private val mutable = MutableStateFlow<SetupState>(SetupState.Checking)
    val state: StateFlow<SetupState> = mutable.asStateFlow()

    suspend fun check() {
        if (gate.isHeld()) {
            mutable.value = SetupState.Done
            return
        }
        mutable.value = when (val c = gate.capability()) {
            SmsCapability.Available -> SetupState.Ready()
            else -> SetupState.Blocked(c)
        }
    }

    /** CTA: prośba o rolę albo — po odmowie trwałej — przejście do ustawień systemu. */
    suspend fun primary() {
        val current = mutable.value as? SetupState.Ready ?: return
        if (current.notice == SetupState.Notice.Denied) {
            gate.openSystemSettings()
            return
        }
        mutable.value = SetupState.Requesting
        mutable.value = when (gate.request()) {
            RoleRequestResult.Granted -> SetupState.Done
            RoleRequestResult.Cancelled -> SetupState.Ready(SetupState.Notice.Cancelled)
            RoleRequestResult.Denied -> SetupState.Ready(SetupState.Notice.Denied)
            RoleRequestResult.Unavailable -> SetupState.Blocked(SmsCapability.RoleUnavailable)
        }
    }

    /** Powrót na ekran (np. z ustawień systemu): rola mogła zostać nadana poza aplikacją. */
    fun recheck() {
        if (mutable.value is SetupState.Ready && gate.isHeld()) mutable.value = SetupState.Done
    }
}
