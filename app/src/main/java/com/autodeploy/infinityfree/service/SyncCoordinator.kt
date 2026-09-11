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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class SyncCoordinator(
    private val context: Context,
    private val repository: AppRepository,
    private val scanner: ReconciliationScanner,
    private val queueProcessor: SyncQueueProcessor,
    private val backupManager: BackupManager,
    private val preferences: AppPreferences,
    private val fileWatcher: FileWatcher = AndroidFileWatcher(),
    private val changeDetector: RealTimeChangeDetector? = null
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
            preferences: AppPreferences,
            fileWatcher: FileWatcher? = null,
            changeDetector: RealTimeChangeDetector? = null
        ): SyncCoordinator {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncCoordinator(
                    context.applicationContext,
                    repository,
                    scanner,
                    queueProcessor,
                    backupManager,
                    preferences,
                    fileWatcher ?: AndroidFileWatcher(),
                    changeDetector
                )
                INSTANCE = instance
                instance
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()
    private val realTimeDetector: RealTimeChangeDetector = changeDetector ?: RealTimeChangeDetector(
        context = context,
        database = repository.projectDao.let { com.autodeploy.infinityfree.data.local.AppDatabase.getInstance(context) },
        preferences = preferences,
        deploymentManager = com.autodeploy.infinityfree.data.deployment.DeploymentManager(
            infinityFreeProvider = com.autodeploy.infinityfree.data.deployment.InfinityFreeProvider(
                com.autodeploy.infinityfree.data.local.AppDatabase.getInstance(context).hostingConnectionDao(),
                com.autodeploy.infinityfree.data.security.SecureStorageManager(context),
                com.autodeploy.infinityfree.data.ftp.FtpClientManager()
            ),
            shrotiHostProvider = com.autodeploy.infinityfree.data.deployment.ShrotiHostCPanelProvider(
                com.autodeploy.infinityfree.data.local.AppDatabase.getInstance(context).shrotiHostConnectionDao(),
                com.autodeploy.infinityfree.data.security.SecureStorageManager(context),
                com.autodeploy.infinityfree.data.ftp.FtpClientManager()
            ),
            githubProvider = com.autodeploy.infinityfree.data.deployment.GitHubDeploymentProvider(
                com.autodeploy.infinityfree.data.local.AppDatabase.getInstance(context).githubConnectionDao(),
                com.autodeploy.infinityfree.data.security.SecureStorageManager(context),
                com.autodeploy.infinityfree.data.github.GitHubClientManager()
            ),
            preferences = preferences,
            syncQueueDao = com.autodeploy.infinityfree.data.local.AppDatabase.getInstance(context).syncQueueDao()
        ),
        stabilityTracker = FileStabilityTracker(),
        queueProcessor = queueProcessor
    )

    // Watcher health & metrics exposed to UI
    val watcherHealthState: StateFlow<WatcherHealthState> = fileWatcher.healthState
    val monitoredFilesCount: StateFlow<Int> = fileWatcher.monitoredFilesCount
    val monitoredDirectoriesCount: StateFlow<Int> = fileWatcher.monitoredDirectoriesCount

    private var currentWatchedProjectId: Long? = null

    init {
        // Observe active project changes to rebind watchers
        scope.launch {
            repository.observeActiveProject().collect { project ->
                if (project != null && project.id != currentWatchedProjectId) {
                    onActiveProjectChanged(project.id)
                }
            }
        }
    }

    fun startSync() {
        scope.launch {
            preferences.setSyncControlState(SyncControlState.ACTIVE)
            AutoSyncForegroundService.start(context)
            startRealTimeWatching()
            triggerManualSync()
        }
    }

    fun stopSync() {
        scope.launch {
            preferences.setSyncControlState(SyncControlState.STOPPED)
            stopRealTimeWatching()
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
            startRealTimeWatching()
            triggerManualSync()
        }
    }

    fun emergencyStop() {
        scope.launch {
            preferences.setSyncControlState(SyncControlState.EMERGENCY_STOPPED)
            stopRealTimeWatching()
            AutoSyncForegroundService.stop(context)
            preferences.setCurrentActivityState("EMERGENCY STOPPED")
            preferences.setSyncProgressText("Sync halted by emergency stop")
        }
    }

    suspend fun startRealTimeWatching(): Boolean {
        val project = repository.getActiveProject() ?: return false
        val rootFile = StoragePathResolver.resolveToFile(context, project.folderUri)

        if (rootFile == null || !rootFile.exists() || !rootFile.isDirectory) {
            Log.e(TAG, "Cannot start real-time watcher: Folder ${project.folderUri} not accessible on filesystem")
            return false
        }

        currentWatchedProjectId = project.id
        realTimeDetector.setActiveProject(project.id)

        val started = fileWatcher.startWatching(rootFile) { event ->
            realTimeDetector.onFileSystemEvent(event, project, rootFile)
        }

        if (started) {
            Log.i(TAG, "Real-time watcher active on ${rootFile.absolutePath} for project '${project.projectName}'")
        }
        return started
    }

    fun stopRealTimeWatching() {
        fileWatcher.stopWatching()
        realTimeDetector.cancelAll()
        Log.i(TAG, "Real-time watcher stopped")
    }

    fun restartWatcher(onComplete: (Boolean) -> Unit = {}) {
        scope.launch {
            Log.i(TAG, "Restarting real-time watcher and running recovery reconciliation...")
            val project = repository.getActiveProject()
            if (project == null) {
                onComplete(false)
                return@launch
            }

            val rootFile = StoragePathResolver.resolveToFile(context, project.folderUri)
            if (rootFile == null) {
                onComplete(false)
                return@launch
            }

            // 1. Stop old watchers
            fileWatcher.stopWatching()
            realTimeDetector.cancelAll()

            // 2. Re-register watchers
            val restarted = fileWatcher.startWatching(rootFile) { event ->
                realTimeDetector.onFileSystemEvent(event, project, rootFile)
            }

            // 3. Reconcile current state to recover any missed changes during interruption
            runReconciliationCycle()

            onComplete(restarted)
        }
    }

    private suspend fun onActiveProjectChanged(newProjectId: Long) {
        Log.i(TAG, "Active project changed to $newProjectId. Switching watchers...")
        stopRealTimeWatching()
        currentWatchedProjectId = newProjectId

        val controlState = preferences.syncControlState.first()
        if (controlState == SyncControlState.ACTIVE) {
            startRealTimeWatching()
            runReconciliationCycle()
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
                val changes = scanner.performScan(project.id, folderUri, forceAllAsPending = false) { state ->
                    scope.launch { preferences.setCurrentActivityState(state) }
                }

                if (changes > 0) {
                    preferences.setCurrentActivityState("Creating Pre-Deployment Snapshot")
                    backupManager.createPreDeploymentSnapshot(project.id, folderUri)
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
            val changes = scanner.performScan(project.id, folderUri, forceAllAsPending = false) { state ->
                scope.launch { preferences.setCurrentActivityState(state) }
            }
            if (changes > 0) {
                backupManager.createPreDeploymentSnapshot(project.id, folderUri)
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
        val activeTarget = preferences.activeDeploymentTarget.first()

        repository.syncQueueDao.insertItem(
            SyncQueueEntity(
                projectId = project.id,
                relativePath = backup.relativePath,
                operation = "ROLLBACK",
                status = "PENDING",
                target = activeTarget.name,
                targetProvider = activeTarget.name,
                backupId = backup.id
            )
        )
        queueProcessor.processPendingQueue()
        return true
    }

    suspend fun rollbackSnapshot(snapshotId: Long): Boolean {
        val project = repository.getActiveProject() ?: return false
        val folderUri = Uri.parse(project.folderUri)
        val restored = backupManager.restoreSnapshot(project.id, snapshotId, folderUri, createEmergencyBackup = true)
        if (restored) {
            repository.syncQueueDao.clearAllForProject(project.id)
            scanner.performScan(project.id, folderUri, forceAllAsPending = true)
            queueProcessor.processPendingQueue()
        }
        return restored
    }

    suspend fun rollbackToLastStableSnapshot(): Boolean {
        val project = repository.getActiveProject() ?: return false
        val stableSnapshot = repository.backupSnapshotDao.getLatestStableSnapshot(project.id) ?: return false
        return rollbackSnapshot(stableSnapshot.id)
    }

    suspend fun createManualSnapshot(label: String = "Manual Snapshot"): Boolean {
        val project = repository.getActiveProject() ?: return false
        val folderUri = Uri.parse(project.folderUri)
        val snap = backupManager.createManualSnapshot(project.id, folderUri, label)
        return snap != null
    }

    suspend fun resolveConflict(queueId: Long, overwriteRemote: Boolean) {
        repository.resolveConflict(queueId, overwriteRemote)
        queueProcessor.processPendingQueue()
    }
}
