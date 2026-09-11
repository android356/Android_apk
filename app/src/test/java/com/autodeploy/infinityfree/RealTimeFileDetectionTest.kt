package com.autodeploy.infinityfree

import android.os.FileObserver
import com.autodeploy.infinityfree.data.deployment.DeploymentTargetType
import com.autodeploy.infinityfree.data.local.entity.FileMetadataEntity
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import com.autodeploy.infinityfree.service.FileStabilityTracker
import com.autodeploy.infinityfree.service.FileSystemEvent
import com.autodeploy.infinityfree.service.StoragePathResolver
import com.autodeploy.infinityfree.service.WatcherHealthState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RealTimeFileDetectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // In-memory simulation of Room DAO state for verification in unit tests
    private val memoryMetadata = ConcurrentHashMap<String, FileMetadataEntity>()
    private val memoryQueue = ConcurrentHashMap<String, SyncQueueEntity>()
    private val stabilityTracker = FileStabilityTracker()

    private var activeDeploymentTarget = DeploymentTargetType.INFINITY_FREE
    private var activeProjectId = 1L

    /**
     * Layered Verification & Enqueue logic mirroring RealTimeChangeDetector
     */
    private fun simulateProcessCandidate(
        projectId: Long,
        rootDir: File,
        relativePath: String,
        eventMask: Int
    ): Boolean {
        if (projectId != activeProjectId) {
            // Project isolation: events from inactive projects are rejected
            return false
        }

        val file = File(rootDir, relativePath)

        if (!file.exists()) {
            val existing = memoryMetadata[relativePath] ?: return false
            if (!existing.isPresent) return false

            memoryMetadata[relativePath] = existing.copy(isPresent = false, syncStatus = "DELETED")
            memoryQueue[relativePath] = SyncQueueEntity(
                projectId = projectId,
                relativePath = relativePath,
                operation = "DELETE_FILE",
                status = "PENDING",
                target = activeDeploymentTarget.name,
                targetProvider = activeDeploymentTarget.name
            )
            return true
        }

        val isStable = stabilityTracker.checkFileStability(file, sampleDelayMs = 10L)
        if (!isStable) return false

        val currentSize = file.length()
        val currentModified = file.lastModified()
        val currentHash = StoragePathResolver.calculateSha256(file)

        val existing = memoryMetadata[relativePath]

        if (existing == null) {
            // New File
            memoryMetadata[relativePath] = FileMetadataEntity(
                projectId = projectId,
                relativePath = relativePath,
                fileSize = currentSize,
                lastModified = currentModified,
                contentHash = currentHash,
                syncStatus = "PENDING",
                isPresent = true
            )
            memoryQueue[relativePath] = SyncQueueEntity(
                projectId = projectId,
                relativePath = relativePath,
                operation = "UPLOAD",
                status = "PENDING",
                target = activeDeploymentTarget.name,
                targetProvider = activeDeploymentTarget.name
            )
            return true
        } else {
            val sizeChanged = existing.fileSize != currentSize
            val hashChanged = existing.contentHash == null || !existing.contentHash.equals(currentHash, ignoreCase = true)
            val timeChanged = currentModified > existing.lastModified

            if (sizeChanged || hashChanged || timeChanged) {
                if (sizeChanged || hashChanged) {
                    memoryMetadata[relativePath] = existing.copy(
                        fileSize = currentSize,
                        lastModified = currentModified,
                        contentHash = currentHash,
                        syncStatus = "PENDING",
                        isPresent = true
                    )
                    memoryQueue[relativePath] = SyncQueueEntity(
                        projectId = projectId,
                        relativePath = relativePath,
                        operation = "UPLOAD",
                        status = "PENDING",
                        target = activeDeploymentTarget.name,
                        targetProvider = activeDeploymentTarget.name
                    )
                    return true
                } else {
                    memoryMetadata[relativePath] = existing.copy(
                        lastModified = currentModified,
                        isPresent = true
                    )
                    return false
                }
            }
            return false
        }
    }

    @Test
    fun testBasicOneCharacterModificationDetected() {
        val root = tempFolder.newFolder("project1")
        val file = File(root, "hello.txt")

        // Initial state: "hello world"
        file.writeText("hello world")
        simulateProcessCandidate(1L, root, "hello.txt", FileObserver.CREATE)
        memoryQueue.clear()

        // Modify exactly ONE character: "hello World"
        file.writeText("hello World")

        // Size is identical (11 bytes), but 1 character differs ('w' -> 'W')
        assertEquals(11L, file.length())
        val detected = simulateProcessCandidate(1L, root, "hello.txt", FileObserver.MODIFY or FileObserver.CLOSE_WRITE)

        assertTrue("One character modification must be detected", detected)
        val queueItem = memoryQueue["hello.txt"]
        assertNotNull(queueItem)
        assertEquals("UPLOAD", queueItem?.operation)
        assertEquals("PENDING", queueItem?.status)
        assertEquals("INFINITY_FREE", queueItem?.targetProvider)
    }

    @Test
    fun testSameSizeModificationDetected() {
        val root = tempFolder.newFolder("project_same_size")
        val file = File(root, "test.txt")

        // Initial state: "abc" (3 bytes)
        file.writeText("abc")
        simulateProcessCandidate(1L, root, "test.txt", FileObserver.CREATE)
        memoryQueue.clear()

        // Modify to: "abd" (same 3 bytes)
        file.writeText("abd")
        assertEquals(3L, file.length())

        val detected = simulateProcessCandidate(1L, root, "test.txt", FileObserver.MODIFY)
        assertTrue("Same-size content change must be detected via hash comparison", detected)

        val queueItem = memoryQueue["test.txt"]
        assertNotNull(queueItem)
        assertEquals("UPLOAD", queueItem?.operation)
        assertEquals("PENDING", queueItem?.status)
    }

    @Test
    fun testCheckboxCharacterChangeDetected() {
        val root = tempFolder.newFolder("project_symbols")
        val file = File(root, "todo.md")

        // Initial state: "Task: ✓" (UTF-8 bytes match)
        file.writeText("Task: ✓")
        val initialSize = file.length()
        simulateProcessCandidate(1L, root, "todo.md", FileObserver.CREATE)
        memoryQueue.clear()

        // Modify to: "Task: ☑" (Same UTF-8 byte count)
        file.writeText("Task: ☑")
        assertEquals(initialSize, file.length())

        val detected = simulateProcessCandidate(1L, root, "todo.md", FileObserver.MODIFY)
        assertTrue("Checkbox character replacement must be detected", detected)
        assertNotNull(memoryQueue["todo.md"])
    }

    @Test
    fun testSameSecondRapidModificationsCoalesceWithoutLosingFinalState() {
        val root = tempFolder.newFolder("project_rapid")
        val file = File(root, "rapid.txt")

        // Rapid series of writes simulating typing: A -> AB -> ABC -> ABCD
        file.writeText("A")
        stabilityTracker.recordEvent("rapid.txt", FileObserver.MODIFY)

        file.writeText("AB")
        stabilityTracker.recordEvent("rapid.txt", FileObserver.MODIFY)

        file.writeText("ABC")
        stabilityTracker.recordEvent("rapid.txt", FileObserver.MODIFY)

        file.writeText("ABCD")
        stabilityTracker.recordEvent("rapid.txt", FileObserver.MODIFY or FileObserver.CLOSE_WRITE)

        // After quiet period, candidate is processed
        val detected = simulateProcessCandidate(1L, root, "rapid.txt", FileObserver.CLOSE_WRITE)
        assertTrue(detected)

        // The final content state on disk ("ABCD") must be recorded in metadata and queue
        val metadata = memoryMetadata["rapid.txt"]
        assertNotNull(metadata)
        assertEquals(4L, metadata?.fileSize)
        val expectedSha = StoragePathResolver.calculateSha256(file)
        assertEquals(expectedSha, metadata?.contentHash)
        assertEquals("ABCD", file.readText())
    }

    @Test
    fun testNestedDirectoryFileDetection() {
        val root = tempFolder.newFolder("project_nested")
        val nestedDir = File(root, "src/main/assets/data").apply { mkdirs() }
        val nestedFile = File(nestedDir, "config.json")

        nestedFile.writeText("{\"active\": true}")
        val relPath = "src/main/assets/data/config.json"

        val detected = simulateProcessCandidate(1L, root, relPath, FileObserver.CREATE)
        assertTrue("Nested directory file creation must be detected", detected)

        val queueItem = memoryQueue[relPath]
        assertNotNull(queueItem)
        assertEquals(relPath, queueItem?.relativePath)

        // Nested file modification
        nestedFile.writeText("{\"active\": false}")
        val modDetected = simulateProcessCandidate(1L, root, relPath, FileObserver.MODIFY)
        assertTrue("Nested directory file modification must be detected", modDetected)
    }

    @Test
    fun testNewFileCreationDetected() {
        val root = tempFolder.newFolder("project_new")
        val newFile = File(root, "styles.css")
        newFile.writeText("body { color: red; }")

        val detected = simulateProcessCandidate(1L, root, "styles.css", FileObserver.CREATE)
        assertTrue("New file must be detected automatically", detected)
        assertEquals("UPLOAD", memoryQueue["styles.css"]?.operation)
        assertEquals("PENDING", memoryQueue["styles.css"]?.status)
    }

    @Test
    fun testDeleteFileDetected() {
        val root = tempFolder.newFolder("project_del")
        val file = File(root, "obsolete.html")
        file.writeText("<h1>Old</h1>")

        simulateProcessCandidate(1L, root, "obsolete.html", FileObserver.CREATE)
        memoryQueue.clear()

        // Delete file from disk
        file.delete()
        assertFalse(file.exists())

        val detected = simulateProcessCandidate(1L, root, "obsolete.html", FileObserver.DELETE)
        assertTrue("File deletion must be detected", detected)

        val queueItem = memoryQueue["obsolete.html"]
        assertNotNull(queueItem)
        assertEquals("DELETE_FILE", queueItem?.operation)
        assertEquals("PENDING", queueItem?.status)
    }

    @Test
    fun testRenameFileHandling() {
        val root = tempFolder.newFolder("project_rename")
        val oldFile = File(root, "draft.txt")
        oldFile.writeText("Hello Draft")

        simulateProcessCandidate(1L, root, "draft.txt", FileObserver.CREATE)
        memoryQueue.clear()

        // Rename draft.txt -> final.txt
        val newFile = File(root, "final.txt")
        oldFile.renameTo(newFile)

        // Process MOVED_FROM on draft.txt
        simulateProcessCandidate(1L, root, "draft.txt", FileObserver.MOVED_FROM)
        // Process MOVED_TO on final.txt
        simulateProcessCandidate(1L, root, "final.txt", FileObserver.MOVED_TO)

        assertEquals("DELETE_FILE", memoryQueue["draft.txt"]?.operation)
        assertEquals("UPLOAD", memoryQueue["final.txt"]?.operation)
    }

    @Test
    fun testAtomicEditorSaveSimulation() {
        val root = tempFolder.newFolder("project_atomic")
        val targetFile = File(root, "App.kt")
        targetFile.writeText("class App { val v = 1 }")

        simulateProcessCandidate(1L, root, "App.kt", FileObserver.CREATE)
        memoryQueue.clear()

        // Simulate editor atomic save:
        // 1. Write to App.kt.tmp
        val tmpFile = File(root, "App.kt.tmp")
        tmpFile.writeText("class App { val v = 2 }")

        // 2. Atomic rename App.kt.tmp -> App.kt (replaces original)
        tmpFile.renameTo(targetFile)

        // Inotify generates MOVED_TO or CLOSE_WRITE on targetFile
        val detected = simulateProcessCandidate(1L, root, "App.kt", FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE)
        assertTrue("Atomic editor save must be detected", detected)

        val queueItem = memoryQueue["App.kt"]
        assertNotNull(queueItem)
        assertEquals("UPLOAD", queueItem?.operation)
        assertEquals("PENDING", queueItem?.status)
    }

    @Test
    fun testWatcherRestartRecovery() {
        val root = tempFolder.newFolder("project_restart")
        val file = File(root, "code.js")
        file.writeText("console.log(1);")

        simulateProcessCandidate(1L, root, "code.js", FileObserver.CREATE)
        memoryQueue.clear()

        // Simulate Watcher Stop (e.g. app backgrounded or process killed)
        stabilityTracker.clear()

        // Modify file while watcher was stopped
        file.writeText("console.log(2);")

        // Watcher restarts -> runs reconciliation scan to recover missed changes
        val currentHash = StoragePathResolver.calculateSha256(file)
        val existing = memoryMetadata["code.js"]
        assertNotNull(existing)
        assertNotEquals(currentHash, existing?.contentHash)

        // Reconciliation picks it up and enqueues
        val recovered = simulateProcessCandidate(1L, root, "code.js", FileObserver.CREATE)
        assertTrue("Missed change during watcher interruption must be recovered", recovered)
        assertEquals("UPLOAD", memoryQueue["code.js"]?.operation)
    }

    @Test
    fun testProjectSwitchIsolation() {
        val rootA = tempFolder.newFolder("projectA")
        val rootB = tempFolder.newFolder("projectB")

        val fileA = File(rootA, "fileA.txt").apply { writeText("A") }
        val fileB = File(rootB, "fileB.txt").apply { writeText("B") }

        // Active project is Project 1 (Project A)
        activeProjectId = 1L
        simulateProcessCandidate(1L, rootA, "fileA.txt", FileObserver.CREATE)
        assertEquals("UPLOAD", memoryQueue["fileA.txt"]?.operation)

        // Switch to Project 2 (Project B)
        activeProjectId = 2L
        memoryQueue.clear()

        // Event arrives from Project A (e.g. delayed event) -> must be rejected
        val acceptedA = simulateProcessCandidate(1L, rootA, "fileA.txt", FileObserver.MODIFY)
        assertFalse("Events from Project A must never enter Project B queue", acceptedA)
        assertNull(memoryQueue["fileA.txt"])

        // Event arrives from Project B -> accepted
        val acceptedB = simulateProcessCandidate(2L, rootB, "fileB.txt", FileObserver.CREATE)
        assertTrue(acceptedB)
        assertNotNull(memoryQueue["fileB.txt"])
    }

    @Test
    fun testActiveDeploymentTargetSwitching() {
        val root = tempFolder.newFolder("project_target")
        val file = File(root, "api.php")
        file.writeText("<?php echo 'v1';")

        // Active target: INFINITY_FREE
        activeDeploymentTarget = DeploymentTargetType.INFINITY_FREE
        simulateProcessCandidate(1L, root, "api.php", FileObserver.CREATE)
        assertEquals("INFINITY_FREE", memoryQueue["api.php"]?.targetProvider)

        // Switch active target: SHROTI_HOST
        activeDeploymentTarget = DeploymentTargetType.SHROTI_HOST
        file.writeText("<?php echo 'v2';")

        simulateProcessCandidate(1L, root, "api.php", FileObserver.MODIFY)
        val item = memoryQueue["api.php"]
        assertNotNull(item)
        assertEquals("SHROTI_HOST", item?.targetProvider)
        assertEquals("SHROTI_HOST", item?.target)
    }

    @Test
    fun testWatcherHealthStateTransitions() {
        assertEquals("Monitoring", WatcherHealthState.WATCHER_RUNNING.displayName)
        assertEquals("Needs recovery", WatcherHealthState.WATCHER_DEGRADED.displayName)
        assertEquals("Restarting...", WatcherHealthState.WATCHER_RESTARTING.displayName)
        assertEquals("Stopped", WatcherHealthState.WATCHER_STOPPED.displayName)
    }

    @Test
    fun testStoragePathResolverWithDirectPath() {
        val testDir = tempFolder.newFolder("storage_test")
        val resolved = StoragePathResolver.resolveToFile(null, testDir.absolutePath)
        assertNotNull(resolved)
        assertEquals(testDir.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun testSha256ChecksumIntegrity() {
        val f1 = tempFolder.newFile("f1.txt").apply { writeText("hello world") }
        val f2 = tempFolder.newFile("f2.txt").apply { writeText("hello World") }

        val hash1 = StoragePathResolver.calculateSha256(f1)
        val hash2 = StoragePathResolver.calculateSha256(f2)

        assertNotNull(hash1)
        assertNotNull(hash2)
        assertEquals(64, hash1.length) // SHA-256 is 64 hex chars
        assertEquals(64, hash2.length)
        assertNotEquals("One char change must produce completely different SHA-256 hash", hash1, hash2)
    }
}
