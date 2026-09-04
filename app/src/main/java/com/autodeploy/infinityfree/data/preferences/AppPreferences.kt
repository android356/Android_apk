package com.autodeploy.infinityfree.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_deploy_prefs")

enum class SyncControlState {
    ACTIVE,
    STOPPED,
    PAUSED,
    EMERGENCY_STOPPED
}

class AppPreferences(private val context: Context) {

    private object PreferencesKeys {
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_CONTROL_STATE = stringPreferencesKey("sync_control_state")
        val DEBOUNCE_DURATION_SEC = intPreferencesKey("debounce_duration_sec")
        val RECONCILIATION_INTERVAL_SEC = intPreferencesKey("reconciliation_interval_sec")
        val SYNC_DELETIONS = booleanPreferencesKey("sync_deletions")
        val BACKUP_RETENTION_MINUTES = intPreferencesKey("backup_retention_minutes")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        val LAST_SUCCESSFUL_SYNC_TIMESTAMP = longPreferencesKey("last_successful_sync_timestamp")
        val LAST_GITHUB_SYNC_TIMESTAMP = longPreferencesKey("last_github_sync_timestamp")
        val LAST_INFINITYFREE_SYNC_TIMESTAMP = longPreferencesKey("last_infinityfree_sync_timestamp")
        val CURRENT_ACTIVITY_STATE = stringPreferencesKey("current_activity_state")
        val SYNC_PROGRESS_TEXT = stringPreferencesKey("sync_progress_text")
        val CUSTOM_IGNORE_PATTERNS = stringPreferencesKey("custom_ignore_patterns")
    }

    val isAutoSyncEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.AUTO_SYNC_ENABLED] ?: false }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_SYNC_ENABLED] = enabled }
    }

    val syncControlState: Flow<SyncControlState> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map {
            val raw = it[PreferencesKeys.SYNC_CONTROL_STATE] ?: SyncControlState.STOPPED.name
            try {
                SyncControlState.valueOf(raw)
            } catch (e: Exception) {
                SyncControlState.STOPPED
            }
        }

    suspend fun setSyncControlState(state: SyncControlState) {
        context.dataStore.edit {
            it[PreferencesKeys.SYNC_CONTROL_STATE] = state.name
            it[PreferencesKeys.AUTO_SYNC_ENABLED] = (state == SyncControlState.ACTIVE)
        }
    }

    val debounceDurationSeconds: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.DEBOUNCE_DURATION_SEC] ?: 3 }

    suspend fun setDebounceDurationSeconds(seconds: Int) {
        context.dataStore.edit { it[PreferencesKeys.DEBOUNCE_DURATION_SEC] = seconds }
    }

    val reconciliationIntervalSeconds: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.RECONCILIATION_INTERVAL_SEC] ?: 30 }

    suspend fun setReconciliationIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[PreferencesKeys.RECONCILIATION_INTERVAL_SEC] = seconds }
    }

    val syncDeletions: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.SYNC_DELETIONS] ?: false }

    suspend fun setSyncDeletions(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SYNC_DELETIONS] = enabled }
    }

    val backupRetentionMinutes: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.BACKUP_RETENTION_MINUTES] ?: 60 }

    suspend fun setBackupRetentionMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.BACKUP_RETENTION_MINUTES] = minutes }
    }

    val lastScanTimestamp: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.LAST_SCAN_TIMESTAMP] ?: 0L }

    suspend fun setLastScanTimestamp(timestamp: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_SCAN_TIMESTAMP] = timestamp }
    }

    val lastSuccessfulSyncTimestamp: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.LAST_SUCCESSFUL_SYNC_TIMESTAMP] ?: 0L }

    suspend fun setLastSuccessfulSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_SUCCESSFUL_SYNC_TIMESTAMP] = timestamp }
    }

    val lastGitHubSyncTimestamp: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.LAST_GITHUB_SYNC_TIMESTAMP] ?: 0L }

    suspend fun setLastGitHubSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_GITHUB_SYNC_TIMESTAMP] = timestamp }
    }

    val lastInfinityFreeSyncTimestamp: Flow<Long> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.LAST_INFINITYFREE_SYNC_TIMESTAMP] ?: 0L }

    suspend fun setLastInfinityFreeSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { it[PreferencesKeys.LAST_INFINITYFREE_SYNC_TIMESTAMP] = timestamp }
    }

    val currentActivityState: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.CURRENT_ACTIVITY_STATE] ?: "Idle" }

    suspend fun setCurrentActivityState(state: String) {
        context.dataStore.edit { it[PreferencesKeys.CURRENT_ACTIVITY_STATE] = state }
    }

    val syncProgressText: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.SYNC_PROGRESS_TEXT] ?: "Idle" }

    suspend fun setSyncProgressText(text: String) {
        context.dataStore.edit { it[PreferencesKeys.SYNC_PROGRESS_TEXT] = text }
    }

    val customIgnorePatterns: Flow<List<String>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map {
            val raw = it[PreferencesKeys.CUSTOM_IGNORE_PATTERNS] ?: ""
            if (raw.isEmpty()) emptyList() else raw.split("\n").filter { p -> p.isNotBlank() }
        }

    suspend fun setCustomIgnorePatterns(patterns: List<String>) {
        context.dataStore.edit {
            it[PreferencesKeys.CUSTOM_IGNORE_PATTERNS] = patterns.joinToString("\n")
        }
    }
}
