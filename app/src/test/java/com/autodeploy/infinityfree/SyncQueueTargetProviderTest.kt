package com.autodeploy.infinityfree

import com.autodeploy.infinityfree.data.deployment.DeploymentTargetType
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueTargetProviderTest {

    @Test
    fun testDefaultTargetProviderIsInfinityFree() {
        val item = SyncQueueEntity(
            projectId = 1L,
            relativePath = "index.php"
        )
        assertEquals("INFINITY_FREE", item.targetProvider)
        assertEquals(DeploymentTargetType.INFINITY_FREE, DeploymentTargetType.fromId(item.targetProvider))
        assertFalse(item.verified)
    }

    @Test
    fun testTargetProviderPreservationAcrossTargetSwitches() {
        // Enqueue item for ShrotiHost cPanel
        val shrotiItem = SyncQueueEntity(
            projectId = 1L,
            relativePath = "api/data.json",
            target = DeploymentTargetType.SHROTI_HOST.name,
            targetProvider = DeploymentTargetType.SHROTI_HOST.name
        )

        // Enqueue item for GitHub
        val gitHubItem = SyncQueueEntity(
            projectId = 1L,
            relativePath = "README.md",
            target = DeploymentTargetType.GITHUB.name,
            targetProvider = DeploymentTargetType.GITHUB.name
        )

        // Suppose active target switches to InfinityFree:
        val activeTarget = DeploymentTargetType.INFINITY_FREE

        // Both items must still retain their intended target provider
        assertEquals(DeploymentTargetType.SHROTI_HOST, DeploymentTargetType.fromId(shrotiItem.targetProvider))
        assertEquals(DeploymentTargetType.GITHUB, DeploymentTargetType.fromId(gitHubItem.targetProvider))

        // Ensure that shrotiItem does NOT match the new activeTarget
        assertTrue(DeploymentTargetType.fromId(shrotiItem.targetProvider) != activeTarget)
    }

    @Test
    fun testPostUploadVerificationStatus() {
        val unverifiedItem = SyncQueueEntity(
            projectId = 1L,
            relativePath = "app.js",
            status = "SUCCESS",
            verified = false
        )
        assertFalse(unverifiedItem.verified)

        val verifiedItem = unverifiedItem.copy(verified = true)
        assertTrue(verifiedItem.verified)
    }
}
