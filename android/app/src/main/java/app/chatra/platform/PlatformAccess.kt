package app.chatra.platform

import kotlinx.coroutines.flow.StateFlow

enum class AccessFeature(val title: String, val explanation: String) {
    Contacts("Kontakty", "Nazwy i sugestie pojawią się podczas dodawania odbiorcy. Możesz nadal wpisywać numery ręcznie."),
    Sims("Karty SIM", "Dostęp do stanu telefonu pozwala wyświetlić aktywne karty. Nie odczytujemy numeru Twojej karty."),
    Notifications("Powiadomienia", "Pozwalają pokazywać nowe wiadomości i problemy wysyłki. Możesz odmówić i korzystać z aplikacji."),
    Sms("Dostęp do SMS", "Rola domyślnej aplikacji i dostęp do wiadomości są sprawdzane oddzielnie."),
}
enum class AccessResult { Granted, Denied, SettingsRequired, RoleRequired, Unavailable }

interface PlatformAccess {
    val changes: StateFlow<Int>
    fun allowed(feature: AccessFeature): Boolean
    suspend fun request(feature: AccessFeature): AccessResult
    fun openSettings()
    fun refresh()
}
