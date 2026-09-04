package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.preferences.SyncControlState
import com.autodeploy.infinityfree.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class SyncCoordinator(
    private val context: Context,
    private val repository: AppRepository,
    private val scanner: ReconciliationScanner,
    private val queueProcessor: SyncQueueProcessor,
    private val backupManager: BackupManager,
    private val preferences: AppPreferences
) {
    companion object {
        private const val TAG = "SyncCoordinator"
        @Volatile
        private var INSTANCE: SyncCoordinator? = null

        fun getInstance(
            context: Context,
            repository: AppRepository,
            scanner: ReconciliationScanner,
            queueProcessor: SyncQueueProcessor,
            backupManager: BackupManager,
            preferences: AppPreferences
        ): SyncCoordinator {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncCoordinator(
                    context.applicationContext,
                    repository,
                    scanner,
                    queueProcessor,
                    backupManager,
                    preferences
                )
                INSTANCE = instance
                instance
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()

    fun startSync() {
        scope.launch {
            preferences.setSyncControlState(SyncControlState.ACTIVE)
            AutoSyncForegroundService.start(context)
            triggerManualSync()
        }
    }

    fun stopSync() {
        scope.launch {
            preferences.setSyncControlState(SyncControlState.STOPPED)
            AutoSyncForegroundService.stop(context)
            preferences.setCurrentActivityState("Stopped")
        }
    }

    fun pauseSync() {
        scope.launch {
            preferences.setSyncControlState(SyncControlState.PAUSED)
            preferences.setCurrentActivityState("Paused")
        }
    }

    fun resumeSync() {
        scope.launch {
            preferences.setSyncControlState(SyncControlState.ACTIVE)
            triggerManualSync()
        }
    }

    fun emergencyStop() {
        scope.launch {
            // Immediate full stop
            preferences.setSyncControlState(SyncControlState.EMERGENCY_STOPPED)
            AutoSyncForegroundService.stop(context)
            preferences.setCurrentActivityState("EMERGENCY STOPPED")
            preferences.setSyncProgressText("Sync halted by emergency stop")
        }
    }

    fun triggerManualSync(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            val control = preferences.syncControlState.first()
            if (control == SyncControlState.EMERGENCY_STOPPED) {
                onComplete(false, "Cannot sync: System is Emergency Stopped. Resume first.")
                return@launch
            }

            if (!syncMutex.tryLock()) {
                onComplete(false, "Sync already in progress")
                return@launch
            }
            try {
                preferences.setCurrentActivityState("Scanning")
                val project = repository.getActiveProject()
                if (project == null) {
                    preferences.setCurrentActivityState("Error: No Project Selected")
                    onComplete(false, "No project selected")
                    return@launch
                }

                val folderUri = Uri.parse(project.folderUri)
                scanner.performScan(project.id, folderUri, forceAllAsPending = false) { state ->
                    scope.launch { preferences.setCurrentActivityState(state) }
                }

                preferences.setCurrentActivityState("Processing Queue")
                val processed = queueProcessor.processPendingQueue { state ->
                    scope.launch { preferences.setCurrentActivityState(state) }
                }

                backupManager.cleanupExpiredBackups()
                val current = preferences.syncControlState.first()
                if (current != SyncControlState.EMERGENCY_STOPPED && current != SyncControlState.PAUSED) {
                    preferences.setCurrentActivityState("Idle")
                }
                onComplete(true, "Sync complete ($processed items processed)")
            } catch (e: Exception) {
                Log.e(TAG, "Manual sync failed", e)
                preferences.setCurrentActivityState("Error: ${e.localizedMessage}")
                onComplete(false, e.localizedMessage ?: "Sync error")
            } finally {
                syncMutex.unlock()
            }
        }
    }

    suspend fun runReconciliationCycle() {
        val control = preferences.syncControlState.first()
        if (control != SyncControlState.ACTIVE) return

        if (!syncMutex.tryLock()) return
        try {
            val project = repository.getActiveProject() ?: return
            val folderUri = Uri.parse(project.folderUri)
            scanner.performScan(project.id, folderUri, forceAllAsPending = false) { state ->
                scope.launch { preferences.setCurrentActivityState(state) }
            }
            queueProcessor.processPendingQueue { state ->
                scope.launch { preferences.setCurrentActivityState(state) }
            }
            backupManager.cleanupExpiredBackups()
            preferences.setCurrentActivityState("Idle")
        } catch (e: Exception) {
            Log.e(TAG, "Reconciliation cycle failed", e)
            preferences.setCurrentActivityState("Error")
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun retryAllFailed() {
        val project = repository.getActiveProject() ?: return
        repository.retryAllFailed(project.id)
        queueProcessor.processPendingQueue()
    }

    suspend fun rollbackBackup(backupId: Long): Boolean {
        val project = repository.getActiveProject() ?: return false
        val backup = repository.backupDao.getBackupById(backupId) ?: return false

        repository.syncQueueDao.insertItem(
            SyncQueueEntity(
                projectId = project.id,
                relativePath = backup.relativePath,
                operation = "ROLLBACK",
                status = "PENDING"
            )
        )
        queueProcessor.processPendingQueue()
        return true
    }

    suspend fun resolveConflict(queueId: Long, overwriteRemote: Boolean) {
        repository.resolveConflict(queueId, overwriteRemote)
        queueProcessor.processPendingQueue()
    }
}
