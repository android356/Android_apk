package com.autodeploy.infinityfree.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.service.AutoSyncForegroundService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoDeployApplication
    private val repo = app.container.repository
    private val coordinator = app.container.syncCoordinator
    private val prefs = app.container.preferences

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                repo.observeActiveProject(),
                prefs.isAutoSyncEnabled,
                prefs.lastScanTimestamp,
                prefs.lastSuccessfulSyncTimestamp,
                prefs.currentActivityState
            ) { project, autoSync, lastScan, lastSync, activity ->
                _uiState.update { current ->
                    current.copy(
                        projectName = project?.projectName,
                        projectFolderUri = project?.folderUri,
                        isAutoSyncOn = autoSync,
                        lastScanTime = lastScan,
                        lastSuccessfulSyncTime = lastSync,
                        currentActivity = activity
                    )
                }
                project?.id
            }.filterNotNull().collectLatest { projectId ->
                // Observe project specific metrics
                launch {
                    repo.observeConnectionForProject(projectId).collect { conn ->
                        _uiState.update { current ->
                            current.copy(
                                hostingConnectionName = conn?.connectionName,
                                hostingServer = conn?.server,
                                isHostingConfigured = conn != null,
                                hostingStatus = if (conn != null) "Configured" else "Not Configured"
                            )
                        }
                    }
                }
                launch {
                    repo.observeFileCount(projectId).collect { count ->
                        _uiState.update { it.copy(totalFiles = count) }
                    }
                }
                launch {
                    repo.observeFolderCount(projectId).collect { count ->
                        _uiState.update { it.copy(totalFolders = count) }
                    }
                }
                launch {
                    repo.observePendingCount(projectId).collect { count ->
                        _uiState.update { it.copy(pendingQueueCount = count) }
                    }
                }
                launch {
                    repo.observeFailedCount(projectId).collect { count ->
                        _uiState.update { it.copy(failedQueueCount = count) }
                    }
                }
                launch {
                    repo.observeActiveBackupCount(projectId).collect { count ->
                        _uiState.update { it.copy(activeBackupCount = count) }
                    }
                }
            }
        }
    }

    fun toggleAutoSync(enable: Boolean) {
        viewModelScope.launch {
            prefs.setAutoSyncEnabled(enable)
            if (enable) {
                AutoSyncForegroundService.start(getApplication())
            } else {
                AutoSyncForegroundService.stop(getApplication())
            }
        }
    }

    fun triggerSyncNow() {
        if (_uiState.value.isSyncingNow) return
        _uiState.update { it.copy(isSyncingNow = true, userMessage = "Starting Manual Sync...") }

        coordinator.triggerManualSync { success, message ->
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isSyncingNow = false,
                        userMessage = if (success) "Sync Succeeded: $message" else "Sync Failed: $message"
                    )
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
