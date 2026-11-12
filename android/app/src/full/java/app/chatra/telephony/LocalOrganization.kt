package app.chatra.telephony

import android.content.Context
import android.provider.Telephony
import androidx.room.withTransaction
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.di.*
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Local blocks and immutable message scopes. Provider deletion is resumable and idempotent. */
class LocalOrganization(private val context: Context, private val db: ChatraDatabase, private val platform: SystemPlatform,
    scope: CoroutineScope, private val now: () -> Long = System::currentTimeMillis,
    private val allowed: () -> Boolean = { context.smsRead(platform) },
    private val deleteRef: ((TrashRef) -> Unit)? = null) : OrganizationPreview {
    private val dao = db.organization()
    private val mutex = Mutex()
    val changes = combine(dao.blocks(), dao.hidden()) { _, _ -> Unit }
    override val blocked = dao.blocks().map { rows -> rows.map { BlockedPreview(it.addressKey, it.label) } }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val trash = dao.trash().map { rows -> rows.map { TrashPreview(ConversationId(it.conversationId), it.title, it.id, it.expiresAt, it.purging) } }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val editable = true
    private fun access() { check(allowed()) { "Przywróć rolę SMS i dostęp do historii." } }
    override suspend fun block(address: Address) = withContext(Dispatchers.IO) {
        access(); val key = recipientKey(address.raw); require(key.isNotBlank() && address.raw.length <= 256) { "Podaj numer lub nadawcę." }
        dao.block(LocalBlock(key, address.raw, now()))
    }
    override suspend fun unblock(id: String) = withContext(Dispatchers.IO) { access(); dao.unblock(id) }
    override suspend fun addToTrash(id: ConversationId, title: String) = withContext(Dispatchers.IO) { mutex.withLock {
        access()
        db.withTransaction {
            val refs = dao.snapshot(id.value)
            check(refs.isNotEmpty()) { "Brak wiadomości do przeniesienia. Zlecenia wysyłania pozostają poza koszem." }
            val at = now(); val entry = TrashEntry(UUID.randomUUID().toString(), id.value, title, at, at + RETENTION)
            dao.insert(entry); dao.hide(refs.map { TrashRef(it.kind, it.providerId, entry.id, it.timestamp) })
        }
    } }
    override suspend fun restore(id: ConversationId) { dao.trash().first().filter { it.conversationId == id.value }.forEach { restoreEntry(it.id) } }
    override suspend fun restoreEntry(entryId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        access(); val row = dao.entry(entryId) ?: return@withLock
        check(!row.purging) { "Usuwanie już rozpoczęte. Ponów usuwanie pozostałych wiadomości." }
        check(now() < row.expiresAt) { "Minęło 30 dni odzyskiwania." }
        dao.remove(entryId)
    } }
    override suspend fun purge(entryId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        access(); dao.entry(entryId) ?: return@withLock
        dao.claim(entryId)
        // Keep every tombstone until all deletes succeed. A crash can safely retry the same scope.
        for (ref in dao.refs(entryId)) {
            access()
            val uri = if (ref.kind == "SMS") Telephony.Sms.CONTENT_URI else Telephony.Mms.CONTENT_URI
            val date = if (ref.kind == "SMS") ref.timestamp else ref.timestamp / 1000
            if (deleteRef != null) deleteRef.invoke(ref)
            else {
                val args = arrayOf(ref.providerId.toString(), date.toString())
                context.contentResolver.delete(uri, "_id=? AND date=?", args)
                access()
                val remaining = context.contentResolver.query(uri, arrayOf("_id"), "_id=? AND date=?", args, null)?.use { it.count }
                check(remaining == 0) { "Nie udało się usunąć części zakresu. Ponów usuwanie po przywróceniu dostępu." }
            }
            db.withTransaction { dao.forget(ref.kind, ref.providerId, ref.timestamp); if (ref.kind == "MMS") dao.forgetMedia(ref.providerId, ref.timestamp) }
        }
        dao.remove(entryId)
    } }
    suspend fun expire() {
        if (!allowed()) return
        dao.trash().first().filter { it.purging || now() >= it.expiresAt }.forEach { purge(it.id) }
    }
    companion object { const val RETENTION = 30L * 24 * 60 * 60 * 1000 }
}
