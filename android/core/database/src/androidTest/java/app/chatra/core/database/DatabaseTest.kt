package app.chatra.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun typedReferencesReconcileWithoutLosingMetadataOrMediaRelations() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.insertConversation(ConversationMeta("thread", 1, "+48123", "[\"+48123\"]"))
            dao.pin("thread", true)
            dao.preferSim("thread", 14, "eSIM Praca")
            dao.upsertReferences(listOf(
                ProviderMessageRef("SMS", 7, "thread", 1, true, false, "one"),
                ProviderMessageRef("MMS", 7, "thread", 2, true, false, "one"),
            ))
            dao.upsertMedia(listOf(MediaRef(13, messageId = 7, conversationId = "thread", mimeType = "image/jpeg", label = "zdjęcie", contentUri = "content://mms/part/13")))
            assertEquals(2, dao.page("thread", 200).size)
            dao.upsertReferences(listOf(ProviderMessageRef("SMS", 7, "thread", 1, true, true, "two")))
            dao.sweep("two")
            assertEquals(listOf("SMS"), dao.page("thread", 200).map { it.kind })
            assertTrue(dao.media("thread", 7).isEmpty())
            assertTrue(dao.conversation("thread")!!.pinned)
            assertEquals(14, dao.conversation("thread")!!.preferredSim)
        } finally { db.close() }
    }

    @Test fun operationIdentityClaimAndCallbacksAreIdempotent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        try {
            val dao = db.dao()
            val row = OutgoingOperation("op", "draft", null, "[\"123\"]", "test", "Single", 14, "eSIM", "PENDING_UNDO", 0, 1, 1)
            assertTrue(dao.insertOperation(row) != -1L)
            assertEquals(-1L, dao.insertOperation(row.copy(id = "duplicate")))
            assertEquals(1, dao.transition("op", "PENDING_UNDO", 0, "DISPATCHING"))
            assertEquals(0, dao.transition("op", "PENDING_UNDO", 0, "DISPATCHING"))
            dao.attempt(OutgoingAttempt("try", "op", "123", 14, "eSIM", 1, "nonce", 1))
            dao.parts(listOf(OutgoingPart("try", 0)))
            dao.delivered("try", 0, "Delivered")
            dao.delivered("try", 0, "Failed")
            dao.sent("try", 0, "Sent", -1)
            dao.sent("try", 0, "Failed", 1)
            assertEquals("Delivered", dao.parts("try").single().delivery)
            assertEquals("Sent", dao.parts("try").single().sent)
        } finally { db.close() }
    }

    @Test fun migrationOneToTwoPreservesExistingConversationPreferences() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("migration-v1.sql").bufferedReader().use { it.readText() }
        schema.lineSequence().filterNot { it.startsWith("--") }.joinToString("\n").split(';').filter { it.isNotBlank() }.forEach { legacy.execSQL(it) }
        legacy.execSQL("INSERT INTO ConversationMeta(id,threadId,participantsKey,participants,pinned,trashed) VALUES ('thread',1,'123','[\"123\"]',1,0)")
        legacy.version = 1
        legacy.close()
        val db = Room.databaseBuilder(context, ChatraDatabase::class.java, name).addMigrations(ChatraDatabase.MIGRATION_1_2).build()
        try {
            val row = db.dao().conversation("thread")!!
            assertTrue(row.pinned)
            assertFalse(row.muted)
            assertFalse(row.manualUnread)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
