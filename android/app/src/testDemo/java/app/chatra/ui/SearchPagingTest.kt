package app.chatra.ui

import androidx.lifecycle.SavedStateHandle
import app.chatra.core.model.*
import app.chatra.ui.search.SearchViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SearchPagingTest {
    private fun hit(id:Long,text:String) = SearchHit(Message(MessageRef.Provider(ProviderRef(ProviderTransport.SMS,id)),ConversationId("qa"),Direction.Incoming,Address("QA"),text,Instant.ofEpochMilli(id),false),"QA")
    @Test fun typingDebouncesTwoHundredMillisecondsAndCancelsOldQuery() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val requests=mutableListOf<String>()
            val repo=object:SearchRepository {
                override fun search(query:SearchQuery)=flow {
                    requests+=query.text
                    delay(if(query.text=="old")1000 else 10)
                    emit(SearchResults(listOf(hit(1,query.text))))
                }
            }
            val model=SearchViewModel(SavedStateHandle(),repo,"old",null)
            val collector=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){model.state.collect()}
            runCurrent();advanceTimeBy(199);runCurrent();assertTrue(requests.isEmpty())
            advanceTimeBy(1);runCurrent();assertEquals(listOf("old"),requests)
            model.text("ŁÓDŹ");advanceTimeBy(200);runCurrent();advanceTimeBy(10);runCurrent()
            assertEquals("ŁÓDŹ",model.state.value.results.hits.single().message.body)
            advanceTimeBy(1500);runCurrent();assertEquals("ŁÓDŹ",model.state.value.results.hits.single().message.body)
            collector.cancel()
        } finally {Dispatchers.resetMain()}
    }
    @Test fun pagingSurvivesReturningFromConversationAndEveryFilterRestores() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val calls=mutableListOf<SearchQuery>()
            val repo=object:SearchRepository {override fun search(query:SearchQuery)=flow {
                calls+=query
                emit(if(query.after==null)SearchResults(listOf(hit(1,"one"),hit(2,"two")),next=SearchCursor(2,2)) else SearchResults(listOf(hit(3,"three"))))
            }}
            val saved=SavedStateHandle();val model=SearchViewModel(saved,repo,"paczk",null,debounceMillis=0)
            var collector=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){model.state.collect()}
            runCurrent();model.loadMore();model.loadMore();runCurrent()
            assertEquals(3,model.state.value.results.hits.size);assertEquals(2,calls.size)
            collector.cancel();advanceTimeBy(6000);runCurrent()
            collector=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){model.state.collect()};runCurrent()
            assertEquals(3,model.state.value.results.hits.size);assertEquals(2,calls.size)
            model.sender(ConversationId("thread"));model.address("+48600100200");model.since(Instant.ofEpochMilli(1));model.until(Instant.ofEpochMilli(50));model.content(SearchContent.Images);model.sim(SubscriptionId(2));runCurrent()
            val restored=SearchViewModel(saved,repo,"",null,debounceMillis=0)
            val restoredCollector=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){restored.state.collect()};runCurrent()
            val q=calls.last();assertEquals(ConversationId("thread"),q.sender);assertEquals("+48600100200",q.senderAddress)
            assertEquals(Instant.ofEpochMilli(1),q.since);assertEquals(Instant.ofEpochMilli(50),q.until);assertEquals(SearchContent.Images,q.content);assertEquals(SubscriptionId(2),q.subscriptionId)
            restored.clearFilters();runCurrent();assertFalse(calls.last().hasFilters);assertEquals("paczk",restored.query.value)
            collector.cancel();restoredCollector.cancel()
        } finally {Dispatchers.resetMain()}
    }
}
