package app.chatra.navigation

import app.chatra.core.model.ConversationId

/** Ekrany. Adresy niosą lokalne ID, nigdy treści ani numerów (specyfikacja 5). */
sealed interface Route {
    data object Start : Route
    data object Inbox : Route
    data class Conversation(val id: ConversationId) : Route
    data class Search(val initialQuery: String = "", val initialFilter: String? = null) : Route
    data object Scheduled : Route
    data object Settings : Route
    data object Appearance : Route
    data object Privacy : Route
    data object Help : Route
    data class SettingsDetail(val kind: Kind) : Route {
        enum class Kind { Sims, Delay, Hd, Unknown, Blocked, Trash, Pro }
    }

    /** Arkusze rysowane nad poprzednim ekranem z przyciemnieniem. */
    sealed interface Sheet : Route
    data class Extras(val id: ConversationId) : Sheet
    data class Schedule(val id: ConversationId) : Sheet
    data class NewMessage(val preset: Boolean = false) : Sheet
}

enum class Tab { Conversations, Scheduled, Settings }

fun Tab.root(): Route = when (this) {
    Tab.Conversations -> Route.Inbox
    Tab.Scheduled -> Route.Scheduled
    Tab.Settings -> Route.Settings
}
