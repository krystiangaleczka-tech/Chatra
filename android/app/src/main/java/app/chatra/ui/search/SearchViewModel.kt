package app.chatra.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

data class SearchState(val results: SearchResults = SearchResults(), val loading: Boolean = true, val error: Boolean = false, val loadingMore: Boolean = false, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val saved: SavedStateHandle, private val repository: SearchRepository, initialQuery: String,
    initialFilter: String?, initialConversation: ConversationId? = null, private val debounceMillis: Long = 200) : ViewModel() {
    init {
        if (!saved.contains("query")) saved["query"] = initialQuery
        if (!saved.contains("filter")) saved["filter"] = if (initialFilter == "parcels") SearchFilter.Parcels.name else SearchFilter.All.name
        if (!saved.contains("sender") && initialConversation != null) saved["sender"] = initialConversation.value
    }
    val query = saved.getStateFlow("query", initialQuery)
    val filter = saved.getStateFlow("filter", SearchFilter.All.name)
    val sender = saved.getStateFlow<String?>("sender", initialConversation?.value)
    val since = saved.getStateFlow<Long?>("since", null)
    val until = saved.getStateFlow<Long?>("until", null)
    val dateZone = saved.getStateFlow("dateZone", java.time.ZoneId.systemDefault().id)
    fun dateZone(value: java.time.ZoneId) { saved["dateZone"] = value.id }
    val senderAddress = saved.getStateFlow<String?>("address", null)
    val content = saved.getStateFlow("content", SearchContent.Any.name)
    val sim = saved.getStateFlow<Int?>("subscription", null)
    private val retry = MutableStateFlow(0)
    private val cursor = MutableStateFlow<SearchCursor?>(null)
    private val core = combine(query, filter, sender, since, until) { q, f, s, a, b -> SearchQuery(q, SearchFilter.valueOf(f), s?.let(::ConversationId), a?.let(Instant::ofEpochMilli), b?.let(Instant::ofEpochMilli)) }
    private val filters = combine(core, senderAddress, content, sim) { q, address, type, subscription -> q.copy(senderAddress = address, content = SearchContent.valueOf(type), subscriptionId = subscription?.let(::SubscriptionId)) }
    val state: StateFlow<SearchState> = combine(filters, retry, repository.changes) { q, _, _ -> q }.debounce(debounceMillis)
        .flatMapLatest { q ->
            cursor.value = null
            cursor.flatMapLatest { page ->
                repository.search(q.copy(after = page)).map { page to SearchState(it, loading = false) }
                    .onStart { emit(page to SearchState(loading = page == null, loadingMore = page != null)) }
                    .catch { e -> emit(page to SearchState(loading = false, error = true, message = if (e is IllegalArgumentException || e is IllegalStateException || e is SecurityException) e.message else null)) }
            }.scan(SearchState()) { previous, (page, next) ->
                if (page == null) next
                else if (next.loadingMore) previous.copy(loadingMore = true)
                else next.copy(results = next.results.copy(hits = (previous.results.hits + next.results.hits).distinctBy { it.message.ref }, quick = previous.results.quick), loadingMore = false)
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, SearchState())

    fun text(value: String) { saved["query"] = value.take(512) }
    fun filter(value: SearchFilter) { saved["filter"] = if (filter.value == value.name) SearchFilter.All.name else value.name }
    fun sender(value: ConversationId?) { saved["sender"] = value?.value }
    fun since(value: Instant?) { saved["since"] = value?.toEpochMilli() }
    fun until(value: Instant?) { saved["until"] = value?.toEpochMilli() }
    fun address(value: String?) { saved["address"] = value }
    fun content(value: SearchContent) { saved["content"] = value.name }
    fun sim(value: SubscriptionId?) { saved["subscription"] = value?.value }
    fun clearFilters() { saved["filter"] = SearchFilter.All.name; saved["sender"] = null; saved["since"] = null; saved["until"] = null; saved["address"] = null; saved["content"] = SearchContent.Any.name; saved["subscription"] = null }
    fun loadMore() { if (!state.value.loadingMore) state.value.results.next?.let { cursor.value = it } }
    fun retry() { viewModelScope.launch { repository.rebuild(); retry.value++ } }
}
