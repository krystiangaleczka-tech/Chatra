package app.chatra.core.model

/** Konserwatywny numer wyjściowy: cyfry oraz wizualne separatory, opcjonalny prefiks + / 00. */
fun isOutgoingNumber(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.matches(Regex("\\+?[0-9 ()-]+")) && Address(trimmed).normalized.count { it.isDigit() } in 3..15
}

/** Jedna operacja na odbiorcę osobnego SMS; świadomie wybrany MMS to jedna operacja grupowa. */
fun Draft.newMessagePlan(): List<Draft> {
    if (!isSendable || recipients.any { !isOutgoingNumber(it.raw) }) return emptyList()
    val unique = recipients.distinctBy { it.normalized }
    return if (recipientMode == RecipientMode.GroupMms && unique.size > 1) {
        listOf(copy(recipients = unique))
    } else unique.map { copy(id = DraftId.new(), conversationId = null, recipients = listOf(it), recipientMode = RecipientMode.Single) }
}
