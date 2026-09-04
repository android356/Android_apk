package com.autodeploy.infinityfree.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.data.preferences.SyncControlState
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
            repo.observeActiveProject().collect { project ->
                _uiState.update { current ->
                    current.copy(
                        projectName = project?.projectName,
                        projectFolderUri = project?.folderUri
                    )
                }
            }
        }
        viewModelScope.launch {
            prefs.isAutoSyncEnabled.collect { autoSync ->
                _uiState.update { it.copy(isAutoSyncOn = autoSync) }
            }
        }
        viewModelScope.launch {
            prefs.syncControlState.collect { controlState ->
                _uiState.update { it.copy(syncControlState = controlState) }
            }
        }
        viewModelScope.launch {
            prefs.lastScanTimestamp.collect { lastScan ->
                _uiState.update { it.copy(lastScanTime = lastScan) }
            }
        }
        viewModelScope.launch {
            prefs.lastSuccessfulSyncTimestamp.collect { lastSync ->
                _uiState.update { it.copy(lastSuccessfulSyncTime = lastSync) }
            }
        }
        viewModelScope.launch {
            prefs.lastGitHubSyncTimestamp.collect { lastGh ->
                _uiState.update { it.copy(lastGitHubSyncTime = lastGh) }
            }
        }
        viewModelScope.launch {
            prefs.lastInfinityFreeSyncTimestamp.collect { lastIf ->
                _uiState.update { it.copy(lastInfinityFreeSyncTime = lastIf) }
            }
        }
        viewModelScope.launch {
            prefs.currentActivityState.collect { activity ->
                _uiState.update { it.copy(currentActivity = activity) }
            }
        }

        viewModelScope.launch {
            repo.observeActiveProject().map { it?.id }.filterNotNull().distinctUntilChanged().collectLatest { projectId ->
                // Observe GitHub connection
                launch {
                    repo.observeGitHubConnection(projectId).collect { conn ->
                        _uiState.update { current ->
                            current.copy(
                                isGitHubConfigured = conn != null,
                                gitHubOwner = conn?.owner,
                                gitHubRepo = conn?.repo,
                                gitHubBranch = conn?.branch,
                                gitHubPath = conn?.destinationPath,
                                gitHubStatus = if (conn != null) "Configured (${conn.owner}/${conn.repo})" else "Not Configured"
                            )
                        }
                    }
                }
                // Observe Hosting connection
                launch {
                    repo.observeConnectionForProject(projectId).collect { conn ->
                        _uiState.update { current ->
                            current.copy(
                                hostingConnectionName = conn?.connectionName,
                                hostingServer = conn?.server,
                                isHostingConfigured = conn != null,
                                hostingStatus = if (conn != null) "Configured (${conn.server})" else "Not Configured"
                            )
                        }
                    }
                }
                // Observe counts
                launch { repo.observeFileCount(projectId).collect { c -> _uiState.update { it.copy(totalFiles = c) } } }
                launch { repo.observeFolderCount(projectId).collect { c -> _uiState.update { it.copy(totalFolders = c) } } }
                launch { repo.observePendingCount(projectId).collect { c -> _uiState.update { it.copy(pendingQueueCount = c) } } }
                launch { repo.observeFailedCount(projectId).collect { c -> _uiState.update { it.copy(failedQueueCount = c) } } }
                launch { repo.observeConflictCount(projectId).collect { c -> _uiState.update { it.copy(conflictCount = c) } } }
                launch { repo.observeActiveBackupCount(projectId).collect { c -> _uiState.update { it.copy(activeBackupCount = c) } } }
            }
        }
    }

    fun observeConflictCount(): Flow<Int> = _uiState.map { it.conflictCount }
    fun observeActiveBackupCount(): Flow<Int> = _uiState.map { it.activeBackupCount }
    fun observeConflictCount(projectId: Long): Flow<Int> = repo.observeConflictCount(projectId)
    fun observeActiveBackupCount(projectId: Long): Flow<Int> = repo.observeActiveBackupCount(projectId)

    fun startSync() {
        coordinator.startSync()
        _uiState.update { it.copy(userMessage = "Auto Sync Started") }
    }

    fun stopSync() {
        coordinator.stopSync()
        _uiState.update { it.copy(userMessage = "Auto Sync Stopped") }
    }

    fun pauseSync() {
        coordinator.pauseSync()
        _uiState.update { it.copy(userMessage = "Sync Paused") }
    }

    fun resumeSync() {
        coordinator.resumeSync()
        _uiState.update { it.copy(userMessage = "Sync Resumed") }
    }

    fun emergencyStop() {
        coordinator.emergencyStop()
        _uiState.update { it.copy(userMessage = "EMERGENCY STOP ACTIVATED! All sync halted.") }
    }

    fun toggleAutoSync(enable: Boolean) {
        if (enable) startSync() else stopSync()
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
