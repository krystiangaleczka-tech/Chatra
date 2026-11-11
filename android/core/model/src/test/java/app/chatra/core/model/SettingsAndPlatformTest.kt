package app.chatra.core.model

import org.junit.Assert.*
import org.junit.Test

class SettingsAndPlatformTest {
    @Test fun `price placeholder cannot appear in production or absent offers`() {
        assertTrue(proSummary(ProOffer.DemoPlaceholder, true).contains("[CENA]"))
        assertFalse(proSummary(ProOffer.DemoPlaceholder, false).contains("[CENA]"))
        assertTrue(proSummary(ProOffer.Unavailable, false).contains("niedostępna"))
        assertTrue(proSummary(ProOffer.Available("24,99 zł"), false).contains("24,99 zł"))
        assertThrows(IllegalArgumentException::class.java) { ProOffer.Available("[CENA]") }
    }
    @Test fun `SIM and organization summaries are derived from current data`() {
        val a = SimCard(SubscriptionId(4), 0, "Prywatna")
        val b = SimCard(SubscriptionId(9), 1, "Praca")
        assertEquals("SIM 1 Prywatna — domyślna · SIM 2 Praca", simSettingsSummary(listOf(a,b), a.id))
        assertEquals("SIM 2 Praca — domyślna", simSettingsSummary(listOf(a.copy(available=false),b), b.id))
        assertEquals("Brak dostępnych kart SIM", simSettingsSummary(emptyList(), null))
        assertEquals("3 numery", polishCount(3,"numer","numery","numerów"))
        assertEquals("12 numerów", polishCount(12,"numer","numery","numerów"))
        assertEquals("1 rozmowa", polishCount(1,"rozmowa","rozmowy","rozmów"))
        assertEquals("22 rozmowy", polishCount(22,"rozmowa","rozmowy","rozmów"))
    }
    @Test fun `role and permissions are independent and revoke access immediately`() {
        assertFalse(SmsAccessSnapshot(false,true,true).canRead)
        assertFalse(SmsAccessSnapshot(false,true,true).canDispatch)
        assertFalse(SmsAccessSnapshot(true,false,true).canRead)
        assertTrue(SmsAccessSnapshot(true,false,true).canDispatch)
        assertFalse(SmsAccessSnapshot(true,true,false).canDispatch)
    }
    @Test fun `external compose validates untrusted data without sending`() {
        val valid = externalCompose(listOf("+48 600 100 200", "+48600100200"), "Wiadomość\nDrugi wiersz", "test")!!
        assertEquals(1, valid.recipients.size)
        assertTrue(valid.text.contains('\n'))
        assertNull(externalCompose(listOf("Paczka24"), "treść", "test"))
        assertNull(externalCompose(listOf("+12+345"), "treść", "test"))
        assertNull(externalCompose(List(21) { "112" }, "treść", "test"))
        assertNull(externalCompose(listOf("112"), "x".repeat(16_385), "test"))
        assertNull(externalCompose(listOf("112"), "a\u0000b", "test"))
    }
}
