package app.chatra.telephony

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import app.chatra.core.model.Address
import app.chatra.core.model.ContactsDirectory
import app.chatra.core.model.Participant
import app.chatra.core.model.isOutgoingNumber
import app.chatra.core.model.outgoingRecipientKey
import app.chatra.core.model.searchFold
import app.chatra.platform.AccessFeature
import app.chatra.platform.PlatformAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/** CH029: tylko odczyt numerów po nadaniu READ_CONTACTS, bez utrwalania książki w bazie. */
internal class AndroidContactsDirectory(
    private val context: Context,
    private val platform: PlatformAccess,
) : ContactsDirectory {
    private fun updates(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val resolver = context.contentResolver
        try {
            resolver.registerContentObserver(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, true, observer)
        } catch (_: SecurityException) { /* Brak zgody nie blokuje wpisywania numeru ręcznie. */ }
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun search(query: String): Flow<List<Participant>> =
        combine(platform.changes, updates()) { _, _ -> query }
            .mapLatest { withContext(Dispatchers.IO) { read(it) } }

    override fun suggestions(): Flow<List<Participant>> = search("")

    override suspend fun lookup(address: Address): Participant? = withContext(Dispatchers.IO) {
        read(address.raw).firstOrNull { outgoingRecipientKey(it.address) == outgoingRecipientKey(address) }
    }

    private fun read(query: String): List<Participant> {
        if (!platform.allowed(AccessFeature.Contacts)) return emptyList()
        val needle = searchFold(query.trim())
        val digits = query.filter(Char::isDigit)
        val result = linkedMapOf<String, Participant>()
        val phone = ContactsContract.CommonDataKinds.Phone
        try {
            context.contentResolver.query(
                phone.CONTENT_URI,
                arrayOf(phone.NUMBER, phone.DISPLAY_NAME),
                null, null,
                "${phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                val numberColumn = cursor.getColumnIndexOrThrow(phone.NUMBER)
                val nameColumn = cursor.getColumnIndexOrThrow(phone.DISPLAY_NAME)
                while (cursor.moveToNext() && result.size < 100) {
                    val number = cursor.getString(numberColumn).orEmpty()
                    if (!isOutgoingNumber(number)) continue
                    val name = cursor.getString(nameColumn)?.takeIf(String::isNotBlank)
                    val address = Address(number)
                    val key = outgoingRecipientKey(address)
                    val matches = needle.isBlank() ||
                        (name != null && needle in searchFold(name)) ||
                        (digits.isNotEmpty() && (digits in number.filter(Char::isDigit) || digits in key.filter(Char::isDigit)))
                    if (matches) result.putIfAbsent(key, Participant(address, name))
                }
            }
        } catch (_: SecurityException) { return emptyList() }
        return result.values.toList()
    }
}
