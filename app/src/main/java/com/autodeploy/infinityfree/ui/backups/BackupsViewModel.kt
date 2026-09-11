package com.autodeploy.infinityfree.ui.backups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.data.local.entity.BackupSnapshotEntity
import com.autodeploy.infinityfree.data.local.entity.TemporaryBackupEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BackupsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutoDeployApplication
    private val repo = app.container.repository
    private val coordinator = app.container.syncCoordinator
    private val backupManager = app.container.backupManager

    val selectedTab = MutableStateFlow(0) // 0 = Versioned Snapshots, 1 = Temporary Backups

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshots: StateFlow<List<BackupSnapshotEntity>> = repo.observeActiveProject()
        .filterNotNull()
        .flatMapLatest { repo.observeSnapshots(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val backups: StateFlow<List<TemporaryBackupEntity>> = repo.observeActiveProject()
        .filterNotNull()
        .flatMapLatest { repo.observeAvailableBackups(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statusMessage = MutableStateFlow<String?>(null)
    val isCreatingSnapshot = MutableStateFlow(false)

    fun selectTab(index: Int) {
        selectedTab.value = index
    }

    fun createManualSnapshot(label: String = "Manual Snapshot") {
        viewModelScope.launch {
            isCreatingSnapshot.value = true
            statusMessage.value = "Creating snapshot: $label..."
            val success = coordinator.createManualSnapshot(label)
            isCreatingSnapshot.value = false
            statusMessage.value = if (success) "Snapshot created successfully!" else "Failed to create snapshot"
        }
    }

    fun restoreSnapshot(snapshot: BackupSnapshotEntity) {
        viewModelScope.launch {
            statusMessage.value = "Restoring snapshot ${snapshot.versionTag}..."
            val success = coordinator.rollbackSnapshot(snapshot.id)
            statusMessage.value = if (success) {
                "Snapshot ${snapshot.versionTag} restored! Emergency snapshot created and deployment queued."
            } else {
                "Failed to restore snapshot ${snapshot.versionTag}"
            }
        }
    }

    fun rollbackToLastStable() {
        viewModelScope.launch {
            statusMessage.value = "Rolling back to last stable version..."
            val success = coordinator.rollbackToLastStableSnapshot()
            statusMessage.value = if (success) {
                "Successfully restored last stable version and queued deployment!"
            } else {
                "No stable version found or rollback failed"
            }
        }
    }

    fun toggleStable(snapshot: BackupSnapshotEntity) {
        viewModelScope.launch {
            val newStable = !snapshot.isStable
            repo.setSnapshotStable(snapshot.id, newStable)
            statusMessage.value = if (newStable) {
                "Marked ${snapshot.versionTag} as STABLE (protected from retention cleanup)"
            } else {
                "Unmarked ${snapshot.versionTag} as stable"
            }
        }
    }

    fun verifySnapshot(snapshot: BackupSnapshotEntity) {
        viewModelScope.launch {
            val verified = backupManager.verifySnapshotIntegrity(snapshot)
            statusMessage.value = if (verified) {
                "Integrity verified: All ${snapshot.fileCount} files in ${snapshot.versionTag} match SHA-256 manifest!"
            } else {
                "Integrity error: Snapshot ${snapshot.versionTag} file hashes or sizes mismatch!"
            }
        }
    }

    fun deleteSnapshot(snapshot: BackupSnapshotEntity) {
        viewModelScope.launch {
            repo.deleteSnapshot(snapshot.id)
            statusMessage.value = "Snapshot ${snapshot.versionTag} removed"
        }
    }

    fun restoreBackup(backup: TemporaryBackupEntity) {
        viewModelScope.launch {
            statusMessage.value = "Restoring ${backup.relativePath}..."
            val success = coordinator.rollbackBackup(backup.id)
            statusMessage.value = if (success) "Restored and queued for active target upload!" else "Failed to restore backup"
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }
}

