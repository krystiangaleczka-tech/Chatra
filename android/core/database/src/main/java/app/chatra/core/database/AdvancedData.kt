package app.chatra.core.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Entity(indices = [Index(value = ["operationId"], unique = true)], foreignKeys = [ForeignKey(entity = OutgoingOperation::class, parentColumns = ["id"], childColumns = ["operationId"], onDelete = ForeignKey.CASCADE)])
data class ScheduledRow(
    @PrimaryKey val id: String, val operationId: String, val instant: Long, val local: String, val zone: String,
    val policy: String, val version: Int, val token: String, val title: String, val createdAt: Long,
    val registered: Boolean = false, val error: String? = null, val manual: Boolean = false, val boot: Int = -1,
)

@Entity(indices = [Index("conversationId"), Index(value = ["timestamp", "rowid"])])
data class SearchDocument(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long,
    val kind: String, val providerId: Long, val conversationId: String, val body: String, val title: String,
    val sender: String, val timestamp: Long, val subscriptionId: Int?, val media: String,
    val hasAttachments: Boolean, val code: String?, val parcel: String?, val hash: String, val generation: String, val incoming: Boolean,
)
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity
data class SearchFts(@PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long, val tokens: String)
data class SearchHash(@ColumnInfo(name = "rowid") val rowid: Long, val hash: String)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM ScheduledRow ORDER BY instant,id") fun rows(): Flow<List<ScheduledRow>>
    @Query("SELECT * FROM ScheduledRow WHERE id=:id") suspend fun row(id: String): ScheduledRow?
    @Query("SELECT * FROM ScheduledRow WHERE operationId=:operation") suspend fun forOperation(operation: String): ScheduledRow?
    @Insert suspend fun insert(row: ScheduledRow)
    @Query("UPDATE ScheduledRow SET operationId=:operation,instant=:instant,local=:local,zone=:zone,policy=:policy,version=version+1,token=:token,registered=0,error=NULL,manual=:manual WHERE id=:id AND version=:version")
    suspend fun revise(id: String, version: Int, operation: String, instant: Long, local: String, zone: String, policy: String, token: String, manual: Boolean): Int
    @Query("UPDATE ScheduledRow SET registered=:registered,error=:error,boot=COALESCE(:boot,boot) WHERE id=:id AND version=:version AND token=:token") suspend fun alarmResult(id: String, version: Int, token: String, registered: Boolean, error: String?, boot: Int? = null): Int
    @Query("DELETE FROM ScheduledRow WHERE id=:id AND version=:version") suspend fun delete(id: String, version: Int): Int
    @Query("UPDATE OutgoingOperation SET state='PENDING_UNDO',version=version+1,createdAt=:wall,undoUntil=:wall,undoElapsed=:elapsed,bootCount=:boot,subscriptionId=:sim,subscriptionLabel=:label WHERE id=:id AND version=:version AND state IN ('SCHEDULED','MISSED_NEEDS_DECISION')")
    suspend fun activate(id: String, version: Int, wall: Long, elapsed: Long, boot: Int, sim: Int, label: String): Int
    @Query("UPDATE OutgoingOperation SET body=:body,snapshot=:snapshot,subscriptionId=:sim,subscriptionLabel=:label,state='SCHEDULED',version=version+1 WHERE id=:id AND version=:version AND state IN ('SCHEDULED','MISSED_NEEDS_DECISION')")
    suspend fun editOperation(id: String, version: Int, body: String, snapshot: String, sim: Int, label: String): Int
}

@Dao
interface SearchDao {
    @Upsert suspend fun documents(rows: List<SearchDocument>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun fts(rows: List<SearchFts>)
    @Query("SELECT rowid,hash FROM SearchDocument WHERE rowid IN (:ids)") suspend fun hashes(ids: List<Long>): List<SearchHash>
    @Query("UPDATE SearchDocument SET generation=:generation WHERE rowid IN (:ids)") suspend fun seen(ids: List<Long>, generation: String)
    @Query("DELETE FROM SearchFts WHERE rowid IN (SELECT rowid FROM SearchDocument WHERE generation!=:generation)") suspend fun sweepFts(generation: String)
    @Query("DELETE FROM SearchDocument WHERE generation!=:generation") suspend fun sweep(generation: String)
    @Query("DELETE FROM SearchFts") suspend fun clearFts()
    @Query("DELETE FROM SearchDocument") suspend fun clear()
    @Query("SELECT COUNT(*) FROM SearchDocument") suspend fun count(): Int
    @RawQuery(observedEntities = [SearchDocument::class, SearchFts::class]) fun observe(query: SupportSQLiteQuery): Flow<List<SearchDocument>>
    @RawQuery suspend fun query(query: SupportSQLiteQuery): List<SearchDocument>
}
