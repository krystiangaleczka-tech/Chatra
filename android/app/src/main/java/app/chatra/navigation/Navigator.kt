package app.chatra.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
}
