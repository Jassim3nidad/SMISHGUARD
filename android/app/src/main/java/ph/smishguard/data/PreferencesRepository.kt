package ph.smishguard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import ph.smishguard.privacy.City
import java.io.IOException

private val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val onboarded: Boolean = false, val scanning: Boolean = false, val city: City = City.NOT_SELECTED)
class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.preferences
    private val consent = booleanPreferencesKey("onboarded")
    private val scanning = booleanPreferencesKey("incoming_sms_consent")
    private val city = stringPreferencesKey("manual_city")
    val flow = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        Preferences(it[consent] ?: false, it[scanning] ?: false,
            runCatching { City.valueOf(it[city] ?: "NOT_SELECTED") }.getOrDefault(City.NOT_SELECTED))
    }
    suspend fun onboard() { store.edit { it[consent] = true } }
    suspend fun scanning(enabled: Boolean) { store.edit { it[scanning] = enabled } }
    suspend fun city(value: City) { store.edit { it[city] = value.name } }
    suspend fun clear() { store.edit { it.clear() } }
}
