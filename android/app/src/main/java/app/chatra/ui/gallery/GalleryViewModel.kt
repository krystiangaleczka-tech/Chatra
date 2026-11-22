package app.chatra.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.ConversationId
import app.chatra.core.model.GalleryItem
import app.chatra.core.model.MediaGallery
import app.chatra.core.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class GalleryState(
    val kind: MediaKind = MediaKind.All,
    val items: List<GalleryItem> = emptyList(),
    val total: Int? = null,
    val next: Long? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
)

internal class GalleryViewModel(private val conversation: ConversationId, private val gallery: MediaGallery) : ViewModel() {
    private val mutable = MutableStateFlow(GalleryState())
    val state = mutable.asStateFlow()
    private var request: Job? = null

    init { load() }

    fun select(kind: MediaKind) {
        if (kind == mutable.value.kind) return
        start(kind)
    }

    fun refresh() = start(mutable.value.kind)

    private fun start(kind: MediaKind) {
        request?.cancel()
        mutable.value = GalleryState(kind = kind)
        load()
    }

    fun load() {
        val before = mutable.value
        if (before.loading || (before.total != null && before.next == null)) return
        mutable.value = before.copy(loading = true, error = false)
        request = viewModelScope.launch {
            try {
                val page = gallery.page(conversation, before.kind, before.next, 40)
                ensureActive()
                check(page.nextBefore == null || page.nextBefore != before.next)
                val items = (before.items + page.items).distinctBy { it.source to it.id }
                mutable.value = before.copy(items = items, total = page.total, next = page.nextBefore, loading = false, error = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ensureActive()
                mutable.value = before.copy(loading = false, error = true)
            }
        }
    }
}
