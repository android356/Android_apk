package com.autodeploy.infinityfree.data.deployment

import android.util.Log
import com.autodeploy.infinityfree.data.ftp.FtpClientManager
import com.autodeploy.infinityfree.data.ftp.FtpConnectionConfig
import com.autodeploy.infinityfree.data.ftp.FtpResult
import com.autodeploy.infinityfree.data.local.dao.ShrotiHostConnectionDao
import com.autodeploy.infinityfree.data.security.SecureStorageManager
import java.io.InputStream

class ShrotiHostCPanelProvider(
    private val connectionDao: ShrotiHostConnectionDao,
    private val secureStorage: SecureStorageManager,
    private val ftpManager: FtpClientManager
) : DeploymentProvider {

    override val targetType: DeploymentTargetType = DeploymentTargetType.SHROTI_HOST
    override val displayName: String = "ShrotiHost cPanel Hosting"

    companion object {
        private const val TAG = "ShrotiHostCPanelProvider"
    }

    private suspend fun getConfig(projectId: Long): FtpConnectionConfig? {
        val conn = connectionDao.getConnectionForProject(projectId) ?: return null
        val pass = secureStorage.getShrotiHostPassword(conn.encryptedPasswordReference) ?: ""
        return FtpConnectionConfig(
            server = conn.server.trim(),
            port = conn.port,
            username = conn.username.trim(),
            password = pass,
            remoteRootDirectory = conn.remoteRootDirectory,
            useFtps = conn.useFtps,
            timeoutMillis = 20000
        )
    }

    override suspend fun isConfigured(projectId: Long): Boolean {
        val config = getConfig(projectId) ?: return false
        return config.server.isNotBlank() && config.username.isNotBlank() && config.password.isNotBlank()
    }

    override suspend fun testConnection(projectId: Long): ProviderResult<String> {
        val config = getConfig(projectId)
            ?: return ProviderResult.Error("ShrotiHost is not configured for this project", isAuthError = true)

        return when (val res = ftpManager.testConnection(config)) {
            is FtpResult.Success -> ProviderResult.Success(res.data)
            is FtpResult.Error -> {
                val isAuth = res.message.contains("Authentication", ignoreCase = true)
                ProviderResult.Error(res.message, isAuthError = isAuth, cause = res.cause)
            }
        }
    }

    override suspend fun uploadFile(
        projectId: Long,
        localStream: InputStream,
        relativePath: String,
        fileSize: Long
    ): ProviderResult<DeploymentVerificationInfo> {
        val config = getConfig(projectId)
            ?: return ProviderResult.Error("ShrotiHost is not configured", isAuthError = true)

        return when (val res = ftpManager.uploadFile(config, localStream, relativePath)) {
            is FtpResult.Success -> {
                val verifyRes = ftpManager.verifyFile(config, relativePath, fileSize)
                val isVerified = verifyRes is FtpResult.Success
                val fullPath = config.remoteRootDirectory.trimEnd('/') + "/" + relativePath.trimStart('/')
                ProviderResult.Success(
                    DeploymentVerificationInfo(
                        remotePath = fullPath,
                        verified = isVerified,
                        remoteSize = fileSize,
                        message = if (isVerified) "Uploaded and verified on ShrotiHost" else "Uploaded to ShrotiHost"
                    )
                )
            }
            is FtpResult.Error -> ProviderResult.Error(res.message, cause = res.cause)
        }
    }

    override suspend fun deleteFile(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        val config = getConfig(projectId)
            ?: return ProviderResult.Error("ShrotiHost is not configured", isAuthError = true)

        return when (val res = ftpManager.deleteFile(config, relativePath)) {
            is FtpResult.Success -> ProviderResult.Success(true)
            is FtpResult.Error -> ProviderResult.Error(res.message, cause = res.cause)
        }
    }

    override suspend fun createDirectory(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        val config = getConfig(projectId)
            ?: return ProviderResult.Error("ShrotiHost is not configured", isAuthError = true)

        return when (val res = ftpManager.createDirectory(config, relativePath)) {
            is FtpResult.Success -> ProviderResult.Success(true)
            is FtpResult.Error -> ProviderResult.Error(res.message, cause = res.cause)
        }
    }

    override suspend fun deleteDirectory(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        val config = getConfig(projectId)
            ?: return ProviderResult.Error("ShrotiHost is not configured", isAuthError = true)

        return when (val res = ftpManager.deleteDirectory(config, relativePath)) {
            is FtpResult.Success -> ProviderResult.Success(true)
            is FtpResult.Error -> ProviderResult.Error(res.message, cause = res.cause)
        }
    }

    override suspend fun verifyDeployment(
        projectId: Long,
        relativePath: String,
        expectedSize: Long
    ): ProviderResult<Boolean> {
        val config = getConfig(projectId)
            ?: return ProviderResult.Error("ShrotiHost is not configured", isAuthError = true)

        return when (val res = ftpManager.verifyFile(config, relativePath, expectedSize)) {
            is FtpResult.Success -> ProviderResult.Success(true)
            is FtpResult.Error -> ProviderResult.Error(res.message, cause = res.cause)
        }
    }

    override suspend fun verifyDeletion(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        val config = getConfig(projectId)
            ?: return ProviderResult.Error("ShrotiHost is not configured", isAuthError = true)

        return when (val res = ftpManager.verifyFileDeleted(config, relativePath)) {
            is FtpResult.Success -> ProviderResult.Success(true)
            is FtpResult.Error -> ProviderResult.Error(res.message, cause = res.cause)
        }
    }
}
