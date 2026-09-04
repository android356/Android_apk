package com.autodeploy.infinityfree.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SyncSettingsUiState(
    val debounceSeconds: Int = 3,
    val reconciliationIntervalSeconds: Int = 30,
    val syncDeletions: Boolean = false,
    val backupRetentionMinutes: Int = 60
)

class SyncSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutoDeployApplication
    private val prefs = app.container.preferences

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                prefs.debounceDurationSeconds,
                prefs.reconciliationIntervalSeconds,
                prefs.syncDeletions,
                prefs.backupRetentionMinutes
            ) { debounce, interval, deletions, retention ->
                SyncSettingsUiState(
                    debounceSeconds = debounce,
                    reconciliationIntervalSeconds = interval,
                    syncDeletions = deletions,
                    backupRetentionMinutes = retention
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setDebounceSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setDebounceDurationSeconds(seconds) }
    }

    fun setReconciliationIntervalSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setReconciliationIntervalSeconds(seconds) }
    }

    fun setSyncDeletions(enabled: Boolean) {
        viewModelScope.launch { prefs.setSyncDeletions(enabled) }
    }

    fun setBackupRetentionMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setBackupRetentionMinutes(minutes) }
    }
}
