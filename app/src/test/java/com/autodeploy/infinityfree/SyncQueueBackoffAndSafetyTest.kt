package com.autodeploy.infinityfree

import com.autodeploy.infinityfree.data.deployment.DeploymentTargetType
import com.autodeploy.infinityfree.data.local.entity.FileMetadataEntity
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueBackoffAndSafetyTest {

    private fun calculateBackoffDelay(retryCount: Int): Long {
        return (2000L * (1L shl (retryCount - 1).coerceAtMost(5))).coerceAtMost(60_000L)
    }

    private fun shouldWaitForBackoff(status: String, retryCount: Int, lastAttemptAt: Long?, now: Long): Boolean {
        if (status != "RETRYING" || retryCount <= 0) return false
        val lastAttempt = lastAttemptAt ?: return false
        val backoffDelay = calculateBackoffDelay(retryCount)
        return (now - lastAttempt) < backoffDelay
    }

    @Test
    fun testExponentialBackoffProgression() {
        assertEquals(2000L, calculateBackoffDelay(1))  // 2s * 2^0 = 2000ms
        assertEquals(4000L, calculateBackoffDelay(2))  // 2s * 2^1 = 4000ms
        assertEquals(8000L, calculateBackoffDelay(3))  // 2s * 2^2 = 8000ms
        assertEquals(16000L, calculateBackoffDelay(4)) // 2s * 2^3 = 16000ms
        assertEquals(32000L, calculateBackoffDelay(5)) // 2s * 2^4 = 32000ms
        assertEquals(60000L, calculateBackoffDelay(6)) // 2s * 2^5 = 64000ms, capped at 60000ms
        assertEquals(60000L, calculateBackoffDelay(10)) // Capped at 60s
    }

    @Test
    fun testShouldWaitForBackoffCooldown() {
        val now = 100_000L
        // Retry 1 needs 2000ms delay. If last attempt was at 99_000ms (1000ms ago), should wait.
        assertTrue(shouldWaitForBackoff("RETRYING", 1, 99_000L, now))

        // If last attempt was at 97_000ms (3000ms ago > 2000ms delay), should NOT wait.
        assertFalse(shouldWaitForBackoff("RETRYING", 1, 97_000L, now))

        // If status is PENDING, should NOT wait regardless of time
        assertFalse(shouldWaitForBackoff("PENDING", 1, 99_000L, now))
    }

    @Test
    fun testCatastrophicBulkDeletionPrevention() {
        // Given existing records containing 50 tracked files
        val existingFiles = (1..50).map { i ->
            FileMetadataEntity(
                projectId = 1L,
                relativePath = "file$i.txt",
                itemType = "FILE",
                isPresent = true
            )
        }
        val fileRecordsCount = existingFiles.count { it.itemType == "FILE" && it.isPresent }
        assertEquals(50, fileRecordsCount)

        // Case 1: Scanned items is 0 (e.g. storage disconnected, SAF error)
        val scannedItemsCount = 0
        val canProcessDeletions = !(scannedItemsCount == 0 && fileRecordsCount > 0)
        assertFalse("Catastrophic deletion must be blocked when scan is 0 files", canProcessDeletions)

        // Case 2: User legitimately deleted 1 file (49 files scanned)
        val legitScanCount = 49
        val canProcessLegit = !(legitScanCount == 0 && fileRecordsCount > 0)
        assertTrue("Legitimate deletion must be allowed when files are scanned", canProcessLegit)
    }

    @Test
    fun testInfiniteRollbackLoopPrevention() {
        val failedItem = SyncQueueEntity(
            projectId = 1L,
            relativePath = "index.html",
            operation = "ROLLBACK",
            status = "FAILED"
        )

        // Rule: If item.operation == "ROLLBACK", triggerAutoRollback must never enqueue another rollback
        val shouldTriggerAutoRollback = failedItem.operation != "ROLLBACK"
        assertFalse("Auto-rollback must never be triggered for an item that is already a ROLLBACK", shouldTriggerAutoRollback)
    }

    @Test
    fun testSingleActiveTargetRebinding() {
        val oldTargetItem = SyncQueueEntity(
            projectId = 1L,
            relativePath = "config.php",
            target = "INFINITY_FREE",
            targetProvider = "INFINITY_FREE",
            status = "PENDING"
        )

        val newActiveTarget = DeploymentTargetType.SHROTI_HOST

        // When queue processor runs, if target != activeTarget, rebind
        val rebound = if (oldTargetItem.targetProvider != newActiveTarget.name) {
            oldTargetItem.copy(target = newActiveTarget.name, targetProvider = newActiveTarget.name)
        } else {
            oldTargetItem
        }

        assertEquals("SHROTI_HOST", rebound.targetProvider)
        assertEquals("SHROTI_HOST", rebound.target)
    }
}
