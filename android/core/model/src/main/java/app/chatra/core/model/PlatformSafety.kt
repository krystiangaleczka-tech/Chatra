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
data class ExternalCompose(val id: String, val recipients: List<Address>, val text: String, val attachmentIds: List<AttachmentId> = emptyList(), val error: String? = null)

fun externalCompose(recipients: List<String>, text: String, id: String, attachments: List<AttachmentId> = emptyList(), allowEmptyRecipients: Boolean = false): ExternalCompose? {
    if (text.length > 16_384 || '\u0000' in text || recipients.size !in (if (allowEmptyRecipients) 0..20 else 1..20) || attachments.size > 20 || recipients.any { !isOutgoingNumber(it) }) return null
    return ExternalCompose(id, recipients.map(::Address).distinctBy { recipientKey(it.raw) }, text, attachments)
}
