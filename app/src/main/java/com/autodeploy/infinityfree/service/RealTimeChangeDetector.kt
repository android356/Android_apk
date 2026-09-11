package com.autodeploy.infinityfree.service

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.autodeploy.infinityfree.data.deployment.DeploymentManager
import com.autodeploy.infinityfree.data.ignore.IgnoreRuleMatcher
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.local.entity.FileMetadataEntity
import com.autodeploy.infinityfree.data.local.entity.ProjectEntity
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.preferences.SyncControlState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RealTimeChangeDetector(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences,
    private val deploymentManager: DeploymentManager,
    private val stabilityTracker: FileStabilityTracker,
    private val queueProcessor: SyncQueueProcessor,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "RealTimeChangeDetector"
    }

    private data class Candidate(
        val relativePath: String,
        val absolutePath: String,
        var accumulatedMask: Int = 0,
        var lastEventTime: Long = System.currentTimeMillis(),
        var job: Job? = null
    )

    private val candidates = ConcurrentHashMap<String, Candidate>()
    private val recentMovedFrom = ConcurrentHashMap<String, Pair<String, Long>>() // parentDir -> (relPath, timestamp)
    private var currentProjectId: Long? = null

    fun setActiveProject(projectId: Long?) {
        if (currentProjectId != projectId) {
            cancelAll()
            currentProjectId = projectId
        }
    }

    fun cancelAll() {
        for ((_, candidate) in candidates) {
            candidate.job?.cancel()
        }
        candidates.clear()
        recentMovedFrom.clear()
        stabilityTracker.clear()
    }

    fun onFileSystemEvent(
        event: FileSystemEvent,
        project: ProjectEntity,
        rootDir: File
    ) {
        if (project.id != currentProjectId) {
            currentProjectId = project.id
        }

        val relativePath = normalizePath(event.relativePath)
        if (relativePath.isEmpty()) return

        // 1. Check ignore rules
        coroutineScope.launch {
            val customPatterns = preferences.customIgnorePatterns.first()
            val ignoreMatcher = IgnoreRuleMatcher(customPatterns)
            if (ignoreMatcher.isIgnored(relativePath)) {
                return@launch
            }

            // Filter out transient editor temp files unless they are an atomic save replacement target
            if (isTransientEditorFile(relativePath) && (event.eventMask and FileObserver.MOVED_TO) == 0) {
                return@launch
            }

            val parentDir = File(event.absolutePath).parent ?: ""

            // Handle atomic save / rename pairing (MOVED_FROM followed by MOVED_TO)
            if ((event.eventMask and FileObserver.MOVED_FROM) != 0) {
                recentMovedFrom[parentDir] = Pair(relativePath, System.currentTimeMillis())
            }

            if ((event.eventMask and FileObserver.MOVED_TO) != 0) {
                val lastMoved = recentMovedFrom[parentDir]
                val now = System.currentTimeMillis()
                if (lastMoved != null && (now - lastMoved.second) < 1000L) {
                    val oldRelPath = lastMoved.first
                    if (oldRelPath != relativePath && !isTransientEditorFile(oldRelPath)) {
                        handleFileRenamed(project, rootDir, oldRelPath, relativePath)
                    }
                    recentMovedFrom.remove(parentDir)
                }
            }

            // Register / coalesce candidate
            val candidate = candidates.compute(relativePath) { _, existing ->
                val c = existing ?: Candidate(relativePath, event.absolutePath)
                c.accumulatedMask = c.accumulatedMask or event.eventMask
                c.lastEventTime = System.currentTimeMillis()
                c
            } ?: return@launch

            // Cancel prior debounce job for this file (coalescing rapid events without losing final state)
            candidate.job?.cancel()

            val debounceSec = preferences.debounceDurationSeconds.first().coerceAtLeast(1)
            val debounceMillis = (debounceSec * 1000L).coerceAtMost(3000L)

            // Schedule debounce verification
            candidate.job = coroutineScope.launch {
                delay(debounceMillis)
                processCandidate(project, rootDir, candidate)
            }
        }
    }

    private suspend fun processCandidate(
        project: ProjectEntity,
        rootDir: File,
        candidate: Candidate
    ) {
        val relativePath = candidate.relativePath
        val file = File(rootDir, relativePath)
        val projectId = project.id

        // Check if sync is stopped or emergency stopped
        val controlState = preferences.syncControlState.first()
        if (controlState == SyncControlState.EMERGENCY_STOPPED || controlState == SyncControlState.PAUSED) {
            Log.d(TAG, "Skipping candidate $relativePath due to controlState: $controlState")
            candidates.remove(relativePath)
            return
        }

        if (!file.exists()) {
            // File no longer exists on disk -> handle deletion
            handleFileDeleted(project, rootDir, relativePath, candidate.accumulatedMask)
            candidates.remove(relativePath)
            return
        }

        // Stability Check: ensure writer has finished writing
        val isStable = stabilityTracker.checkFileStability(file, sampleDelayMs = 50L)
        if (!isStable) {
            Log.d(TAG, "File $relativePath is still changing, rescheduling debounce")
            candidate.job = coroutineScope.launch {
                delay(500L)
                processCandidate(project, rootDir, candidate)
            }
            return
        }

        // Layered Content Verification
        val currentSize = file.length()
        val currentModified = file.lastModified()
        val existing = database.fileMetadataDao().getByPath(projectId, relativePath)

        if (existing == null) {
            // Case 1: New file
            val currentHash = StoragePathResolver.calculateSha256(file)
            enqueueUpload(project, rootDir, relativePath, currentSize, currentModified, currentHash, candidate.accumulatedMask, isNew = true)
        } else {
            // Case 2: Existing file modification check
            val sizeChanged = (existing.fileSize != currentSize)
            val timeChanged = (currentModified > existing.lastModified)

            // Intelligent selective hashing: calculate hash to ensure 1-char / same-size changes are detected
            val currentHash = StoragePathResolver.calculateSha256(file)
            val hashChanged = (existing.contentHash == null || !existing.contentHash.equals(currentHash, ignoreCase = true))

            if (sizeChanged || hashChanged || timeChanged) {
                if (sizeChanged || hashChanged) {
                    // Content definitely changed (even if size was identical, or timestamp same second)
                    enqueueUpload(project, rootDir, relativePath, currentSize, currentModified, currentHash, candidate.accumulatedMask, isNew = false)
                } else {
                    // Only timestamp changed with identical content hash
                    database.fileMetadataDao().insertOrUpdate(
                        existing.copy(
                            lastModified = currentModified,
                            isPresent = true
                        )
                    )
                }
            } else {
                Log.d(TAG, "Candidate $relativePath verified: content identical, ignoring false event")
            }
        }

        candidates.remove(relativePath)
    }

    private suspend fun enqueueUpload(
        project: ProjectEntity,
        rootDir: File,
        relativePath: String,
        size: Long,
        lastModified: Long,
        contentHash: String,
        eventMask: Int,
        isNew: Boolean
    ) {
        val projectId = project.id
        val activeTarget = deploymentManager.getActiveTarget()
        val eventDescription = describeEvents(eventMask, isNew)
        val now = System.currentTimeMillis()

        // 1. Log detection event in SyncHistoryDao
        database.syncHistoryDao().insert(
            SyncHistoryEntity(
                projectId = projectId,
                operation = if (isNew) "NEW_FILE" else "FILE_CHANGED",
                relativePath = relativePath,
                startedAt = now,
                completedAt = now,
                result = "SUCCESS",
                targetProvider = activeTarget.name,
                errorMessage = "Detection: $eventDescription | Status: Queued | Target: ${activeTarget.displayName}"
            )
        )

        // 2. Update live UI timestamps
        preferences.setLastDetectedChangeTimestamp(now)
        preferences.setLastDetectedFilePath(relativePath)

        // 3. Enqueue in syncQueueDao
        val activeQueueItem = database.syncQueueDao().getActiveItemByPath(projectId, relativePath)
        if (activeQueueItem == null) {
            database.syncQueueDao().insertItem(
                SyncQueueEntity(
                    projectId = projectId,
                    relativePath = relativePath,
                    operation = "UPLOAD",
                    status = "PENDING",
                    target = activeTarget.name,
                    targetProvider = activeTarget.name,
                    retryCount = 0,
                    createdAt = now
                )
            )
        } else {
            database.syncQueueDao().updateItem(
                activeQueueItem.copy(
                    status = "PENDING",
                    target = activeTarget.name,
                    targetProvider = activeTarget.name,
                    createdAt = now,
                    retryCount = 0
                )
            )
        }

        // 4. Update fileMetadataDao with verified content hash
        val targetFile = File(rootDir, relativePath)
        val fileUri = "file://${targetFile.absolutePath}"
        database.fileMetadataDao().insertOrUpdate(
            FileMetadataEntity(
                projectId = projectId,
                relativePath = relativePath,
                itemType = "FILE",
                fileSize = size,
                lastModified = lastModified,
                contentHash = contentHash,
                syncStatus = "PENDING",
                optionalHash = fileUri,
                isPresent = true
            )
        )

        Log.i(TAG, "Queued upload for $relativePath (Size: $size, Hash: ${contentHash.take(8)}..., Target: ${activeTarget.name})")

        // 5. Trigger Queue Processing immediately (No manual refresh required!)
        queueProcessor.processPendingQueue { state ->
            coroutineScope.launch { preferences.setCurrentActivityState(state) }
        }
    }

    private suspend fun handleFileDeleted(
        project: ProjectEntity,
        rootDir: File,
        relativePath: String,
        eventMask: Int
    ) {
        val projectId = project.id
        val existing = database.fileMetadataDao().getByPath(projectId, relativePath) ?: return
        if (!existing.isPresent) return // Already recorded as deleted

        // Guard against catastrophic deletion if project root suddenly disappeared
        if (!rootDir.exists()) {
            Log.w(TAG, "Project root ${rootDir.absolutePath} does not exist. Catastrophic deletion guarded for $relativePath")
            return
        }

        val activeTarget = deploymentManager.getActiveTarget()
        val syncDeletions = preferences.syncDeletions.first()
        val now = System.currentTimeMillis()

        database.fileMetadataDao().insertOrUpdate(
            existing.copy(
                isPresent = false,
                syncStatus = "DELETED"
            )
        )

        database.syncHistoryDao().insert(
            SyncHistoryEntity(
                projectId = projectId,
                operation = "DELETE_FILE",
                relativePath = relativePath,
                startedAt = now,
                completedAt = now,
                result = "SUCCESS",
                targetProvider = activeTarget.name,
                errorMessage = "Detection: DELETE | Status: Queued | Target: ${activeTarget.displayName}"
            )
        )

        preferences.setLastDetectedChangeTimestamp(now)
        preferences.setLastDetectedFilePath(relativePath)

        if (syncDeletions) {
            val activeQueueItem = database.syncQueueDao().getActiveItemByPath(projectId, relativePath)
            if (activeQueueItem == null) {
                database.syncQueueDao().insertItem(
                    SyncQueueEntity(
                        projectId = projectId,
                        relativePath = relativePath,
                        operation = "DELETE_FILE",
                        status = "PENDING",
                        target = activeTarget.name,
                        targetProvider = activeTarget.name,
                        retryCount = 0,
                        createdAt = now
                    )
                )
            } else {
                database.syncQueueDao().updateItem(
                    activeQueueItem.copy(
                        operation = "DELETE_FILE",
                        status = "PENDING",
                        target = activeTarget.name,
                        targetProvider = activeTarget.name,
                        createdAt = now
                    )
                )
            }

            queueProcessor.processPendingQueue { state ->
                coroutineScope.launch { preferences.setCurrentActivityState(state) }
            }
        }
    }

    private suspend fun handleFileRenamed(
        project: ProjectEntity,
        rootDir: File,
        oldRelativePath: String,
        newRelativePath: String
    ) {
        Log.i(TAG, "File rename detected: $oldRelativePath -> $newRelativePath")
        handleFileDeleted(project, rootDir, oldRelativePath, FileObserver.MOVED_FROM)

        val newFile = File(rootDir, newRelativePath)
        if (newFile.exists()) {
            val size = newFile.length()
            val modified = newFile.lastModified()
            val hash = StoragePathResolver.calculateSha256(newFile)
            enqueueUpload(project, rootDir, newRelativePath, size, modified, hash, FileObserver.MOVED_TO, isNew = true)
        }
    }

    private fun describeEvents(mask: Int, isNew: Boolean): String {
        val events = mutableListOf<String>()
        if ((mask and FileObserver.CREATE) != 0) events.add("CREATE")
        if ((mask and FileObserver.MODIFY) != 0) events.add("MODIFY")
        if ((mask and FileObserver.CLOSE_WRITE) != 0) events.add("CLOSE_WRITE")
        if ((mask and FileObserver.MOVED_TO) != 0) events.add("MOVED_TO")
        if ((mask and FileObserver.MOVED_FROM) != 0) events.add("MOVED_FROM")
        if ((mask and FileObserver.DELETE) != 0) events.add("DELETE")
        if ((mask and FileObserver.ATTRIB) != 0) events.add("ATTRIB")
        return if (events.isEmpty()) (if (isNew) "CREATE" else "MODIFY") else events.joinToString(" + ")
    }

    private fun isTransientEditorFile(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return name.endsWith(".tmp", ignoreCase = true) ||
                name.endsWith(".swp", ignoreCase = true) ||
                name.endsWith("~") ||
                name.startsWith(".#") ||
                name.startsWith("~") ||
                name == "4913" // Vim temporary probe file
    }

    private fun normalizePath(path: String): String {
        return path.trim().replace('\\', '/').trimStart('/')
    }
}
