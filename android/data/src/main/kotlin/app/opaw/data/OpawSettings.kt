package app.opaw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.opaw.core.OpawFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.opawPrefs: DataStore<Preferences> by preferencesDataStore(name = "opaw")

/** Settings shared between the app, the widget and the walk service. Mirrors `OpawDefaults` on iOS. */
class OpawSettings(context: Context) {
    private val store = context.applicationContext.opawPrefs

    private object Keys {
        val activeDog = stringPreferencesKey("activeDogID")
        /** The name shown when this phone logs care, such as "Sam". */
        val caretaker = stringPreferencesKey("caretakerName")
        val imperial = booleanPreferencesKey("usesImperialUnits")
        val soundsOn = booleanPreferencesKey("soundsOn")
        val hapticsOn = booleanPreferencesKey("hapticsOn")
    }

    /** The dog currently shown on Home and targeted by quick logs. */
    val activeDogId: Flow<String?> = store.data.map { it[Keys.activeDog]?.takeIf(String::isNotEmpty) }.distinctUntilChanged()
    val caretakerName: Flow<String> = store.data.map { it[Keys.caretaker].orEmpty() }.distinctUntilChanged()
    val usesImperial: Flow<Boolean> = store.data.map { it[Keys.imperial] ?: false }.distinctUntilChanged()
    val soundsOn: Flow<Boolean> = store.data.map { it[Keys.soundsOn] ?: true }.distinctUntilChanged()
    val hapticsOn: Flow<Boolean> = store.data.map { it[Keys.hapticsOn] ?: true }.distinctUntilChanged()

    suspend fun setActiveDog(id: String?) = store.edit { it[Keys.activeDog] = id.orEmpty() }
    suspend fun setCaretakerName(name: String) = store.edit { it[Keys.caretaker] = name }
    suspend fun setSoundsOn(on: Boolean) = store.edit { it[Keys.soundsOn] = on }
    suspend fun setHapticsOn(on: Boolean) = store.edit { it[Keys.hapticsOn] = on }

    suspend fun setUsesImperial(on: Boolean) {
        store.edit { it[Keys.imperial] = on }
        OpawFormat.usesImperial = on
    }

    /** Copies the Units setting into `OpawFormat` so formatting matches. Call once at launch. */
    suspend fun syncFormat() {
        OpawFormat.usesImperial = usesImperial.first()
    }

    suspend fun clear() = store.edit { it.clear() }
}
