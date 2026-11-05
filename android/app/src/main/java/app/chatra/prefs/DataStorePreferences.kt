package app.chatra.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.chatra.core.model.AppearancePrefs
import app.chatra.core.model.Composition
import app.chatra.core.model.Density
import app.chatra.core.model.MediaVariant
import app.chatra.core.model.SendingPrefs
import app.chatra.core.model.SubscriptionId
import app.chatra.core.model.ThemeMode
import app.chatra.di.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

private val Context.demoStore by preferencesDataStore("demo")
private val Context.fullStore by preferencesDataStore("chatra")
private val Context.referenceStore by preferencesDataStore("demo-reference")

/** Wygląd (S09) i ustawienia wysyłania (S10) w DataStore; zmiany stosowane od razu (CH040, CH041). */
class DataStorePreferences(context: Context, scope: CoroutineScope, name: String) {
    private val store: DataStore<Preferences> = when (name) {
        "demo" -> context.demoStore
        "demo-reference" -> context.referenceStore
        else -> context.fullStore
    }

    val appearance: PreferenceStore<AppearancePrefs> = Store(scope, ::readAppearance, ::writeAppearance)
    val sending: PreferenceStore<SendingPrefs> = Store(scope, ::readSending, ::writeSending)

    private inner class Store<T>(
        scope: CoroutineScope,
        private val read: (Preferences) -> T,
        private val write: (androidx.datastore.preferences.core.MutablePreferences, T) -> Unit,
    ) : PreferenceStore<T> {
        // Pierwszy odczyt synchronicznie: ekran startuje od razu z zapisanym wyglądem, bez mignięcia.
        override val value: StateFlow<T> = store.data.map(read).stateIn(scope, SharingStarted.Eagerly, runBlocking { read(store.data.first()) })

        override suspend fun update(transform: (T) -> T) {
            store.edit { prefs -> write(prefs, transform(read(prefs))) }
        }
    }

    private object K {
        val composition = stringPreferencesKey("composition")
        val mode = stringPreferencesKey("mode")
        val density = stringPreferencesKey("density")
        val textStep = intPreferencesKey("text_step")
        val reduceTransparency = booleanPreferencesKey("reduce_transparency")
        val increaseContrast = booleanPreferencesKey("increase_contrast")
        val defaultSim = intPreferencesKey("default_sim")
        val undoSeconds = intPreferencesKey("undo_seconds")
        val smsCounter = booleanPreferencesKey("sms_counter")
        val hdDefault = stringPreferencesKey("hd_default")
    }

    private inline fun <reified E : Enum<E>> enumOr(v: String?, def: E): E = v?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def

    private fun readAppearance(p: Preferences) = AppearancePrefs.Default.let { d ->
        AppearancePrefs(
            enumOr(p[K.composition], d.composition), enumOr(p[K.mode], d.mode), enumOr(p[K.density], d.density),
            (p[K.textStep] ?: d.textStep).coerceIn(1, 7), p[K.reduceTransparency] ?: d.reduceTransparency, p[K.increaseContrast] ?: d.increaseContrast,
        )
    }

    private fun writeAppearance(p: androidx.datastore.preferences.core.MutablePreferences, v: AppearancePrefs) {
        p[K.composition] = v.composition.name
        p[K.mode] = v.mode.name
        p[K.density] = v.density.name
        p[K.textStep] = v.textStep
        p[K.reduceTransparency] = v.reduceTransparency
        p[K.increaseContrast] = v.increaseContrast
    }

    private fun readSending(p: Preferences) = SendingPrefs(
        p[K.defaultSim]?.let { SubscriptionId(it) },
        (p[K.undoSeconds] ?: 3).takeIf { it in SendingPrefs.UNDO_OPTIONS } ?: 3,
        p[K.smsCounter] ?: true,
        enumOr(p[K.hdDefault], MediaVariant.OriginalHd),
    )

    private fun writeSending(p: androidx.datastore.preferences.core.MutablePreferences, v: SendingPrefs) {
        v.defaultSubscriptionId?.let { p[K.defaultSim] = it.value } ?: p.remove(K.defaultSim)
        p[K.undoSeconds] = v.undoSeconds
        p[K.smsCounter] = v.smsCounterEnabled
        p[K.hdDefault] = v.hdDefault.name
    }
}

