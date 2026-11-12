package app.chatra.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class LocalBlock(@PrimaryKey val addressKey: String, val label: String, val createdAt: Long)
@Entity data class TrashEntry(@PrimaryKey val id: String, val conversationId: String, val title: String, val createdAt: Long, val expiresAt: Long, val purging: Boolean = false)
@Entity(primaryKeys = ["kind", "providerId"], indices = [Index("entryId")], foreignKeys = [ForeignKey(entity = TrashEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)])
data class TrashRef(val kind: String, val providerId: Long, val entryId: String, val timestamp: Long)

@Dao interface OrganizationDao {
    @Query("SELECT * FROM LocalBlock ORDER BY createdAt DESC") fun blocks(): Flow<List<LocalBlock>>
    @Upsert suspend fun block(row: LocalBlock)
    @Query("DELETE FROM LocalBlock WHERE addressKey=:key") suspend fun unblock(key: String)
    @Query("SELECT * FROM TrashEntry ORDER BY createdAt DESC") fun trash(): Flow<List<TrashEntry>>
    @Query("SELECT * FROM TrashEntry WHERE id=:id") suspend fun entry(id: String): TrashEntry?
    @Insert suspend fun insert(row: TrashEntry)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun hide(refs: List<TrashRef>)
    @Query("SELECT * FROM TrashRef WHERE entryId=:id ORDER BY kind, providerId") suspend fun refs(id: String): List<TrashRef>
    @Query("SELECT * FROM TrashRef") fun hidden(): Flow<List<TrashRef>>
    @Query("SELECT * FROM ProviderMessageRef WHERE conversationId=:id AND NOT EXISTS (SELECT 1 FROM OutgoingRecipient r JOIN OutgoingOperation o ON o.id=r.operationId WHERE r.providerId=ProviderMessageRef.providerId AND r.providerKind=ProviderMessageRef.kind AND o.state IN ('PENDING_UNDO','DISPATCHING','DISPATCH_UNCERTAIN')) AND NOT EXISTS (SELECT 1 FROM TrashRef t WHERE t.kind=ProviderMessageRef.kind AND t.providerId=ProviderMessageRef.providerId AND t.timestamp=ProviderMessageRef.timestamp)") suspend fun snapshot(id: String): List<ProviderMessageRef>
    @Query("UPDATE TrashEntry SET purging=1 WHERE id=:id") suspend fun claim(id: String)
    @Query("DELETE FROM TrashEntry WHERE id=:id") suspend fun remove(id: String)
    @Query("DELETE FROM ProviderMessageRef WHERE kind=:kind AND providerId=:providerId AND timestamp=:timestamp") suspend fun forget(kind: String, providerId: Long, timestamp: Long)
    @Query("DELETE FROM MediaRef WHERE messageId=:id AND NOT EXISTS (SELECT 1 FROM ProviderMessageRef r WHERE r.kind='MMS' AND r.providerId=:id AND r.timestamp!=:timestamp)") suspend fun forgetMedia(id: Long, timestamp: Long)
}
