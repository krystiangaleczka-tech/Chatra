package app.chatra.core.model

/** Konserwatywny numer wyjściowy: cyfry oraz wizualne separatory, opcjonalny prefiks + / 00. */
fun isOutgoingNumber(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.matches(Regex("\\+?[0-9 ()-]+")) && Address(trimmed).normalized.count { it.isDigit() } in 3..15
}

/**
 * Klucz deduplikacji dla polskich numerów. Lokalny format 9 cyfr i jawny +48/0048
 * wskazują ten sam numer; format przekazany do SmsManager pozostaje bez zmian.
 * Nie zgadujemy kraju dla krótkich kodów ani numerów z innym prefiksem kraju.
 */
fun outgoingRecipientKey(address: Address): String {
    val normalized = address.normalized
    return if (normalized.length == 9 && normalized.all(Char::isDigit)) "+48$normalized" else normalized
}

/** Jedna operacja na odbiorcę osobnego SMS; świadomie wybrany MMS to jedna operacja grupowa. */
fun Draft.newMessagePlan(): List<Draft> {
    if (!isSendable || recipients.any { !isOutgoingNumber(it.raw) }) return emptyList()
    val unique = recipients.distinctBy(::outgoingRecipientKey)
    return if (recipientMode == RecipientMode.GroupMms && unique.size > 1) {
        listOf(copy(recipients = unique))
    } else unique.map { copy(id = DraftId.new(), conversationId = null, recipients = listOf(it), recipientMode = RecipientMode.Single) }
}
