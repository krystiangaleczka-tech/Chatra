package app.chatra.core.model

/**
 * Adres nadawcy/odbiorcy. Nadawca alfanumeryczny (np. „Paczka24”) bywa nieodpowiadalny i nie
 * może być użyty jako numer wyjściowy (S08).
 */
data class Address(val raw: String) {
    val kind: Kind = when {
        raw.isBlank() -> Kind.Unknown
        raw.any { it.isLetter() } -> Kind.Alphanumeric
        else -> Kind.Phone
    }

    /** Klucz porównania: cyfry i wiodący „+”; dla nadawcy alfanumerycznego — wielkie litery. */
    val normalized: String = when (kind) {
        Kind.Phone -> buildString {
            raw.trim().forEachIndexed { i, c -> if (c.isDigit() || (i == 0 && c == '+')) append(c) }
        }.let { if (it.startsWith("00")) "+" + it.drop(2) else it }
        Kind.Alphanumeric -> raw.trim().uppercase()
        Kind.Unknown -> ""
    }

    val canReply: Boolean get() = kind == Kind.Phone && normalized.count { it.isDigit() } >= 3

    enum class Kind { Phone, Alphanumeric, Unknown }
}

data class Participant(val address: Address, val displayName: String? = null) {
    /** Bez kontaktu pokazujemy adres; nie zgadujemy nazwy (S02). */
    val label: String get() = displayName ?: address.raw
}

/** Ustalony zbiór uczestników identyfikuje wątek grupowy (specyfikacja 7, źródła prawdy). */
fun participantsKey(addresses: Collection<Address>): String =
    addresses.map { recipientKey(it.raw) }.filter { it.isNotEmpty() }.distinct().sorted().joinToString("|")

/** Identity comparison uses PL for local nine-digit numbers; the destination is preserved. */
fun recipientKey(value: String, region: String = "PL"): String {
    val address = Address(value)
    return if (address.kind == Address.Kind.Phone && region == "PL" && address.normalized.matches(Regex("[0-9]{9}"))) "+48${address.normalized}" else address.normalized
}
