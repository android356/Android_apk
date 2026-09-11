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
    val backupRetentionCount: Int = 10,
    val isAutoRollbackEnabled: Boolean = false,
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
                combine(
                    prefs.debounceDurationSeconds,
                    prefs.reconciliationIntervalSeconds,
                    prefs.syncDeletions
                ) { debounce, interval, deletions ->
                    Triple(debounce, interval, deletions)
                },
                combine(
                    prefs.backupRetentionMinutes,
                    prefs.backupRetentionCount,
                    prefs.isAutoRollbackEnabled,
                    prefs.customIgnorePatterns
                ) { retention, count, autoRollback, ignores ->
                    Tuple4(retention, count, autoRollback, ignores)
                }
            ) { t1, t2 ->
                SyncSettingsUiState(
                    debounceSeconds = t1.first,
                    reconciliationIntervalSeconds = t1.second,
                    syncDeletions = t1.third,
                    backupRetentionMinutes = t2.a,
                    backupRetentionCount = t2.b,
                    isAutoRollbackEnabled = t2.c,
                    ignorePatternsText = t2.d.joinToString("\n")
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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

    fun setBackupRetentionCount(count: Int) {
        viewModelScope.launch { prefs.setBackupRetentionCount(count) }
    }

    fun setAutoRollbackEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoRollbackEnabled(enabled) }
    }

    fun onIgnorePatternsTextChange(text: String) {
        _uiState.update { it.copy(ignorePatternsText = text) }
    }

    fun saveIgnorePatterns() {
        val lines = _uiState.value.ignorePatternsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch { prefs.setCustomIgnorePatterns(lines) }
    }
}
