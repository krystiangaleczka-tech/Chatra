package app.chatra.core.model

import java.util.UUID

/** Lokalny identyfikator rozmowy Chatry (UUID); nie jest ID wątku providera. */
@JvmInline value class ConversationId(val value: String) {
    companion object { fun new() = ConversationId(UUID.randomUUID().toString()) }
}

@JvmInline value class OperationId(val value: String) {
    companion object { fun new() = OperationId(UUID.randomUUID().toString()) }
}

@JvmInline value class AttemptId(val value: String) {
    companion object { fun new() = AttemptId(UUID.randomUUID().toString()) }
}

@JvmInline value class DraftId(val value: String) {
    companion object { fun new() = DraftId(UUID.randomUUID().toString()) }
}

@JvmInline value class ScheduleId(val value: String) {
    companion object { fun new() = ScheduleId(UUID.randomUUID().toString()) }
}

@JvmInline value class AttachmentId(val value: String) {
    companion object { fun new() = AttachmentId(UUID.randomUUID().toString()) }
}

@JvmInline value class TemplateId(val value: String)

/** subscriptionId z SubscriptionManager; etykieta „SIM 1” nie identyfikuje karty trwale. */
@JvmInline value class SubscriptionId(val value: Int)

enum class ProviderTransport { SMS, MMS }

/** Rekord historii w Telephony Provider: te same liczby mogą istnieć w tabelach SMS i MMS. */
data class ProviderRef(val transport: ProviderTransport, val providerId: Long) {
    val key: String get() = "${transport.name.lowercase()}:$providerId"
}
