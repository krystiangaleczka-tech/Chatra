package app.chatra.core.model

import org.junit.Assert.*
import org.junit.Test

class RecipientIdentityTest {
    @Test fun polishLocalAndInternationalFormsShareIdentityWithoutRewritingForeignNumbers() {
        assertEquals(recipientKey("600 100 200"), recipientKey("+48 600-100-200"))
        assertEquals(recipientKey("0048600100200"), recipientKey("600100200"))
        assertNotEquals(recipientKey("+49600100200"), recipientKey("600100200"))
        assertEquals("600100200", recipientKey("600100200", "DE"))
        assertEquals(participantsKey(listOf(Address("600100200"))), participantsKey(listOf(Address("+48600100200"))))
    }
}
