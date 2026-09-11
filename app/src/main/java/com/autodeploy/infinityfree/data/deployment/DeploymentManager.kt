package com.autodeploy.infinityfree.data.deployment

import com.autodeploy.infinityfree.data.local.dao.SyncQueueDao
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class DeploymentManager(
    val infinityFreeProvider: InfinityFreeProvider,
    val shrotiHostProvider: ShrotiHostCPanelProvider,
    val githubProvider: GitHubDeploymentProvider,
    private val preferences: AppPreferences,
    private val syncQueueDao: SyncQueueDao? = null
) {
    val activeTargetFlow: Flow<DeploymentTargetType> = preferences.activeDeploymentTarget

    suspend fun getActiveTarget(): DeploymentTargetType {
        return preferences.activeDeploymentTarget.first()
    }

    suspend fun setActiveTarget(target: DeploymentTargetType, projectId: Long? = null) {
        preferences.setActiveDeploymentTarget(target)
        if (projectId != null && syncQueueDao != null) {
            syncQueueDao.rebindPendingToTarget(projectId, target.name)
        }
    }

    suspend fun rebindPendingQueue(projectId: Long, target: DeploymentTargetType) {
        syncQueueDao?.rebindPendingToTarget(projectId, target.name)
    }

    fun getProvider(target: DeploymentTargetType): DeploymentProvider {
        return when (target) {
            DeploymentTargetType.INFINITY_FREE -> infinityFreeProvider
            DeploymentTargetType.SHROTI_HOST -> shrotiHostProvider
            DeploymentTargetType.GITHUB -> githubProvider
        }
    }

    suspend fun getActiveProvider(): DeploymentProvider {
        val target = getActiveTarget()
        return getProvider(target)
    }

    fun getAllProviders(): List<DeploymentProvider> {
        return listOf(infinityFreeProvider, shrotiHostProvider, githubProvider)
    }
}
