package app.chatra.core.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SearchAndRecipientsTest {
    @Test fun `Polish case and diacritics fold while ranges point to original text`() {
        assertEquals("zolc lodz", searchFold("ŻÓŁĆ Łódź"))
        assertEquals(listOf(0..4, 7..11), searchRanges("PACZKę paczkA", "paczk"))
        assertEquals(listOf(0..5), searchRanges("Z\u0307o\u0301łć", "zolc"))
        assertTrue(searchRanges("abc", "").isEmpty())
    }

    @Test fun `outgoing number rejects alphabetic malformed and empty recipients`() {
        listOf("", "Paczka24", "123x", "+12+345", "++123", "12", "1234567890123456").forEach { assertFalse(it, isOutgoingNumber(it)) }
        listOf("+48 600 100 200", "0048 600-100-200", "112", "(600) 100 200").forEach { assertTrue(it, isOutgoingNumber(it)) }
    }

    private fun draft(mode: RecipientMode) = Draft(DraftId.new(), null, listOf(Address("+48 600 100 200"), Address("600100200"), Address("+48 605 300 400")), "Cześć", mode, emptyList(), SubscriptionId(1), Instant.EPOCH)

    @Test fun `separate SMS has unique recipients in separate operations and group MMS stays one`() {
        val separate = draft(RecipientMode.SeparateSms).newMessagePlan()
        assertEquals(2, separate.size)
        assertTrue(separate.all { it.recipients.size == 1 && it.recipientMode == RecipientMode.Single })
        assertEquals(2, separate.map { it.id }.distinct().size)
        val group = draft(RecipientMode.GroupMms).newMessagePlan().single()
        assertEquals(2, group.recipients.size)
        assertEquals(RecipientMode.GroupMms, group.recipientMode)
        assertTrue(group.copy(text = "").newMessagePlan().isEmpty())
        assertTrue(group.copy(recipients = listOf(Address("Paczka24"))).newMessagePlan().isEmpty())
    }
}
