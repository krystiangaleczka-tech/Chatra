package app.chatra.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** HD history is local; a push never creates a downloaded/read receipt. */
@Entity(indices = [Index("conversationId")])
data class HdRecord(
    @PrimaryKey val id: String, val owner: String, val conversationId: String,
    val sender: String, val recipient: String, val incoming: Boolean,
    val createdAt: Long, val expiresAt: Long, val manifest: String,
    val localFiles: String = "[]", val state: String = "AVAILABLE",
    val downloadedAt: Long? = null, val read: Boolean = false,
    val bytes: Long = 0, val metered: Boolean = false,
)
@Entity data class HdCursor(@PrimaryKey val owner: String, val cursor: String)
@Entity data class HdDismissed(@PrimaryKey val id: String)
@Entity(indices = [Index("entryId")], foreignKeys = [ForeignKey(
    entity = TrashEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE,
)]) data class TrashHdRef(@PrimaryKey val id: String, val entryId: String)

@Dao interface HdDao {
    @Query("SELECT * FROM HdRecord WHERE NOT EXISTS(SELECT 1 FROM TrashHdRef t WHERE t.id=HdRecord.id)")
    fun visible(): Flow<List<HdRecord>>
    @Query("SELECT * FROM HdRecord") suspend fun all(): List<HdRecord>
    @Query("SELECT * FROM HdRecord WHERE id=:id") suspend fun get(id: String): HdRecord?
    @Query("SELECT * FROM HdRecord WHERE conversationId=:id AND NOT EXISTS(SELECT 1 FROM TrashHdRef t WHERE t.id=HdRecord.id)")
    suspend fun snapshot(id: String): List<HdRecord>
    @Query("SELECT * FROM HdRecord WHERE state IN ('QUEUED','DOWNLOADING','RECEIPT')") suspend fun pending(): List<HdRecord>
    @Query("SELECT COUNT(*) FROM HdDismissed WHERE id=:id") suspend fun dismissed(id: String): Int
    @Upsert suspend fun put(row: HdRecord)
    @Query("SELECT cursor FROM HdCursor WHERE owner=:owner") suspend fun cursor(owner: String): String?
    @Upsert suspend fun cursor(row: HdCursor)
    @Query("UPDATE HdRecord SET read=1 WHERE conversationId=:id") suspend fun read(id: String)
    @Query("UPDATE HdRecord SET read=1 WHERE id IN (:ids) AND conversationId=:conversation") suspend fun shown(conversation: String, ids: List<String>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun hide(rows: List<TrashHdRef>)
    @Query("SELECT id FROM TrashHdRef WHERE entryId=:entry") suspend fun hidden(entry: String): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun dismiss(row: HdDismissed)
    @Query("DELETE FROM HdRecord WHERE id=:id") suspend fun delete(id: String)
}
