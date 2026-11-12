package app.chatra.search

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.database.*
import app.chatra.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

class SystemSearchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ChatraDatabase
    private lateinit var scope: CoroutineScope
    private class Source(var count: Int) : SearchSource {
        override val changes = MutableStateFlow(0)
        var access = true
        val deleted = mutableSetOf<Long>()
        val overrides = mutableMapOf<Long,String>()
        override fun allowed() = access
        override suspend fun references() = (1L..count.toLong()).filterNot { it in deleted }.map { ref(it) }
        fun ref(id: Long) = ProviderMessageRef("SMS",id,if(id%2L==0L) "a" else "b",1_793_000_000_000L+id*1000,true,false,"")
        override suspend fun load(refs: List<ProviderMessageRef>): List<SearchHit> = refs.filterNot { it.providerId in deleted }.map { r ->
            val body = overrides[r.providerId] ?: "Paczka czeka w Łodzi; wiadomość ${r.providerId}"
            val media = if(r.providerId%10L==0L) listOf(AttachmentSummary(AttachmentId("part-${r.providerId}"),"image/jpeg","Łódź ${r.providerId}.jpg")) else if(r.providerId==7L) listOf(AttachmentSummary(AttachmentId("document"),"text/plain","notatka.txt")) else emptyList()
            SearchHit(Message(MessageRef.Provider(ProviderRef(ProviderTransport.SMS,r.providerId)), ConversationId(r.conversationId),Direction.Incoming,Address("+48600100200"),body,Instant.ofEpochMilli(r.timestamp),false,SubscriptionId(1),attachments=media),"Nadawca Łódź")
        }
    }
    @Before fun prepare() { db=Room.inMemoryDatabaseBuilder(context,ChatraDatabase::class.java).build();scope=CoroutineScope(SupervisorJob()+Dispatchers.IO) }
    @After fun cleanup() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin();db.close() }
    private suspend fun results(engine:SystemSearch,query:SearchQuery) = withTimeout(15000) { engine.search(query).first { !it.indexing } }
    @Test fun fiftyThousandDocumentsPrefixPaginationAndRebuildStayLocal() = runBlocking {
        val source=Source(50_000);val engine=SystemSearch(db,source,scope,autoIndex=false)
        val start=android.os.SystemClock.elapsedRealtime()
        withTimeout(300000) { engine.rebuildNow() }
        val millis=android.os.SystemClock.elapsedRealtime()-start
        android.util.Log.i("ChatraSearchQA","INDEX_50000_MS=$millis")
        assertNull(engine.progress.value.error);assertEquals(50_000,db.search().count())
        val q=SearchQuery("pacz lodz",pageSize=40)
        val first=results(engine,q);assertEquals(40,first.hits.size);assertNotNull(first.next)
        val next=results(engine,q.copy(after=first.next));assertEquals(40,next.hits.size)
        assertTrue(first.hits.map { it.message.ref }.intersect(next.hits.map { it.message.ref }.toSet()).isEmpty())
        assertTrue(first.hits.all { searchTermRanges(it.message.body,"lodz").isNotEmpty() })
        assertTrue(results(engine,q.copy(sender=ConversationId("b"),content=SearchContent.Images)).hits.isEmpty())
        assertEquals(40,results(engine,q.copy(sender=ConversationId("a"),content=SearchContent.Images)).hits.size)
        // Rebuild includes FTS itself, rather than trusting the unchanged document hashes.
        db.search().clearFts()
        assertTrue(results(engine,q).hits.isEmpty())
        withTimeout(300000) { engine.rebuildNow(force=true) }
        assertEquals(40,results(engine,q).hits.size)
    }
    @Test fun changedAndDeletedProviderMessagesAreNeverReturnedFromStaleCache() = runBlocking {
        val source=Source(30);val engine=SystemSearch(db,source,scope,autoIndex=false)
        engine.rebuildNow();val old=results(engine,SearchQuery("pacz",pageSize=40));assertEquals(30,old.hits.size)
        source.deleted+=30;source.overrides[29]="Zmieniono treść bez paczki"
        val stale=results(engine,SearchQuery("pacz",pageSize=40))
        assertFalse(stale.hits.any { (it.message.ref as MessageRef.Provider).ref.providerId==30L })
        assertFalse(results(engine,SearchQuery("lodz",SearchFilter.Codes)).hits.isNotEmpty())
        engine.rebuildNow();assertEquals(29,db.search().count())
        assertEquals(1,results(engine,SearchQuery("zmieniono")).hits.size)
        source.access=false
        try { results(engine,SearchQuery("pacz"));fail("cached SMS exposed without permission") } catch (_: IllegalStateException) {}
    }
    @Test fun queryOperatorsAreBoundAndAllFiltersNarrowTheSameSourceMessage() = runBlocking {
        val source=Source(50);source.overrides[20]="Kod odbioru: 482 915, paczka w Łodzi"
        source.overrides[21]="Telefon: 600100200. Numer konta 123456"
        val engine=SystemSearch(db,source,scope,autoIndex=false);engine.rebuildNow()
        val q=SearchQuery("pacz",SearchFilter.Codes,ConversationId("a"),senderAddress="600100200",content=SearchContent.Images,subscriptionId=SubscriptionId(1))
        val result=results(engine,q);assertEquals(1,result.hits.size);assertEquals("482 915",result.quick.single().value)
        assertNotNull(result.quick.single().message)
        assertTrue(results(engine,q.copy(subscriptionId=SubscriptionId(2))).hits.isEmpty())
        assertTrue(results(engine,q.copy(sender=ConversationId("b"))).hits.isEmpty())
        assertTrue(results(engine,q.copy(until=result.hits.single().message.timestamp)).hits.isEmpty())
        assertTrue(results(engine,SearchQuery("\" OR * ( ) --")).hits.isEmpty())
        assertEquals(1,results(engine,SearchQuery("",SearchFilter.Codes)).quick.size)
        assertEquals(1,results(engine,SearchQuery("pacz",content=SearchContent.Documents)).hits.size)
    }
    @Test fun incrementalUpdatesAndDeletedRowsReconcileThroughSourceEvents() = runBlocking {
        val source=Source(25);val engine=SystemSearch(db,source,scope)
        withTimeout(15000) { engine.changes.first { it>0 } }
        val original=engine.changes.value
        source.overrides[7]="Kod: 654321";source.deleted+=8;source.changes.value++
        withTimeout(15000) { engine.changes.first { it>original } }
        assertEquals(24,db.search().count())
        assertEquals("654321",results(engine,SearchQuery("",SearchFilter.Codes)).quick.single().value)
        assertEquals(1,db.search().query(SimpleSQLiteQuery("SELECT * FROM SearchDocument WHERE code IS NOT NULL")).size)
    }
}
