package com.autodeploy.infinityfree.ui.backups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodeploy.infinityfree.AutoDeployApplication
import com.autodeploy.infinityfree.data.local.entity.TemporaryBackupEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BackupsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AutoDeployApplication
    private val repo = app.container.repository
    private val coordinator = app.container.syncCoordinator

    @OptIn(ExperimentalCoroutinesApi::class)
    val backups: StateFlow<List<TemporaryBackupEntity>> = repo.observeActiveProject()
        .filterNotNull()
        .flatMapLatest { repo.observeAvailableBackups(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rollbackStatus = MutableStateFlow<String?>(null)

    fun restoreBackup(backup: TemporaryBackupEntity) {
        viewModelScope.launch {
            rollbackStatus.value = "Restoring ${backup.relativePath}..."
            val success = coordinator.rollbackBackup(backup.id)
            rollbackStatus.value = if (success) "Restored and queued for remote upload!" else "Failed to restore backup"
        }
    }

    fun clearRollbackStatus() {
        rollbackStatus.value = null
    }
}
