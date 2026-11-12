package app.chatra.di

import app.chatra.core.model.ConversationId
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class OrganizationPreviewTest {
    @Test fun `demo restore and unblock change counts idempotently`() = runBlocking {
        val store = MemoryOrganizationPreview(true, listOf(BlockedPreview("a", "Numer")), listOf(TrashPreview(ConversationId("t"), "Rozmowa")))
        store.unblock("a"); store.unblock("a")
        assertTrue(store.blocked.value.isEmpty())
        store.restore(ConversationId("t")); store.restore(ConversationId("t"))
        assertTrue(store.trash.value.isEmpty())
        store.addToTrash(ConversationId("t2"), "Druga"); store.addToTrash(ConversationId("t2"), "Druga")
        assertEquals(1, store.trash.value.size)
    }
    @Test fun `read-only production preview cannot pretend to alter history`() = runBlocking {
        val store = MemoryOrganizationPreview(false)
        store.addToTrash(ConversationId("t"), "Rozmowa")
        assertTrue(store.trash.value.isEmpty())
        assertFalse(store.editable)
    }
}
