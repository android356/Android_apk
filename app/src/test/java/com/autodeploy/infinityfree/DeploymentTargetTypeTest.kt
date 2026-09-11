package com.autodeploy.infinityfree

import com.autodeploy.infinityfree.data.deployment.DeploymentTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeploymentTargetTypeTest {

    @Test
    fun testFromIdExactMatches() {
        assertEquals(DeploymentTargetType.INFINITY_FREE, DeploymentTargetType.fromId("INFINITY_FREE"))
        assertEquals(DeploymentTargetType.SHROTI_HOST, DeploymentTargetType.fromId("SHROTI_HOST"))
        assertEquals(DeploymentTargetType.GITHUB, DeploymentTargetType.fromId("GITHUB"))
    }

    @Test
    fun testFromIdCaseInsensitive() {
        assertEquals(DeploymentTargetType.INFINITY_FREE, DeploymentTargetType.fromId("infinity_free"))
        assertEquals(DeploymentTargetType.SHROTI_HOST, DeploymentTargetType.fromId("shroti_host"))
        assertEquals(DeploymentTargetType.GITHUB, DeploymentTargetType.fromId("github"))
    }

    @Test
    fun testFromIdFallbackToInfinityFreeOnUnknown() {
        assertEquals(DeploymentTargetType.INFINITY_FREE, DeploymentTargetType.fromId(null))
        assertEquals(DeploymentTargetType.INFINITY_FREE, DeploymentTargetType.fromId(""))
        assertEquals(DeploymentTargetType.INFINITY_FREE, DeploymentTargetType.fromId("UNKNOWN_PROVIDER"))
    }

    @Test
    fun testTargetProperties() {
        assertEquals("InfinityFree", DeploymentTargetType.INFINITY_FREE.shortName)
        assertEquals("ShrotiHost", DeploymentTargetType.SHROTI_HOST.shortName)
        assertEquals("GitHub", DeploymentTargetType.GITHUB.shortName)

        assertNotEquals(DeploymentTargetType.INFINITY_FREE.id, DeploymentTargetType.SHROTI_HOST.id)
        assertNotEquals(DeploymentTargetType.SHROTI_HOST.id, DeploymentTargetType.GITHUB.id)
    }
}
