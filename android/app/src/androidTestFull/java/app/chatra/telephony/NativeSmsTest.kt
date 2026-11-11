package app.chatra.telephony

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.model.SmsCounter
import app.chatra.platform.parseComposeIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeSmsTest {
    private val analyzer get() = PlatformSmsText(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun asciiBoundariesUsePlatformParts() {
        assertEquals(1, analyzer.count("a".repeat(160), null).segments)
        val count = analyzer.count("a".repeat(161), null)
        assertEquals(2, count.segments)
        assertEquals(161, count.units)
        assertEquals(8, count.lastSegmentUnits)
    }
    @Test fun extensionAlphabetConsumesTwoSeptets() {
        assertEquals(1, analyzer.count("^".repeat(80), null).segments)
        assertEquals(2, analyzer.count("^".repeat(81), null).segments)
        assertEquals(162, analyzer.count("^".repeat(81), null).units)
    }
    @Test fun polishSourceHasTwentyUnitsAndIsNeverChanged() {
        val source = "Kupię kwiaty dla Ani"
        val count = analyzer.count(source, null)
        assertEquals(SmsCounter.Encoding.Ucs2, count.encoding)
        assertEquals(20, count.units)
        assertEquals("20/70 · 1 SMS", count.label)
        assertEquals("Kupię kwiaty dla Ani", source)
        assertEquals("Kupie kwiaty dla Ani", SmsCounter.transliteratePolish(source))
    }
    @Test fun emojiUseUtf16AndDoNotSplitSurrogates() {
        assertEquals(1, analyzer.count("😀".repeat(35), null).segments)
        val count = analyzer.count("😀".repeat(36), null)
        assertEquals(2, count.segments)
        assertEquals(72, count.units)
        assertEquals(36, count.visibleChars)
        assertEquals(6, count.lastSegmentUnits)
    }
    @Test fun emptyTextDoesNotBecomeOneSms() { assertEquals(0, analyzer.count("", null).segments) }
    @Test fun sendToValidatesUntrustedInputAndOpaqueQuery() {
        assertNull(parseComposeIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
        assertNull(parseComposeIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:Paczka24"))))
        val draft = parseComposeIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:%2B48600100200?body=Za%C5%BC%C3%B3%C5%82%C4%87")))!!
        assertEquals("+48600100200", draft.recipients.single().raw)
        assertEquals("Zażółć", draft.text)
        assertNull(parseComposeIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("sms:123")).putExtra("sms_body", "a".repeat(16385))))
    }
}
