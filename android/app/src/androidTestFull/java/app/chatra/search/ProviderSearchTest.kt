package app.chatra.search

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.database.ChatraDatabase
import app.chatra.core.model.*
import app.chatra.platform.SystemPlatform
import app.chatra.telephony.SystemHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.util.UUID

/** Real provider adapter CRUD, distinct from the synthetic 50k indexing benchmark. */
class ProviderSearchTest {
    @Test fun originalSourceIsRecheckedAfterEditAndDelete() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val platform = SystemPlatform(context)
        assumeTrue(platform.isHeld())
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var owned: Uri? = null
        val token = "qa" + UUID.randomUUID().toString().replace("-", "")
        try {
            val contacts = object : ContactsDirectory {
                override fun suggestions() = flowOf(emptyList<Participant>())
                override suspend fun lookup(address: Address): Participant? = null
            }
            val history = SystemHistory(context, db, platform, contacts, scope)
            owned = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                put("address", "+48999000123"); put("body", "$token Kod odbioru: 482 915")
                put("date", System.currentTimeMillis()); put("read", 1); put("sub_id", 1)
            })!!
            val engine = SystemSearch(db, ProviderSearchSource(context, db, history, contacts, platform), scope)
            val first = withTimeout(15000) { engine.search(SearchQuery(token)).first { !it.indexing && it.hits.size == 1 } }
            assertEquals("482 915", first.quick.single().value)
            val original = first.hits.single().message
            assertEquals(Direction.Incoming, original.direction)
            assertEquals("+48999000123", original.sender?.raw)
            assertEquals(SubscriptionId(1), original.subscriptionId)
            context.contentResolver.update(owned, ContentValues().apply { put("body", "$token zmieniony tekst") }, null, null)
            val edited = withTimeout(15000) { engine.search(SearchQuery(token)).first { !it.indexing && it.hits.singleOrNull()?.message?.body?.contains("zmieniony") == true } }
            assertTrue(edited.quick.isEmpty()); assertEquals(original.ref, edited.hits.single().message.ref)
            context.contentResolver.delete(owned, null, null)
            withTimeout(15000) { engine.search(SearchQuery(token)).first { !it.indexing && it.hits.isEmpty() } }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            owned?.let { context.contentResolver.delete(it, null, null) }
            db.close()
        }
    }
}
