package app.chatra.core.model

import org.junit.Assert.*
import org.junit.Test

class LocalMediaTest {
    @Test fun `template appends without replacing composer text`() {
        assertEquals("Dziękuję", appendTemplate("", "Dziękuję"))
        assertEquals("Adres: A Dziękuję", appendTemplate("Adres: A", "Dziękuję"))
    }
    @Test fun `timed mute expires exactly at deadline and does not mute another conversation`() {
        val prefs = NotificationPrefs(mutedUntil = mapOf("a" to 1000, "forever" to Long.MAX_VALUE))
        assertTrue(prefs.muted("a", 999)); assertFalse(prefs.muted("a", 1000))
        assertFalse(prefs.muted("b", 999)); assertTrue(prefs.muted("forever", 100000))
    }
    @Test fun `shared media can open an empty recipient editor but cannot bypass recipient validation`() {
        assertNotNull(externalCompose(emptyList(), "", "qa", listOf(AttachmentId("id")), allowEmptyRecipients = true))
        assertNull(externalCompose(listOf("BANK"), "text", "qa", allowEmptyRecipients = true))
        assertNull(externalCompose(List(21) { "+48555000320" }, "text", "qa"))
        assertNull(externalCompose(emptyList(), "x".repeat(16385), "qa", allowEmptyRecipients = true))
    }
}
