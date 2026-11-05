package app.chatra.core.model

import java.time.Instant

/** Wiersz skrzynki (S02). Liczby i podglądy wynikają ze stanu, nie są stałymi. */
data class ConversationSummary(
    val id: ConversationId,
    val participants: List<Participant>,
    val title: String,
    val lastActivity: Instant,
    val preview: Preview,
    val unreadCount: Int,
    val pinned: Boolean,
    val isGroupMms: Boolean = false,
    val tags: Set<Tag> = emptySet(),
    val muted: Boolean = false,
    val trashed: Boolean = false,
    val blocked: Boolean = false,
    /** Odbiorca ma Chatrę i przyjmuje HD (wynik rejestru HD, CH047). */
    val hdCapable: Boolean = false,
) {
    val isUnread: Boolean get() = unreadCount > 0
    val isUnknownSender: Boolean get() = Tag.UnknownNumber in tags

    enum class Tag { Code, UnknownNumber }

    /** Szkic ma pierwszeństwo w podglądzie; błąd wysyłki pozostaje widoczny jako status (S02). */
    sealed interface Preview {
        data class Text(val text: String, val senderPrefix: String? = null) : Preview
        data class Draft(val text: String) : Preview
        data object SendFailed : Preview
    }
}

/** Kolejność skrzynki: przypięte przed pozostałymi, w grupach malejąco po aktywności. */
fun List<ConversationSummary>.inboxOrder(): List<ConversationSummary> =
    filterNot { it.trashed }.sortedWith(compareByDescending<ConversationSummary> { it.pinned }.thenByDescending { it.lastActivity })

/** „Nieprzeczytane · N” liczy rozmowy z nieprzeczytaną treścią, nie sumę wiadomości (S02). */
fun List<ConversationSummary>.unreadConversationCount(): Int = count { it.isUnread && !it.trashed }
