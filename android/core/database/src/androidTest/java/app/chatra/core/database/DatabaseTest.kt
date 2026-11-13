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

    @Test fun migrationOneToThreePreservesExistingConversationPreferences() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("migration-v1.sql").bufferedReader().use { it.readText() }
        schema.lineSequence().filterNot { it.startsWith("--") }.joinToString("\n").split(';').filter { it.isNotBlank() }.forEach { legacy.execSQL(it) }
        legacy.execSQL("INSERT INTO ConversationMeta(id,threadId,participantsKey,participants,pinned,trashed) VALUES ('thread',1,'123','[\"123\"]',1,0)")
        legacy.execSQL("INSERT INTO OutgoingOperation(id,draftKey,conversationId,recipients,body,mode,subscriptionId,subscriptionLabel,state,version,createdAt,undoUntil,bootCount) VALUES ('old','old-draft',NULL,'[\"123\"]','legacy','Single',1,'SIM','PENDING_UNDO',0,1,2,1)")
        legacy.execSQL("INSERT INTO SavedDraft(id,conversationId,payload) VALUES ('draft',NULL,'legacy-payload')")
        legacy.execSQL("INSERT INTO OutgoingOperation(id,draftKey,conversationId,recipients,body,mode,subscriptionId,subscriptionLabel,state,version,createdAt,undoUntil,bootCount) VALUES ('failure','failure-draft','thread','[\"123\"]','uncertain','Single',1,'SIM','FAILED',0,1,2,1)")
        legacy.execSQL("INSERT INTO OutgoingRecipient(operationId,address,conversationId,state,providerId) VALUES ('failure','123','thread','FAILED',42)")
        legacy.execSQL("INSERT INTO OutgoingAttempt(id,operationId,address,subscriptionId,subscriptionLabel,startedAt,callbackToken,partCount) VALUES ('attempt','failure','123',1,'SIM',1,'token',1)")
        legacy.execSQL("INSERT INTO OutgoingPart(attemptId,partIndex,sent,delivery,resultCode) VALUES ('attempt',0,'Failed','Unknown',1)")
        legacy.version = 1
        legacy.close()
        val db = Room.databaseBuilder(context, ChatraDatabase::class.java, name).addMigrations(ChatraDatabase.MIGRATION_1_2, ChatraDatabase.MIGRATION_2_3, ChatraDatabase.MIGRATION_3_4, ChatraDatabase.MIGRATION_4_5, ChatraDatabase.MIGRATION_5_6, ChatraDatabase.MIGRATION_6_7).build()
        try {
            val row = db.dao().conversation("thread")!!
            assertTrue(row.pinned)
            assertFalse(row.muted)
            assertFalse(row.manualUnread)
            assertEquals("legacy", db.dao().operation("old")!!.body)
            assertNull(db.dao().operation("old")!!.undoElapsed)
            assertEquals("legacy-payload", db.dao().savedDraft("draft")!!.payload)
            assertEquals("DISPATCH_UNCERTAIN", db.dao().operation("failure")!!.state)
            assertEquals("Unknown", db.dao().parts("attempt").single().sent)
            assertEquals("SMS", db.dao().recipients("failure").single().providerKind)
            assertEquals(42L, db.dao().attempt("attempt")!!.providerId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun allExportedVersionsMigrateWithPinnedMutedUnreadAndSourceRelations() = runBlocking {
        for (version in 2..6) {
            val name = "migration-from-$version.db"
            context.deleteDatabase(name)
            val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null)
            val json = InstrumentationRegistry.getInstrumentation().context.assets
                .open("app.chatra.core.database.ChatraDatabase/$version.json").bufferedReader().use { org.json.JSONObject(it.readText()).getJSONObject("database") }
            val entities = json.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            legacy.execSQL("INSERT INTO ConversationMeta(id,threadId,participantsKey,participants,pinned,muted,manualUnread,trashed) VALUES ('thread',1,'123','[\"123\"]',1,1,1,0)")
            legacy.execSQL("INSERT INTO ProviderMessageRef VALUES ('MMS',7,'thread',20,1,0,'seed')")
            legacy.execSQL("INSERT INTO MediaRef VALUES (9,'MMS',7,'thread','image/jpeg','photo','content://mms/part/9')")
            legacy.version = version; legacy.close()
            val db = Room.databaseBuilder(context, ChatraDatabase::class.java, name).addMigrations(
                ChatraDatabase.MIGRATION_2_3, ChatraDatabase.MIGRATION_3_4, ChatraDatabase.MIGRATION_4_5,
                ChatraDatabase.MIGRATION_5_6, ChatraDatabase.MIGRATION_6_7).build()
            try {
                val row = db.dao().conversation("thread")!!
                assertTrue(row.pinned && row.muted && row.manualUnread)
                assertEquals(7L, db.dao().mediaPart(9)!!.messageId)
                db.dao().upsertMedia(listOf(MediaRef(9, messageId = 7, conversationId = "thread", mimeType = "image/jpeg", label = "photo", contentUri = "content://mms/part/9")))
                assertEquals(1, db.dao().galleryCount("thread", ""))
                val hd = HdRecord("hd", "owner", "thread", "+12025550101", "+12025550102", true, 30, 40, "[]")
                db.hd().put(hd); db.hd().put(hd)
                assertEquals(1, db.hd().all().size)
                db.hd().shown("thread", listOf("hd")); assertTrue(db.hd().get("hd")!!.read)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

}
