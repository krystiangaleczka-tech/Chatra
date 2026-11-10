package app.chatra.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import app.chatra.core.model.*
import java.net.URLEncoder
import java.net.URLDecoder

/**
 * Trzy sekcje z własnymi stosami (specyfikacja 5). Start nie zostaje pod Skrzynką po nadaniu roli.
 * Back: najpierw arkusz, potem ekran; na korzeniu sekcji innej niż Rozmowy wraca do Rozmów.
 */
@Stable
class Navigator(initialTab: Tab = Tab.Conversations, initialStack: List<Route>? = null) {
    var tab by mutableStateOf(initialTab)
        private set
    private val stacks = mutableStateMapOf<Tab, List<Route>>().apply {
        Tab.entries.forEach { put(it, listOf(it.root())) }
        if (initialStack != null) put(initialTab, initialStack)
    }

    val stack: List<Route> get() = stacks.getValue(tab)
    val top: Route get() = stack.last()

    /** Ekran pod arkuszami (rysowany zawsze) i najwyższy arkusz (jeśli jest). */
    val screen: Route get() = stack.last { it !is Route.Sheet }
    val sheet: Route.Sheet? get() = stack.lastOrNull()?.takeIf { it is Route.Sheet } as Route.Sheet?

    fun select(t: Tab) {
        if (t == tab) stacks[t] = listOf(t.root()) else tab = t
    }

    fun push(route: Route) {
        stacks[tab] = stack + route
    }

    /** Zastępuje najwyższy arkusz (np. Dodatki → Zaplanuj, Back wraca do Dodatków). */
    fun replaceTop(route: Route) {
        stacks[tab] = stack.dropLast(1) + route
    }

    fun resetTo(vararg routes: Route) {
        stacks[tab] = routes.toList()
    }

    /** @return false, gdy nie ma dokąd wrócić (system zamyka aktywność). */
    fun back(): Boolean {
        if (stack.size > 1) {
            stacks[tab] = stack.dropLast(1)
            return true
        }
        if (tab != Tab.Conversations) {
            tab = Tab.Conversations
            return true
        }
        return false
    }

    fun popSheets() {
        stacks[tab] = stack.dropLastWhile { it is Route.Sheet }
    }

    companion object {
        /** Odtwarza aktywną sekcję i wszystkie stosy, także po ponownym utworzeniu procesu. */
        val Saver = listSaver<Navigator, String>(
            save = { nav -> listOf(nav.tab.name) + Tab.entries.map { tab -> nav.stacks.getValue(tab).joinToString("\n", transform = ::encodeRoute) } },
            restore = { values -> Navigator(Tab.valueOf(values.first())).apply {
                Tab.entries.forEachIndexed { i, tab -> stacks[tab] = values[i + 1].split('\n').map(::decodeRoute) }
            } },
        )
    }
}

private fun encodeRoute(route: Route): String {
    val fields = when (route) {
        Route.Start -> listOf("start")
        Route.Inbox -> listOf("inbox")
        Route.Scheduled -> listOf("scheduled")
        Route.Settings -> listOf("settings")
        Route.Appearance -> listOf("appearance")
        Route.Privacy -> listOf("privacy")
        Route.Help -> listOf("help")
        Route.DesignCatalog -> listOf("catalog")
        is Route.SettingsDetail -> listOf("detail", route.kind.name)
        is Route.Search -> listOf("search", route.initialQuery, route.initialFilter.orEmpty())
        is Route.Extras -> listOf("extras", route.id.value)
        is Route.Schedule -> listOf("schedule", route.id.value, route.preset.toString())
        is Route.NewMessage -> listOf("new", route.preset.toString(), route.externalId.orEmpty())
        is Route.Conversation -> listOf("conversation", route.id.value) + when (val m = route.message) {
            null -> listOf("none")
            is MessageRef.Provider -> listOf("provider", m.ref.transport.name, m.ref.providerId.toString())
            is MessageRef.Hd -> listOf("hd", m.id)
            is MessageRef.Local -> listOf("local", m.operationId.value)
        }
    }
    return fields.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
}

private fun decodeRoute(value: String): Route {
    val p = value.split('|').map { URLDecoder.decode(it, "UTF-8") }
    return when (p[0]) {
        "start" -> Route.Start
        "inbox" -> Route.Inbox
        "scheduled" -> Route.Scheduled
        "settings" -> Route.Settings
        "appearance" -> Route.Appearance
        "privacy" -> Route.Privacy
        "help" -> Route.Help
        "catalog" -> Route.DesignCatalog
        "detail" -> Route.SettingsDetail(Route.SettingsDetail.Kind.valueOf(p[1]))
        "search" -> Route.Search(p[1], p[2].ifEmpty { null })
        "extras" -> Route.Extras(ConversationId(p[1]))
        "schedule" -> Route.Schedule(ConversationId(p[1]), p[2].toBoolean())
        "new" -> Route.NewMessage(p[1].toBoolean(), p.getOrNull(2)?.ifEmpty { null })
        "conversation" -> Route.Conversation(ConversationId(p[1]), when (p[2]) {
            "provider" -> MessageRef.Provider(ProviderRef(ProviderTransport.valueOf(p[3]), p[4].toLong()))
            "hd" -> MessageRef.Hd(p[3])
            "local" -> MessageRef.Local(OperationId(p[3]))
            else -> null
        })
        else -> Route.Inbox
    }
}
