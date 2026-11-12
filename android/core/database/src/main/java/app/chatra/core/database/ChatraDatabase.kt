package app.chatra.core.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Metadane lokalne. Treść kanonicznych SMS/MMS pozostaje wyłącznie w providerze. */
@Entity(indices = [Index(value = ["threadId", "participantsKey"], unique = true)])
data class ConversationMeta(
    @PrimaryKey val id: String,
    val threadId: Long,
    val participantsKey: String,
    val participants: String,
    val pinned: Boolean = false,
    val preferredSim: Int? = null,
    val preferredSimLabel: String? = null,
    val trashed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val muted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val manualUnread: Boolean = false,
)

@Entity(primaryKeys = ["kind", "providerId"], indices = [Index("conversationId")], foreignKeys = [
    ForeignKey(entity = ConversationMeta::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE),
])
data class ProviderMessageRef(
    val kind: String, val providerId: Long, val conversationId: String,
    val timestamp: Long, val incoming: Boolean, val read: Boolean, val generation: String,
)

@Entity(indices = [Index(value = ["messageKind", "messageId"]), Index("conversationId")], foreignKeys = [
    ForeignKey(entity = ProviderMessageRef::class, parentColumns = ["kind", "providerId"], childColumns = ["messageKind", "messageId"], onDelete = ForeignKey.CASCADE),
])
data class MediaRef(
    @PrimaryKey val partId: Long, val messageKind: String = "MMS", val messageId: Long,
    val conversationId: String, val mimeType: String, val label: String, val contentUri: String,
)

/** Dziennik wejścia zamyka zwykłe duplikaty; writing wymaga rekoncyliacji po awarii. */
@Entity data class InboundReceipt(@PrimaryKey val fingerprint: String, val state: String, val baselineId: Long, val providerId: Long? = null)

@Entity(indices = [Index(value = ["draftKey"], unique = true), Index(value = ["retryOf"], unique = true)])
data class OutgoingOperation(
    @PrimaryKey val id: String, val draftKey: String, val conversationId: String?, val recipients: String,
    val body: String, val mode: String, val subscriptionId: Int, val subscriptionLabel: String,
    val state: String, val version: Int, val createdAt: Long, val undoUntil: Long, @ColumnInfo(defaultValue = "0") val bootCount: Int = 0,
    val undoElapsed: Long? = null, val snapshot: String? = null, val retryOf: String? = null,
)
@Entity(primaryKeys = ["operationId", "address"], indices = [Index("operationId")], foreignKeys = [
    ForeignKey(entity = OutgoingOperation::class, parentColumns = ["id"], childColumns = ["operationId"], onDelete = ForeignKey.CASCADE),
])
data class OutgoingRecipient(val operationId: String, val address: String, val conversationId: String, val state: String, val providerId: Long? = null, @ColumnInfo(defaultValue = "'SMS'") val providerKind: String = "SMS")

@Entity(indices = [Index("operationId")], foreignKeys = [
    ForeignKey(entity = OutgoingOperation::class, parentColumns = ["id"], childColumns = ["operationId"], onDelete = ForeignKey.CASCADE),
])
data class OutgoingAttempt(
    @PrimaryKey val id: String, val operationId: String, val address: String, val subscriptionId: Int,
    val subscriptionLabel: String, val startedAt: Long, val callbackToken: String, val partCount: Int,
    @ColumnInfo(defaultValue = "'LEGACY_UNKNOWN'") val phase: String = "LEGACY_UNKNOWN",
    val providerId: Long? = null,
)
@Entity(primaryKeys = ["attemptId", "partIndex"], indices = [Index("attemptId")], foreignKeys = [
    ForeignKey(entity = OutgoingAttempt::class, parentColumns = ["id"], childColumns = ["attemptId"], onDelete = ForeignKey.CASCADE),
])
data class OutgoingPart(val attemptId: String, val partIndex: Int, val sent: String = "Pending", val delivery: String = "Unknown", val resultCode: Int? = null)

@Entity data class SavedDraft(@PrimaryKey val id: String, val conversationId: String?, val payload: String,
    @ColumnInfo(defaultValue = "0") val revision: Long = 0, @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0)
@Entity data class ClosedDraft(@PrimaryKey val id: String)

data class ConversationIndex(
    @Embedded val meta: ConversationMeta, val lastAt: Long?, val unreadCount: Int,
    val lastKind: String?, val lastId: Long?,
)

@Dao
interface ChatraDao {
    @Query("SELECT c.*, MAX(r.timestamp) AS lastAt, SUM(CASE WHEN r.incoming=1 AND r.read=0 THEN 1 ELSE 0 END) AS unreadCount, (SELECT kind FROM ProviderMessageRef WHERE conversationId=c.id ORDER BY timestamp DESC, providerId DESC LIMIT 1) AS lastKind, (SELECT providerId FROM ProviderMessageRef WHERE conversationId=c.id ORDER BY timestamp DESC, providerId DESC LIMIT 1) AS lastId FROM ConversationMeta c LEFT JOIN ProviderMessageRef r ON r.conversationId=c.id GROUP BY c.id")
    fun indices(): Flow<List<ConversationIndex>>
    @Query("SELECT COUNT(*) FROM ProviderMessageRef") fun referenceChanges(): Flow<Int>
    @Query("SELECT COUNT(*) FROM ProviderMessageRef WHERE conversationId=:id") fun messageCount(id: String): Flow<Int>
    @Query("SELECT * FROM ConversationMeta WHERE id=:id") fun meta(id: String): Flow<ConversationMeta?>
    @Query("SELECT * FROM ConversationMeta") fun conversations(): Flow<List<ConversationMeta>>
    @Query("SELECT * FROM ConversationMeta WHERE id=:id") suspend fun conversation(id: String): ConversationMeta?
    @Query("SELECT * FROM ConversationMeta WHERE threadId=:thread AND participantsKey=:participants LIMIT 1") suspend fun findConversation(thread: Long, participants: String): ConversationMeta?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertConversation(row: ConversationMeta): Long
    @Query("UPDATE ConversationMeta SET pinned=:value WHERE id=:id") suspend fun pin(id: String, value: Boolean)
    @Query("UPDATE ConversationMeta SET manualUnread=:value WHERE id=:id") suspend fun unread(id: String, value: Boolean)
    @Query("UPDATE ConversationMeta SET manualUnread=0") suspend fun clearManualUnread()
    @Query("UPDATE ConversationMeta SET preferredSim=:sim, preferredSimLabel=:label WHERE id=:id") suspend fun preferSim(id: String, sim: Int?, label: String?)
    @Query("UPDATE ConversationMeta SET trashed=:value WHERE id=:id") suspend fun trash(id: String, value: Boolean)

    @Query("SELECT * FROM ProviderMessageRef") fun references(): Flow<List<ProviderMessageRef>>
    @Query("SELECT * FROM ProviderMessageRef WHERE conversationId=:id ORDER BY timestamp DESC, kind, providerId DESC LIMIT :limit") suspend fun page(id: String, limit: Int): List<ProviderMessageRef>
    @Query("SELECT * FROM ProviderMessageRef WHERE conversationId=:id AND read=0") suspend fun unreadRefs(id: String): List<ProviderMessageRef>
    @Upsert suspend fun upsertReferences(rows: List<ProviderMessageRef>)
    @Query("DELETE FROM ProviderMessageRef WHERE generation != :generation") suspend fun sweep(generation: String)
    @Upsert suspend fun upsertMedia(rows: List<MediaRef>)
    @Query("DELETE FROM MediaRef WHERE messageId=:id AND partId NOT IN (:parts)") suspend fun sweepParts(id: Long, parts: List<Long>)
    @Query("SELECT * FROM MediaRef WHERE conversationId=:id AND messageId=:message") suspend fun media(id: String, message: Long): List<MediaRef>
    @Query("SELECT * FROM MediaRef WHERE conversationId=:id") fun conversationMedia(id: String): Flow<List<MediaRef>>
    @Query("SELECT * FROM MediaRef WHERE conversationId=:id AND (:kind='' OR mimeType LIKE :kind) AND (:before IS NULL OR partId<:before) ORDER BY partId DESC LIMIT :limit") suspend fun galleryPage(id: String, kind: String, before: Long?, limit: Int): List<MediaRef>
    @Query("SELECT COUNT(*) FROM MediaRef WHERE conversationId=:id AND (:kind='' OR mimeType LIKE :kind)") suspend fun galleryCount(id: String, kind: String): Int
    @Query("SELECT * FROM MediaRef WHERE partId=:id") suspend fun mediaPart(id: Long): MediaRef?


    @Query("SELECT * FROM InboundReceipt WHERE fingerprint=:key") suspend fun receipt(key: String): InboundReceipt?
    @Upsert suspend fun receipt(row: InboundReceipt)
    @Query("SELECT * FROM OutgoingOperation ORDER BY createdAt DESC") fun operations(): Flow<List<OutgoingOperation>>
    @Query("SELECT * FROM OutgoingOperation WHERE id=:id") suspend fun operation(id: String): OutgoingOperation?
    @Query("SELECT * FROM OutgoingOperation WHERE draftKey=:key") suspend fun operationForDraft(key: String): OutgoingOperation?
    @Query("SELECT * FROM OutgoingOperation WHERE state IN ('PENDING_UNDO','DISPATCHING')") suspend fun recoverable(): List<OutgoingOperation>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOperation(row: OutgoingOperation): Long
    @Query("UPDATE OutgoingOperation SET state=:next, version=version+1 WHERE id=:id AND state=:expected AND version=:version") suspend fun transition(id: String, expected: String, version: Int, next: String): Int
    @Query("UPDATE OutgoingOperation SET state=:state, version=version+1 WHERE id=:id AND state!='CANCELLED' AND state!='DELIVERED' AND (state!='SENT' OR :state IN ('SENT','DELIVERED'))") suspend fun result(id: String, state: String)
    @Upsert suspend fun recipient(row: OutgoingRecipient)
    @Query("SELECT * FROM OutgoingRecipient WHERE operationId=:id") suspend fun recipients(id: String): List<OutgoingRecipient>
    @Query("SELECT * FROM OutgoingRecipient WHERE conversationId=:id") fun conversationRecipients(id: String): Flow<List<OutgoingRecipient>>
    @Query("SELECT * FROM OutgoingRecipient WHERE providerId IN (:ids)") suspend fun outgoingLinks(ids: List<Long>): List<OutgoingRecipient>
    @Query("SELECT * FROM OutgoingOperation WHERE retryOf=:id LIMIT 1") suspend fun retryChild(id: String): OutgoingOperation?
    @Insert suspend fun attempt(row: OutgoingAttempt)
    @Query("UPDATE OutgoingAttempt SET phase=:phase WHERE id=:id") suspend fun phase(id: String, phase: String)
    @Query("UPDATE OutgoingAttempt SET providerId=:provider WHERE id=:id") suspend fun attemptProvider(id: String, provider: Long)
    @Query("SELECT * FROM OutgoingAttempt WHERE id=:id") suspend fun attempt(id: String): OutgoingAttempt?
    @Query("SELECT * FROM OutgoingAttempt WHERE operationId=:id") suspend fun attempts(id: String): List<OutgoingAttempt>
    @Insert suspend fun parts(rows: List<OutgoingPart>)
    @Query("SELECT * FROM OutgoingPart WHERE attemptId=:id ORDER BY partIndex") suspend fun parts(id: String): List<OutgoingPart>
    @Query("UPDATE OutgoingPart SET sent=:state, resultCode=:code WHERE attemptId=:id AND partIndex=:part AND sent!='Sent' AND (:state='Sent' OR sent IN ('Pending','Unknown'))") suspend fun sent(id: String, part: Int, state: String, code: Int)
    @Query("UPDATE OutgoingPart SET delivery=:state WHERE attemptId=:id AND partIndex=:part AND delivery!='Delivered'") suspend fun delivered(id: String, part: Int, state: String)
    @Query("SELECT * FROM SavedDraft WHERE conversationId=:id ORDER BY updatedAt DESC, id LIMIT 1") fun draft(id: String): Flow<SavedDraft?>
    @Query("SELECT * FROM SavedDraft ORDER BY updatedAt DESC, id") fun drafts(): Flow<List<SavedDraft>>
    @Query("SELECT * FROM SavedDraft WHERE id=:id") suspend fun savedDraft(id: String): SavedDraft?
    @Query("SELECT COUNT(*) FROM ClosedDraft WHERE id=:id") suspend fun closedDraft(id: String): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun closeDraft(row: ClosedDraft)
    @Upsert suspend fun saveDraft(row: SavedDraft)
    @Query("DELETE FROM SavedDraft WHERE id=:id") suspend fun deleteDraft(id: String)
}

@Database(entities = [ConversationMeta::class, ProviderMessageRef::class, MediaRef::class, InboundReceipt::class,
    OutgoingOperation::class, OutgoingRecipient::class, OutgoingAttempt::class, OutgoingPart::class, SavedDraft::class, ClosedDraft::class], version = 4, exportSchema = true)
abstract class ChatraDatabase : RoomDatabase() {
    abstract fun dao(): ChatraDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ConversationMeta ADD COLUMN muted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ConversationMeta ADD COLUMN manualUnread INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE OutgoingOperation ADD COLUMN undoElapsed INTEGER")
                db.execSQL("ALTER TABLE OutgoingOperation ADD COLUMN snapshot TEXT")
                db.execSQL("ALTER TABLE OutgoingOperation ADD COLUMN retryOf TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_OutgoingOperation_retryOf ON OutgoingOperation(retryOf)")
                db.execSQL("ALTER TABLE OutgoingAttempt ADD COLUMN phase TEXT NOT NULL DEFAULT 'LEGACY_UNKNOWN'")
                db.execSQL("ALTER TABLE OutgoingAttempt ADD COLUMN providerId INTEGER")
                db.execSQL("UPDATE OutgoingAttempt SET providerId=(SELECT providerId FROM OutgoingRecipient r WHERE r.operationId=OutgoingAttempt.operationId AND r.address=OutgoingAttempt.address)")
                // v2 classified every negative callback as Failed; that is not sufficient proof for retry.
                db.execSQL("UPDATE OutgoingPart SET sent='Unknown' WHERE sent='Failed'")
                db.execSQL("UPDATE OutgoingRecipient SET state='DISPATCH_UNCERTAIN' WHERE state IN ('FAILED','PARTIALLY_SENT') AND EXISTS(SELECT 1 FROM OutgoingAttempt a WHERE a.operationId=OutgoingRecipient.operationId AND a.address=OutgoingRecipient.address)")
                db.execSQL("UPDATE OutgoingOperation SET state='DISPATCH_UNCERTAIN',version=version+1 WHERE state IN ('FAILED','PARTIALLY_SENT') AND EXISTS(SELECT 1 FROM OutgoingRecipient r WHERE r.operationId=OutgoingOperation.id AND r.state='DISPATCH_UNCERTAIN')")
                db.execSQL("ALTER TABLE SavedDraft ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE SavedDraft ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS ClosedDraft (id TEXT NOT NULL PRIMARY KEY)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE OutgoingRecipient ADD COLUMN providerKind TEXT NOT NULL DEFAULT 'SMS'")
            }
        }
        fun open(context: Context): ChatraDatabase = Room.databaseBuilder(context.applicationContext, ChatraDatabase::class.java, "chatra.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
