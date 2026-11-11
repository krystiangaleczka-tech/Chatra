package app.chatra.local

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract
import app.chatra.core.model.*
import app.chatra.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** One row per phone number; revocation never retains contact names in the UI cache. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemContacts(private val context: Context, private val platform: PlatformAccess, scope: CoroutineScope) : ContactsDirectory {
    private val changes = platform.changes.flatMapLatest { callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { trySend(Unit) } }
        try { context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer) } catch (_: SecurityException) { }
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    } }
    private val rows = combine(changes, platform.changes) { _, _ -> Unit }.mapLatest {
        withContext(Dispatchers.IO) {
            if (!platform.allowed(AccessFeature.Contacts)) emptyList() else try {
                buildList {
                    context.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME, Phone.NORMALIZED_NUMBER), null, null, Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")?.use { c ->
                        while (c.moveToNext()) {
                            ensureActive()
                            val raw = c.getString(0).orEmpty()
                            if (isOutgoingNumber(raw)) {
                                val normalized=c.getString(2)?.takeIf {it.startsWith("+") && isOutgoingNumber(it)} ?: raw
                                add(Participant(Address(normalized),c.getString(1)))
                            }
                        }
                    }
                }.distinctBy { recipientKey(it.address.raw) }
            } catch (_: SecurityException) { emptyList() }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    override fun suggestions(): Flow<List<Participant>> = rows
    override suspend fun lookup(address: Address): Participant? = rows.value.takeIf { platform.allowed(AccessFeature.Contacts) }?.firstOrNull { recipientKey(it.address.raw) == recipientKey(address.raw) }
}
