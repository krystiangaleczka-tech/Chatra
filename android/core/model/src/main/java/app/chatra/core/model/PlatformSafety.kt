package app.chatra.core.model

import kotlinx.coroutines.flow.StateFlow

interface ObservableSmsRoleGate : SmsRoleGate {
    val held: StateFlow<Boolean>
    fun refresh()
}

data class SmsAccessSnapshot(val roleHeld: Boolean, val readGranted: Boolean, val sendGranted: Boolean) {
    val canRead get() = roleHeld && readGranted
    val canDispatch get() = roleHeld && sendGranted
}

/** Zewnętrzne wejście nigdy nie uruchamia wysyłki. Numer/treść nie trafiają do adresu nawigacji. */
data class ExternalCompose(val id: String, val recipients: List<Address>, val text: String)

fun externalCompose(recipients: List<String>, text: String, id: String): ExternalCompose? {
    if (text.length > 16_384 || '\u0000' in text || recipients.size !in 1..20 || recipients.any { !isOutgoingNumber(it) }) return null
    return ExternalCompose(id, recipients.map(::Address).distinctBy { it.normalized }, text)
}
