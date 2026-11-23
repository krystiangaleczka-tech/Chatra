package app.chatra.ui

import app.chatra.core.model.*
import app.chatra.ui.conversation.selectedTranscript
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ConversationExportTest {
    @Test fun transcriptPreservesOrderAndOnlyAddsExplicitMetadata() {
        val id = ConversationId("private-thread-id")
        val first = Message(MessageRef.Provider(ProviderRef(ProviderTransport.SMS, 10)), id,
            Direction.Incoming, Address("+48600100100"), "Zażółć", Instant.EPOCH, true)
        val second = first.copy(ref = MessageRef.Provider(ProviderRef(ProviderTransport.MMS, 11)), direction = Direction.Outgoing,
            body = "", attachments = listOf(AttachmentSummary(AttachmentId("private-attachment-id"), "image/png", "private.png")))
        val selection = listOf(first, second)
        assertEquals("Zażółć\n\n[Wiadomość z załącznikami]", selectedTranscript(selection, false, ZoneOffset.UTC))
        val detailed = selectedTranscript(selection, true, ZoneOffset.UTC)
        assertTrue(detailed.startsWith("01.01.1970 00:00 +00:00 · Odebrana\nZażółć"))
        assertTrue(detailed.contains("· Wysłana"))
        listOf("+48600100100", id.value, "private-attachment-id", "private.png").forEach { assertFalse(detailed.contains(it)) }
    }
}
