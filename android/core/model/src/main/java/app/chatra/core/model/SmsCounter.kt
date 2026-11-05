package app.chatra.core.model

/**
 * Licznik SMS (CH025): znaki widoczne, jednostki kodowania i części. GSM 7-bit (3GPP TS 23.038)
 * z tablicą rozszerzeń liczoną podwójnie; poza alfabetem — UCS-2 (UTF-16, emoji = 2 jednostki).
 * W wariancie full wynik porównujemy z platformowym SmsMessage.calculateLength.
 */
object SmsCounter {
    private const val GSM_BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001BÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val GSM_EXTENDED = "^{}\\[~]|€\u000C"

    enum class Encoding { Gsm7, Ucs2 }

    data class Count(
        val encoding: Encoding,
        /** Jednostki kodowania: septety GSM albo jednostki UTF-16. */
        val units: Int,
        val segments: Int,
        /** Pojemność jednej części przy obecnej liczbie części (160/153 albo 70/67). */
        val perSegment: Int,
        val visibleChars: Int,
    ) {
        /** „20/70 · 1 SMS” — jednostki w bieżącej części / pojemność · liczba części. */
        val label: String
            get() {
                val used = if (segments <= 1) units else units - (segments - 1) * perSegment
                return "$used/$perSegment · $segments SMS"
            }
    }

    fun isGsm(c: Char) = c in GSM_BASIC || c in GSM_EXTENDED

    fun count(text: String): Count {
        val visible = text.codePointCount(0, text.length)
        val gsm = text.all(::isGsm)
        return if (gsm) {
            val units = text.sumOf { if (it in GSM_EXTENDED) 2 else 1 }
            segments(Encoding.Gsm7, units, 160, 153, visible)
        } else {
            segments(Encoding.Ucs2, text.length, 70, 67, visible)
        }
    }

    private fun segments(e: Encoding, units: Int, single: Int, multi: Int, visible: Int): Count =
        if (units <= single) Count(e, units, if (units == 0) 0 else 1, single, visible)
        else Count(e, units, (units + multi - 1) / multi, multi, visible)

    /** Podgląd bez polskich znaków (tylko na życzenie użytkownika; oryginał zostaje w szkicu). */
    fun transliteratePolish(text: String): String = text.map {
        when (it) {
            'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'; 'ó' -> 'o'; 'ś' -> 's'; 'ź' -> 'z'; 'ż' -> 'z'
            'Ą' -> 'A'; 'Ć' -> 'C'; 'Ę' -> 'E'; 'Ł' -> 'L'; 'Ń' -> 'N'; 'Ó' -> 'O'; 'Ś' -> 'S'; 'Ź' -> 'Z'; 'Ż' -> 'Z'
            else -> it
        }
    }.joinToString("")
}
