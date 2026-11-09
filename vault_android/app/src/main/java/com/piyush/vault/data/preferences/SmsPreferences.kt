package com.piyush.vault.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.piyush.vault.domain.model.SmsSettings
import com.piyush.vault.domain.model.SmsCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*

private val Context.smsPreferences by preferencesDataStore("sms_settings")

@Singleton
class SmsPreferences(private val store: androidx.datastore.core.DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context): this(context.smsPreferences)
    val settings = store.data.map { p -> SmsSettings(SmsCapabilities.HISTORY_IMPORT_ENABLED && (p[HISTORY] ?: false),p[LIVE] ?: false,p[GENERATION] ?: 0,p[CONSENT_TIME],p[CONSENT_VERSION] ?: 0,p[STATUS].orEmpty(),p[NOTIFICATIONS] ?: false) }
    suspend fun notifications(enabled: Boolean) { store.edit { it[NOTIFICATIONS] = enabled } }
    suspend fun snapshot() = settings.first()
    suspend fun scopes(history: Boolean,live: Boolean,now: Long) {
        require(!history || SmsCapabilities.HISTORY_IMPORT_ENABLED) { "Historical SMS import is disabled for now." }
        store.edit { p -> p[HISTORY] = history; p[LIVE] = live; p[GENERATION] = Math.addExact(p[GENERATION] ?: 0L,1L); p[CONSENT_VERSION] = 1; p[CONSENT_TIME] = now; p[STATUS] = ""; p.remove(SCAN_TOKEN) }
    }
    suspend fun status(value: String) { store.edit { it[STATUS] = value } }
    suspend fun newScan(token: String,start: Long,end: Long) { store.edit { p -> p[SCAN_TOKEN] = token; p[START] = start; p[END] = end; p[CURSOR_DATE] = start-1; p[CURSOR_ID] = -1; p[COUNT] = 0; p[STATUS] = "History scan queued" } }
    suspend fun scan(): Scan? = store.data.first().let { p -> p[SCAN_TOKEN]?.let { Scan(it,p[START] ?: 0,p[END] ?: 0,p[CURSOR_DATE] ?: 0,p[CURSOR_ID] ?: -1,p[COUNT] ?: 0) } }
    suspend fun checkpoint(token: String,date: Long,id: Long,count: Int) { store.edit { p -> if (p[SCAN_TOKEN] == token) { p[CURSOR_DATE] = date; p[CURSOR_ID] = id; p[COUNT] = count; p[STATUS] = "Scanned $count messages" } } }
    suspend fun complete(token: String) { store.edit { p -> if (p[SCAN_TOKEN] == token) { p.remove(SCAN_TOKEN); p[STATUS] = "History scan complete; review detected activity" } } }
    data class Scan(val token: String,val start: Long,val end: Long,val cursorDate: Long,val cursorId: Long,val count: Int)
    private companion object {
        val NOTIFICATIONS = booleanPreferencesKey("review_notifications")
        val HISTORY = booleanPreferencesKey("history"); val LIVE = booleanPreferencesKey("live")
        val GENERATION = longPreferencesKey("generation"); val CONSENT_TIME = longPreferencesKey("consented_at")
        val CONSENT_VERSION = intPreferencesKey("consent_version"); val STATUS = stringPreferencesKey("status")
        val SCAN_TOKEN = stringPreferencesKey("scan_token"); val START = longPreferencesKey("start"); val END = longPreferencesKey("end")
        val CURSOR_DATE = longPreferencesKey("cursor_date"); val CURSOR_ID = longPreferencesKey("cursor_id"); val COUNT = intPreferencesKey("count")
    }
}
