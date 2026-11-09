package app.chatra.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class SearchState(val results: SearchResults = SearchResults(), val loading: Boolean = true, val error: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val saved: SavedStateHandle, private val repository: SearchRepository, initialQuery: String, initialFilter: String?) : ViewModel() {
    init {
        if (!saved.contains("query")) saved["query"] = initialQuery
        if (!saved.contains("filter")) saved["filter"] = if (initialFilter == "parcels") SearchFilter.Parcels.name else SearchFilter.All.name
    }
    val query = saved.getStateFlow("query", initialQuery)
    val filter = saved.getStateFlow("filter", SearchFilter.All.name)
    val sender = saved.getStateFlow<String?>("sender", null)
    val since = saved.getStateFlow<Long?>("since", null)
    private val retry = MutableStateFlow(0)
    val state = combine(query, filter, sender, since, retry) { q, f, s, t, _ ->
        SearchQuery(q, SearchFilter.valueOf(f), s?.let(::ConversationId), t?.let(java.time.Instant::ofEpochMilli))
    }.flatMapLatest { q ->
        flow { emitAll(repository.search(q)) }.map { SearchState(it, loading = false) }
            .onStart { emit(SearchState()) }.catch { emit(SearchState(loading = false, error = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun text(value: String) { saved["query"] = value }
    fun filter(value: SearchFilter) { saved["filter"] = if (filter.value == value.name) SearchFilter.All.name else value.name }
    fun sender(value: ConversationId?) { saved["sender"] = value?.value }
    fun since(value: java.time.Instant?) { saved["since"] = value?.toEpochMilli() }
    fun retry() { retry.value++ }
}
