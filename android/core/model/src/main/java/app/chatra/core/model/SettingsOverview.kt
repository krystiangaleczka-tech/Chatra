package app.chatra.core.model

enum class SettingsEntry { Sims, Delay, Counter, Hd, Appearance, Unknown, Blocked, Trash, Privacy, Pro }

/** Oferta sklepu jest danymi. Wyjątek [DemoPlaceholder] służy wyłącznie planszy referencyjnej. */
sealed interface ProOffer {
    data object Unavailable : ProOffer
    data object DemoPlaceholder : ProOffer
    data class Available(val formattedPrice: String) : ProOffer {
        init { require(formattedPrice.isNotBlank() && "[CENA]" !in formattedPrice) }
    }
}

fun proSummary(offer: ProOffer, demo: Boolean): String = when {
    offer is ProOffer.Available -> "Jednorazowo ${offer.formattedPrice}. Kompozycje Bursztyn i Las."
    demo && offer == ProOffer.DemoPlaceholder -> "Jednorazowo [CENA]. Dodatkowe kompozycje i reguły porządku. Podstawy zostają darmowe."
    else -> "Oferta zakupu jest teraz niedostępna. Podstawowe funkcje pozostają darmowe."
}

fun simSettingsSummary(cards: List<SimCard>, default: SubscriptionId?): String {
    val available = cards.filter { it.available }
    return if (available.isEmpty()) "Brak dostępnych kart SIM" else available.joinToString(" · ") {
        "${it.shortLabel} ${it.displayName}" + if (it.id == default) " — domyślna" else ""
    }
}

fun appearanceSummary(p: AppearancePrefs): String =
    p.composition.label + " · " +
        listOf("jasny", "ciemny", "automatyczny")[p.mode.ordinal] + " · " +
        if (p.density == Density.Standard) "standardowa gęstość" else "kompaktowa gęstość"

fun polishCount(count: Int, one: String, few: String, many: String): String = "$count " + when {
    count == 1 -> one
    count % 10 in 2..4 && count % 100 !in 12..14 -> few
    else -> many
}
