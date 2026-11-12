package app.chatra.core.model

/** Kompozycje S09. Tylko Szkło ma pełny wzorzec; pozostałe wymagają osobnego odbioru. */
enum class Composition { Glass, Mist, Graphite, Contrast }

enum class ThemeMode { Light, Dark, Auto }

enum class Density { Standard, Compact }

data class AppearancePrefs(
    val composition: Composition = Composition.Glass,
    val mode: ThemeMode = ThemeMode.Light,
    val density: Density = Density.Standard,
    /** Krok tekstu 1–7, domyślnie 4 (mnożnik 1.00). */
    val textStep: Int = 4,
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
) {
    val textScale: Float get() = TEXT_SCALES[(textStep - 1).coerceIn(0, 6)]

    companion object {
        /** Robocze mnożniki z S09; współdziałają ze skalą systemową. */
        val TEXT_SCALES = floatArrayOf(0.85f, 0.90f, 0.95f, 1.00f, 1.10f, 1.20f, 1.30f)
        val Default = AppearancePrefs()
    }
}

data class SendingPrefs(
    val defaultSubscriptionId: SubscriptionId? = null,
    /** Opóźnienie wysyłki 0/3/5/10 s; domyślnie 3. */
    val undoSeconds: Int = 3,
    val smsCounterEnabled: Boolean = true,
    val hdDefault: MediaVariant = MediaVariant.OriginalHd,
    val defaultSubscriptionLabel: String? = null,
    val askBeforeLink: Boolean = true,
) {
    companion object { val UNDO_OPTIONS = listOf(0, 3, 5, 10) }
}

data class Template(val id: TemplateId, val title: String, val body: String, val sortOrder: Int)

data class BlockRule(val address: Address, val systemWide: Boolean, val createdAtMillis: Long)

/** Null fields inherit global values. Global accessibility cannot be weakened by a thread. */
data class AppearanceOverride(
    val composition: Composition? = null,
    val mode: ThemeMode? = null,
    val density: Density? = null,
    val textStep: Int? = null,
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
)
fun resolveAppearance(global: AppearancePrefs, thread: AppearanceOverride?): AppearancePrefs = if (thread == null) global else global.copy(
    composition = thread.composition ?: global.composition,
    mode = thread.mode ?: global.mode,
    density = thread.density ?: global.density,
    textStep = maxOf(thread.textStep?.coerceIn(1, 7) ?: global.textStep, if (global.textStep > 4) global.textStep else 1),
    reduceTransparency = global.reduceTransparency || thread.reduceTransparency,
    increaseContrast = global.increaseContrast || thread.increaseContrast,
)
