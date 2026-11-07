package app.chatra.core.testing

import app.chatra.core.model.RoleRequestResult
import app.chatra.core.model.SmsCapability
import app.chatra.core.model.SmsRoleGate
import kotlinx.coroutines.delay

/**
 * Fałszywy adapter roli SMS (CH010): odgrywa sukces, anulowanie, odmowę albo brak telefonii,
 * bez dotykania systemu. W wariancie demo scenariusz wybiera intencja (`--es role denied`).
 */
class FakeSmsRoleGate(
    var capability: SmsCapability = SmsCapability.Available,
    var nextResult: RoleRequestResult = RoleRequestResult.Granted,
    var held: Boolean = false,
    private val delayMillis: Long = 0,
) : SmsRoleGate {
    var requests = 0
        private set
    var settingsOpened = 0
        private set

    override suspend fun capability() = capability
    override fun isHeld() = held

    override suspend fun request(): RoleRequestResult {
        requests++
        if (delayMillis > 0) delay(delayMillis)
        if (nextResult == RoleRequestResult.Granted) held = true
        return nextResult
    }

    override fun openSystemSettings() {
        settingsOpened++
    }

    companion object {
        /** granted (domyślnie) · cancelled · denied · unavailable · no-telephony · held */
        fun scenario(name: String?, delayMillis: Long = 0): FakeSmsRoleGate = when (name?.lowercase()) {
            "cancelled" -> FakeSmsRoleGate(nextResult = RoleRequestResult.Cancelled, delayMillis = delayMillis)
            "denied" -> FakeSmsRoleGate(nextResult = RoleRequestResult.Denied, delayMillis = delayMillis)
            "unavailable" -> FakeSmsRoleGate(SmsCapability.RoleUnavailable, delayMillis = delayMillis)
            "no-telephony" -> FakeSmsRoleGate(SmsCapability.NoTelephony, delayMillis = delayMillis)
            "held" -> FakeSmsRoleGate(held = true, delayMillis = delayMillis)
            else -> FakeSmsRoleGate(delayMillis = delayMillis)
        }
    }
}
