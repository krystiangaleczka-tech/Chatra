package app.chatra.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.ConversationRepository
import app.chatra.core.model.ConversationSummary
import app.chatra.core.model.inboxOrder
import app.chatra.core.model.unreadConversationCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class InboxFilter { All, Unread }

data class InboxState(
    val loading: Boolean = true,
    val filter: InboxFilter = InboxFilter.All,
    val pinned: List<ConversationSummary> = emptyList(),
    val others: List<ConversationSummary> = emptyList(),
    val unreadConversations: Int = 0,
)

class InboxViewModel(repository: ConversationRepository) : ViewModel() {
    private val filter = MutableStateFlow(InboxFilter.All)

    val state: StateFlow<InboxState> = combine(repository.inbox(), filter) { all, f ->
        val visible = all.inboxOrder().filter { !it.blocked && (f == InboxFilter.All || it.isUnread) }
        InboxState(
            loading = false,
            filter = f,
            pinned = visible.filter { it.pinned },
            others = visible.filterNot { it.pinned },
            unreadConversations = all.filterNot { it.blocked }.unreadConversationCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxState())

    fun setFilter(f: InboxFilter) {
        filter.value = f
    }
}
