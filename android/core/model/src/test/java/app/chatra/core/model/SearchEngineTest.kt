package app.chatra.core.model

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SearchEngineTest {
    private fun hit(body: String, sender: String = "Bank", attachments: List<AttachmentSummary> = emptyList()) = SearchHit(
        Message(MessageRef.Provider(ProviderRef(ProviderTransport.SMS, 1)), ConversationId("a"), Direction.Incoming, Address(sender), body, Instant.parse("2026-10-25T01:30:00Z"), false, SubscriptionId(2), attachments = attachments), "Łódź")
    @Test fun foldingAndSourceOffsetsPreservePolishCombiningMarksCasefoldAndEmoji() {
        assertEquals("strasse lodz", searchFold("STRAẞE ŁÓDŹ"))
        assertEquals(listOf(3..8), searchTermRanges("📦 Z\u0307o\u0301łć", "zolc"))
        assertTrue(hit("Twoja PACZKA jest w Łodzi").matches(SearchQuery("pacz lodz")))
        assertFalse(hit("Przypadkowa spaczka").matches(SearchQuery("pacz")))
        assertTrue(hit("Kod 📦!").matches(SearchQuery("📦")))
    }
    @Test fun contextualCodeNeverTreatsAWholePhoneNumberAsAnOtpOrAuthenticatesSender() {
        assertTrue(recognizeQuick("Telefon: 600100200. Kod telefonu: +48 600100200").isEmpty())
        assertTrue(recognizeQuick("Kod: 123456789").isEmpty())
        assertTrue(recognizeQuick("Kod: 123456AB").isEmpty())
        val pattern = recognizeQuick("Kod odbioru: 482 915").single()
        assertEquals("482 915", pattern.value)
        assertEquals("482 915", "Kod odbioru: 482 915".substring(pattern.range))
        assertEquals(PatternConfidence.Contextual, pattern.confidence)
        assertTrue(hit("Kod: 123456", "Fałszywy Bank").matches(SearchQuery("", SearchFilter.Codes)))
        assertEquals("ZX123456789PL", recognizeQuick("Nr przesyłki: ZX123456789PL").single().value)
        assertTrue(recognizeQuick("Dowolna liczba: 123456").isEmpty())
        assertFalse(parcelContext("Automatyczny kod logowania: 123456"))
    }
    @Test fun everyFilterIsAndWithExclusiveEndAndSourceConversation() {
        val h = hit("Kod: 123456", "+48600100200", listOf(AttachmentSummary(AttachmentId("m"), "image/jpeg", "dom.jpg")))
        val q = SearchQuery("kod", SearchFilter.Codes, ConversationId("a"), Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-26T00:00:00Z"), "600100200", SearchContent.Images, SubscriptionId(2))
        assertTrue(h.matches(q)); assertFalse(h.matches(q.copy(sender = ConversationId("b"))))
        assertFalse(h.matches(q.copy(content = SearchContent.Videos))); assertFalse(h.matches(q.copy(subscriptionId = SubscriptionId(1))))
        assertFalse(h.matches(q.copy(until = h.message.timestamp)))
        assertFalse(h.copy(message = h.message.copy(direction = Direction.Outgoing)).matches(q))
    }
    @Test fun localDateRangeHonorsBothDstTransitions() {
        val zone = ZoneId.of("Europe/Warsaw")
        val spring = searchDateWindow(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29), zone)
        val fall = searchDateWindow(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), zone)
        assertEquals(23, Duration.between(spring.since, spring.until).toHours())
        assertEquals(25, Duration.between(fall.since, fall.until).toHours())
    }
}
