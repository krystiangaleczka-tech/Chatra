package app.chatra.core.testing

import app.chatra.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SearchAndComposeTest {
    @Test fun `S07 finds four messages and two independent clipboard values`() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope, frozen = true)
        val query = SearchQuery("PACZK", SearchFilter.Parcels)
        val result = repo.search(query).first()
        assertEquals(4, result.hits.size)
        assertEquals(listOf("482 915", "KRA01M · ul. Długa 14"), result.quick.map { it.value })
        assertEquals(1, repo.search(query.copy(filter = SearchFilter.Codes)).first().hits.size)
        assertEquals(0, repo.search(query.copy(filter = SearchFilter.Attachments)).first().hits.size)
        assertEquals(1, repo.search(query.copy(sender = Fixture.Ids.Ola)).first().hits.size)
        assertEquals(2, repo.search(query.copy(since = FixedClock().now().minusSeconds(86400))).first().hits.size)
        assertEquals(5, repo.search(query.copy(text = "")).first().hits.size) // Filters also work without a text term (CH039).
        repo.moveToTrash(Fixture.Ids.Paczka24)
        val after = repo.search(query).first()
        assertEquals(2, after.hits.size)
        assertTrue(after.quick.isEmpty())
    }

    @Test fun `three separate sends create single recipient SMS and group text is MMS`() = runTest {
        val repo = InMemoryChatra(FixedClock(), backgroundScope, frozen = true)
        val draft = Draft(DraftId.new(), null, Fixture.newMessageRecipients.map { it.address }, "Spotkanie jutro", RecipientMode.SeparateSms, emptyList(), Fixture.SIM1.id, FixedClock().now())
        val ids = draft.newMessagePlan().map { repo.enqueue(it, 3) }
        val ops = repo.operations().first().filter { it.id in ids }
        assertEquals(3, ops.size)
        assertEquals(3, ops.map { it.conversationId }.distinct().size)
        assertTrue(ops.all { it.transport == Transport.SMS && it.recipients.size == 1 })
        val group = repo.enqueue(draft.copy(recipientMode = RecipientMode.GroupMms).newMessagePlan().single(), 3)
        val groupOp = repo.operations().first().single { it.id == group }
        assertEquals(Transport.MMS, groupOp.transport)
        assertEquals(3, groupOp.recipients.size)
    }
}
