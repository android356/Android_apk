package com.autodeploy.infinityfree

import com.autodeploy.infinityfree.data.local.entity.BackupSnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionedBackupAndStableTest {

    @Test
    fun testVersionTagFormatting() {
        fun formatVersionTag(versionCode: Int): String = "v%03d".format(versionCode)

        assertEquals("v001", formatVersionTag(1))
        assertEquals("v002", formatVersionTag(2))
        assertEquals("v010", formatVersionTag(10))
        assertEquals("v125", formatVersionTag(125))
    }

    @Test
    fun testRetentionPolicyPreservesStableSnapshots() {
        // Create 10 snapshots: v001 to v010.
        // Mark v002 and v005 as STABLE.
        val snapshots = (1..10).map { code ->
            BackupSnapshotEntity(
                id = code.toLong(),
                projectId = 1L,
                versionCode = code,
                versionTag = "v%03d".format(code),
                label = "Snapshot $code",
                snapshotDirectory = "/path/v%03d".format(code),
                fileCount = 1,
                totalSizeBytes = 100L,
                manifestJson = "[]",
                isStable = (code == 2 || code == 5)
            )
        }

        // Retention limit is 5. Total snapshots is 10. Excess = 10 - 5 = 5.
        val retentionCount = 5
        val excess = snapshots.size - retentionCount
        assertEquals(5, excess)

        // Only prune unstable snapshots in ascending order (oldest first)
        val unstables = snapshots.filter { !it.isStable }.sortedBy { it.versionCode }
        val toDelete = unstables.take(excess)

        // Verify that STABLE snapshots (v002 and v005) are NEVER selected for deletion
        val deletedIds = toDelete.map { it.id }.toSet()
        assertFalse("v002 (stable) must NOT be deleted", deletedIds.contains(2L))
        assertFalse("v005 (stable) must NOT be deleted", deletedIds.contains(5L))

        // Verify oldest unstables are deleted: v001, v003, v004, v006, v007
        assertEquals(listOf(1L, 3L, 4L, 6L, 7L), toDelete.map { it.id })
    }

    @Test
    fun testUnlimitedRetentionPreservesAllSnapshots() {
        val snapshots = (1..50).map { code ->
            BackupSnapshotEntity(
                id = code.toLong(),
                projectId = 1L,
                versionCode = code,
                versionTag = "v%03d".format(code),
                label = "Snapshot $code",
                snapshotDirectory = "/path/v%03d".format(code),
                fileCount = 1,
                totalSizeBytes = 100L,
                manifestJson = "[]",
                isStable = false
            )
        }

        fun getPruneList(allSnapshots: List<BackupSnapshotEntity>, retentionCount: Int): List<BackupSnapshotEntity> {
            if (retentionCount <= 0) return emptyList()
            if (allSnapshots.size <= retentionCount) return emptyList()
            val unstables = allSnapshots.filter { !it.isStable }.sortedBy { it.versionCode }
            return unstables.take(allSnapshots.size - retentionCount)
        }

        // Retention count -1 or 0 is UNLIMITED
        val toPruneNegative = getPruneList(snapshots, -1)
        val toPruneZero = getPruneList(snapshots, 0)
        assertTrue("Unlimited retention (-1) should prune 0 snapshots", toPruneNegative.isEmpty())
        assertTrue("Unlimited retention (0) should prune 0 snapshots", toPruneZero.isEmpty())
    }

    @Test
    fun testErrorCategorization() {
        fun categorizeError(msg: String?): String {
            if (msg == null) return "UNKNOWN"
            val lower = msg.lowercase()
            return when {
                lower.contains("timeout") || lower.contains("connect") || lower.contains("network") ||
                    lower.contains("socket") || lower.contains("unreachable") || lower.contains("resolve") -> "NETWORK"
                lower.contains("auth") || lower.contains("login") || lower.contains("password") ||
                    lower.contains("credentials") || lower.contains("401") || lower.contains("403") ||
                    lower.contains("token") || lower.contains("unauthorized") -> "AUTH"
                lower.contains("quota") || lower.contains("disk full") || lower.contains("space") ||
                    lower.contains("552") || lower.contains("storage") -> "STORAGE_FULL"
                lower.contains("permission") || lower.contains("denied") -> "PERMISSION"
                lower.contains("not found") || lower.contains("404") || lower.contains("550") -> "NOT_FOUND"
                lower.contains("hash") || lower.contains("integrity") || lower.contains("checksum") ||
                    lower.contains("mismatch") -> "INTEGRITY"
                else -> "GENERAL"
            }
        }

        assertEquals("NETWORK", categorizeError("SocketTimeoutException: Connection timed out"))
        assertEquals("AUTH", categorizeError("530 Login authentication failed"))
        assertEquals("AUTH", categorizeError("HTTP 401 Unauthorized token"))
        assertEquals("STORAGE_FULL", categorizeError("552 Quota exceeded: disk full"))
        assertEquals("PERMISSION", categorizeError("Permission denied opening remote stream"))
        assertEquals("NOT_FOUND", categorizeError("550 File not found on server"))
        assertEquals("INTEGRITY", categorizeError("SHA-256 hash mismatch after upload"))
        assertEquals("GENERAL", categorizeError("Unexpected unknown error occurred"))
    }
}
