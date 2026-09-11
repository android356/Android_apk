package com.autodeploy.infinityfree.data.deployment

import android.util.Log
import com.autodeploy.infinityfree.data.github.GitHubClientManager
import com.autodeploy.infinityfree.data.github.GitHubResult
import com.autodeploy.infinityfree.data.local.dao.GitHubConnectionDao
import com.autodeploy.infinityfree.data.security.SecureStorageManager
import java.io.InputStream

class GitHubDeploymentProvider(
    private val connectionDao: GitHubConnectionDao,
    private val secureStorage: SecureStorageManager,
    private val githubManager: GitHubClientManager
) : DeploymentProvider {

    override val targetType: DeploymentTargetType = DeploymentTargetType.GITHUB
    override val displayName: String = "GitHub Repository"

    companion object {
        private const val TAG = "GitHubDeploymentProvider"
    }

    private suspend fun getConnection(projectId: Long): Pair<com.autodeploy.infinityfree.data.local.entity.GitHubConnectionEntity, String>? {
        val conn = connectionDao.getConnectionForProject(projectId) ?: return null
        val token = secureStorage.getGitHubToken(conn.encryptedTokenReference) ?: return null
        return Pair(conn, token)
    }

    override suspend fun isConfigured(projectId: Long): Boolean {
        val (conn, token) = getConnection(projectId) ?: return false
        return conn.owner.isNotBlank() && conn.repo.isNotBlank() && token.isNotBlank()
    }

    override suspend fun testConnection(projectId: Long): ProviderResult<String> {
        val (conn, token) = getConnection(projectId)
            ?: return ProviderResult.Error("GitHub is not configured for this project", isAuthError = true)

        return when (val res = githubManager.testConnection(conn.owner, conn.repo, conn.branch, token)) {
            is GitHubResult.Success -> ProviderResult.Success(res.data)
            is GitHubResult.Error -> {
                val isAuth = res.statusCode == 401 || res.statusCode == 403
                ProviderResult.Error(res.message, errorCode = res.statusCode, isAuthError = isAuth, cause = res.cause)
            }
        }
    }

    override suspend fun uploadFile(
        projectId: Long,
        localStream: InputStream,
        relativePath: String,
        fileSize: Long
    ): ProviderResult<DeploymentVerificationInfo> {
        val (conn, token) = getConnection(projectId)
            ?: return ProviderResult.Error("GitHub is not configured", isAuthError = true)

        val bytes = localStream.use { it.readBytes() }
        val destPath = buildGitHubPath(conn.destinationPath, relativePath)

        val existingSha = githubManager.getFileSha(conn.owner, conn.repo, conn.branch, destPath, token)

        val commitMsg = "Auto-deploy: update $relativePath"
        return when (val res = githubManager.uploadOrUpdateFile(
            owner = conn.owner,
            repo = conn.repo,
            branch = conn.branch,
            filePath = destPath,
            fileBytes = bytes,
            commitMessage = commitMsg,
            existingSha = existingSha,
            token = token
        )) {
            is GitHubResult.Success -> {
                ProviderResult.Success(
                    DeploymentVerificationInfo(
                        remotePath = destPath,
                        verified = true,
                        remoteSize = fileSize,
                        remoteSha = res.data,
                        message = "Committed to GitHub ($destPath @ SHA: ${res.data.take(7)})"
                    )
                )
            }
            is GitHubResult.Error -> {
                ProviderResult.Error(res.message, errorCode = res.statusCode, cause = res.cause)
            }
        }
    }

    override suspend fun deleteFile(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        val (conn, token) = getConnection(projectId)
            ?: return ProviderResult.Error("GitHub is not configured", isAuthError = true)

        val destPath = buildGitHubPath(conn.destinationPath, relativePath)
        val sha = githubManager.getFileSha(conn.owner, conn.repo, conn.branch, destPath, token)
            ?: return ProviderResult.Success(true) // Already deleted on remote

        return when (val res = githubManager.deleteFile(
            owner = conn.owner,
            repo = conn.repo,
            branch = conn.branch,
            filePath = destPath,
            commitMessage = "Auto-deploy: delete $relativePath",
            existingSha = sha,
            token = token
        )) {
            is GitHubResult.Success -> ProviderResult.Success(true)
            is GitHubResult.Error -> ProviderResult.Error(res.message, errorCode = res.statusCode, cause = res.cause)
        }
    }

    override suspend fun createDirectory(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        // Git does not track directories independently; no-op success
        return ProviderResult.Success(true)
    }

    override suspend fun deleteDirectory(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        // Handled via recursive file deletion in Git; no-op success
        return ProviderResult.Success(true)
    }

    override suspend fun verifyDeployment(
        projectId: Long,
        relativePath: String,
        expectedSize: Long
    ): ProviderResult<Boolean> {
        val (conn, token) = getConnection(projectId)
            ?: return ProviderResult.Error("GitHub is not configured", isAuthError = true)

        val destPath = buildGitHubPath(conn.destinationPath, relativePath)
        val sha = githubManager.getFileSha(conn.owner, conn.repo, conn.branch, destPath, token)
        return if (sha != null) {
            ProviderResult.Success(true)
        } else {
            ProviderResult.Error("File not found on GitHub: $destPath")
        }
    }

    override suspend fun verifyDeletion(projectId: Long, relativePath: String): ProviderResult<Boolean> {
        val (conn, token) = getConnection(projectId)
            ?: return ProviderResult.Error("GitHub is not configured", isAuthError = true)

        val destPath = buildGitHubPath(conn.destinationPath, relativePath)
        val sha = githubManager.getFileSha(conn.owner, conn.repo, conn.branch, destPath, token)
        return if (sha == null) {
            ProviderResult.Success(true)
        } else {
            ProviderResult.Error("Remote file still exists on GitHub: $destPath")
        }
    }

    private fun buildGitHubPath(destRoot: String, relativePath: String): String {
        val cleanRoot = destRoot.trim().trimStart('/').trimEnd('/')
        val cleanRel = relativePath.trimStart('/')
        return if (cleanRoot.isEmpty()) cleanRel else "$cleanRoot/$cleanRel"
    }
}
