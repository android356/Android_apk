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
    val backupRetentionMinutes: Int = 60,
    val ignorePatternsText: String = ""
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
                prefs.backupRetentionMinutes,
                prefs.customIgnorePatterns
            ) { debounce, interval, deletions, retention, ignores ->
                SyncSettingsUiState(
                    debounceSeconds = debounce,
                    reconciliationIntervalSeconds = interval,
                    syncDeletions = deletions,
                    backupRetentionMinutes = retention,
                    ignorePatternsText = ignores.joinToString("\n")
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

    fun onIgnorePatternsTextChange(text: String) {
        _uiState.update { it.copy(ignorePatternsText = text) }
    }

    fun saveIgnorePatterns() {
        val lines = _uiState.value.ignorePatternsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch { prefs.setCustomIgnorePatterns(lines) }
    }
}
