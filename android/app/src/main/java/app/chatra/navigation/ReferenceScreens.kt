package app.chatra.navigation

import app.chatra.core.model.ConversationId

/**
 * Stany startowe dziesięciu plansz dla zrzutów referencyjnych (CH006): S01…S10 → sekcja i stos.
 * ID rozmów pochodzą z fixture („c-ola”).
 */
object ReferenceScreens {
    private val ola = ConversationId("c-ola")

    fun navigatorFor(screen: String): Navigator? = when (screen.uppercase()) {
        "S01" -> Navigator(Tab.Conversations, listOf(Route.Start))
        "S02" -> Navigator(Tab.Conversations)
        "S03" -> Navigator(Tab.Conversations, listOf(Route.Inbox, Route.Conversation(ola)))
        "S04" -> Navigator(Tab.Conversations, listOf(Route.Inbox, Route.Conversation(ola), Route.Extras(ola)))
        "S05" -> Navigator(Tab.Conversations, listOf(Route.Inbox, Route.Conversation(ola), Route.Schedule(ola, preset = true)))
        "S06" -> Navigator(Tab.Scheduled)
        "S07" -> Navigator(Tab.Conversations, listOf(Route.Inbox, Route.Search("paczk", "parcels")))
        "S08" -> Navigator(Tab.Conversations, listOf(Route.Inbox, Route.NewMessage(preset = true)))
        "S09" -> Navigator(Tab.Settings, listOf(Route.Settings, Route.Appearance))
        "S10" -> Navigator(Tab.Settings)
        else -> null
    }
}
