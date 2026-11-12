package app.chatra.navigation

import app.chatra.core.model.ConversationId

/** Ekrany. Adresy niosą lokalne ID, nigdy treści ani numerów (specyfikacja 5). */
sealed interface Route {
    data object Start : Route
    data object Inbox : Route
    data class Conversation(val id: ConversationId, val message: app.chatra.core.model.MessageRef? = null) : Route
    data class Search(val initialQuery: String = "", val initialFilter: String? = null, val conversation: ConversationId? = null) : Route
    data object Scheduled : Route
    data object Settings : Route
    data object Appearance : Route
    data object Privacy : Route
    data object Help : Route
    data object DesignCatalog : Route
    data class SettingsDetail(val kind: Kind) : Route {
        enum class Kind { Sims, Delay, Hd, Unknown, Blocked, Trash, Pro }
    }

    /** Arkusze rysowane nad poprzednim ekranem z przyciemnieniem. */
    sealed interface Sheet : Route
    data class Extras(val id: ConversationId) : Sheet
    /** [preset]: plansza S05 z treścią i terminem fixture (tryb referencyjny). */
    data class Schedule(val id: ConversationId, val preset: Boolean = false) : Sheet
    data class NewMessage(val preset: Boolean = false, val externalId: String? = null) : Sheet
}

enum class Tab { Conversations, Scheduled, Settings }

fun Tab.root(): Route = when (this) {
    Tab.Conversations -> Route.Inbox
    Tab.Scheduled -> Route.Scheduled
    Tab.Settings -> Route.Settings
}
