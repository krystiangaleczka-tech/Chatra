package app.chatra.core.model

import app.chatra.core.model.SmsCounter.Encoding
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsCounterTest {
    @Test fun polishCharacterSwitchesToUcs2() {
        // Wzorzec S03 pokazuje statyczne „21/160”; poprawnie: 20 znaków z „ę” → UCS-2.
        val c = SmsCounter.count("Kupię kwiaty dla Ani")
        assertEquals(Encoding.Ucs2, c.encoding)
        assertEquals("20/70 · 1 SMS", c.label)
    }

    @Test fun gsmBoundaries() {
        assertEquals("160/160 · 1 SMS", SmsCounter.count("a".repeat(160)).label)
        val two = SmsCounter.count("a".repeat(161))
        assertEquals(2, two.segments)
        assertEquals("8/153 · 2 SMS", two.label)
    }

    @Test fun extendedCharactersCountDouble() {
        assertEquals(2, SmsCounter.count("€").units)
        assertEquals(Encoding.Gsm7, SmsCounter.count("cena 5€ [x]").encoding)
    }

    @Test fun emojiUsesTwoUtf16Units() {
        val c = SmsCounter.count("ok 👍")
        assertEquals(Encoding.Ucs2, c.encoding)
        assertEquals(5, c.units)
        assertEquals(4, c.visibleChars)
    }

    @Test fun ucs2Multipart() {
        val c = SmsCounter.count("ż".repeat(71))
        assertEquals(2, c.segments)
        assertEquals(67, c.perSegment)
    }

    @Test fun transliterationKeepsOriginalSeparate() {
        assertEquals("Kupie kwiaty dla Ani", SmsCounter.transliteratePolish("Kupię kwiaty dla Ani"))
        assertEquals(Encoding.Gsm7, SmsCounter.count(SmsCounter.transliteratePolish("Zażółć gęślą jaźń")).encoding)
    }

    @Test fun empty() {
        assertEquals(0, SmsCounter.count("").segments)
    }
}
