package com.autodeploy.infinityfree.data.deployment

import java.io.InputStream

interface DeploymentProvider {
    val targetType: DeploymentTargetType
    val displayName: String

    suspend fun testConnection(projectId: Long): ProviderResult<String>
    suspend fun uploadFile(
        projectId: Long,
        localStream: InputStream,
        relativePath: String,
        fileSize: Long
    ): ProviderResult<DeploymentVerificationInfo>
    suspend fun deleteFile(projectId: Long, relativePath: String): ProviderResult<Boolean>
    suspend fun createDirectory(projectId: Long, relativePath: String): ProviderResult<Boolean>
    suspend fun deleteDirectory(projectId: Long, relativePath: String): ProviderResult<Boolean>
    suspend fun verifyDeployment(projectId: Long, relativePath: String, expectedSize: Long): ProviderResult<Boolean>
    suspend fun verifyDeletion(projectId: Long, relativePath: String): ProviderResult<Boolean> = ProviderResult.Success(true)
    suspend fun isConfigured(projectId: Long): Boolean
}
