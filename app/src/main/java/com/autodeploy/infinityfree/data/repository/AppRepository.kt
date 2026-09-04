package com.autodeploy.infinityfree.data.repository

import android.content.Context
import com.autodeploy.infinityfree.data.ftp.FtpClientManager
import com.autodeploy.infinityfree.data.ftp.FtpConnectionConfig
import com.autodeploy.infinityfree.data.ftp.FtpResult
import com.autodeploy.infinityfree.data.github.GitHubClientManager
import com.autodeploy.infinityfree.data.github.GitHubResult
import com.autodeploy.infinityfree.data.local.AppDatabase
import com.autodeploy.infinityfree.data.local.entity.*
import com.autodeploy.infinityfree.data.preferences.AppPreferences
import com.autodeploy.infinityfree.data.preferences.SyncControlState
import com.autodeploy.infinityfree.data.security.SecureStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val database: AppDatabase,
    val preferences: AppPreferences,
    private val secureStorage: SecureStorageManager,
    private val ftpManager: FtpClientManager,
    val githubManager: GitHubClientManager
) {
    val projectDao = database.projectDao()
    val githubConnectionDao = database.githubConnectionDao()
    val connectionDao = database.hostingConnectionDao()
    val fileMetadataDao = database.fileMetadataDao()
    val syncQueueDao = database.syncQueueDao()
    val backupDao = database.temporaryBackupDao()
    val historyDao = database.syncHistoryDao()

    fun observeActiveProject(): Flow<ProjectEntity?> = projectDao.observeActiveProject()
    suspend fun getActiveProject(): ProjectEntity? = projectDao.getActiveProject()

    suspend fun saveProject(name: String, folderUri: String): Long = withContext(Dispatchers.IO) {
        val existing = projectDao.getActiveProject()
        if (existing != null) {
            val updated = existing.copy(
                projectName = name,
                folderUri = folderUri,
                updatedAt = System.currentTimeMillis()
            )
            projectDao.updateProject(updated)
            existing.id
        } else {
            val newProject = ProjectEntity(
                projectName = name,
                folderUri = folderUri
            )
            val id = projectDao.insertProject(newProject)
            projectDao.deactivateOtherProjects(id)
            id
        }
    }

    // GitHub Connection
    fun observeGitHubConnection(projectId: Long): Flow<GitHubConnectionEntity?> =
        githubConnectionDao.observeConnectionForProject(projectId)

    suspend fun getGitHubConnection(projectId: Long): GitHubConnectionEntity? =
        githubConnectionDao.getConnectionForProject(projectId)

    suspend fun saveGitHubConnection(
        projectId: Long,
        owner: String,
        repo: String,
        branch: String,
        destinationPath: String,
        token: String
    ): Long = withContext(Dispatchers.IO) {
        val tokenKey = "github_token_proj_$projectId"
        secureStorage.saveGitHubToken(tokenKey, token)

        val normalizedDest = normalizePath(destinationPath)
        val existing = githubConnectionDao.getConnectionForProject(projectId)

        if (existing != null) {
            val updated = existing.copy(
                owner = owner.trim(),
                repo = repo.trim(),
                branch = branch.trim(),
                destinationPath = normalizedDest,
                encryptedTokenReference = tokenKey,
                updatedAt = System.currentTimeMillis()
            )
            githubConnectionDao.updateConnection(updated)
            existing.id
        } else {
            val newConn = GitHubConnectionEntity(
                projectId = projectId,
                owner = owner.trim(),
                repo = repo.trim(),
                branch = branch.trim(),
                destinationPath = normalizedDest,
                encryptedTokenReference = tokenKey
            )
            githubConnectionDao.insertConnection(newConn)
        }
    }

    fun getStoredGitHubToken(keyReference: String): String? {
        return secureStorage.getGitHubToken(keyReference)
    }

    suspend fun testGitHubConnection(
        owner: String,
        repo: String,
        branch: String,
        token: String
    ): GitHubResult<String> {
        return githubManager.testConnection(owner, repo, branch, token)
    }

    // Hosting Connection (InfinityFree)
    fun observeConnectionForProject(projectId: Long): Flow<HostingConnectionEntity?> =
        connectionDao.observeConnectionForProject(projectId)

    suspend fun getConnectionForProject(projectId: Long): HostingConnectionEntity? =
        connectionDao.getConnectionForProject(projectId)

    suspend fun saveHostingConnection(
        projectId: Long,
        connectionName: String,
        server: String,
        port: Int,
        username: String,
        password: String,
        remoteRoot: String
    ): Long = withContext(Dispatchers.IO) {
        val passwordKey = "ftp_pass_proj_$projectId"
        secureStorage.saveFtpPassword(passwordKey, password)

        val existing = connectionDao.getConnectionForProject(projectId)
        val normalizedRoot = if (remoteRoot.endsWith("/")) remoteRoot else "$remoteRoot/"

        if (existing != null) {
            val updated = existing.copy(
                connectionName = connectionName,
                server = server.trim(),
                port = port,
                username = username.trim(),
                encryptedPasswordReference = passwordKey,
                remoteRootDirectory = normalizedRoot,
                updatedAt = System.currentTimeMillis()
            )
            connectionDao.updateConnection(updated)
            existing.id
        } else {
            val newConn = HostingConnectionEntity(
                projectId = projectId,
                connectionName = connectionName,
                server = server.trim(),
                port = port,
                username = username.trim(),
                encryptedPasswordReference = passwordKey,
                remoteRootDirectory = normalizedRoot
            )
            connectionDao.insertConnection(newConn)
        }
    }

    fun getStoredPassword(keyReference: String): String? {
        return secureStorage.getFtpPassword(keyReference)
    }

    suspend fun testFtpConnection(
        server: String,
        port: Int,
        username: String,
        password: String,
        remoteRootDirectory: String
    ): FtpResult<String> {
        val config = FtpConnectionConfig(
            server = server.trim(),
            port = port,
            username = username.trim(),
            password = password,
            remoteRootDirectory = remoteRootDirectory
        )
        return ftpManager.testConnection(config)
    }

    // Counts & Observations
    fun observeFileCount(projectId: Long): Flow<Int> = fileMetadataDao.observeFileCount(projectId)
    fun observeFolderCount(projectId: Long): Flow<Int> = fileMetadataDao.observeFolderCount(projectId)
    fun observePendingCount(projectId: Long): Flow<Int> = syncQueueDao.observePendingCount(projectId)
    fun observeFailedCount(projectId: Long): Flow<Int> = syncQueueDao.observeFailedCount(projectId)
    fun observeConflictCount(projectId: Long): Flow<Int> = syncQueueDao.observeConflictCount(projectId)
    fun observeActiveBackupCount(projectId: Long): Flow<Int> = backupDao.observeActiveBackupCount(projectId)

    fun observeAllQueue(projectId: Long): Flow<List<SyncQueueEntity>> = syncQueueDao.observeAllQueueItems(projectId)
    fun observeFailedItems(projectId: Long): Flow<List<SyncQueueEntity>> = syncQueueDao.observeFailedItems(projectId)
    fun observeConflictedItems(projectId: Long): Flow<List<SyncQueueEntity>> = syncQueueDao.observeConflictedItems(projectId)
    fun observeAvailableBackups(projectId: Long): Flow<List<TemporaryBackupEntity>> = backupDao.observeAvailableBackups(projectId)

    fun observeHistory(projectId: Long, filter: String? = null): Flow<List<SyncHistoryEntity>> {
        return when {
            filter.isNullOrEmpty() || filter == "ALL" -> historyDao.observeHistory(projectId)
            filter in listOf("SUCCESS", "FAILED", "CONFLICT") -> historyDao.observeHistoryByResult(projectId, filter)
            else -> historyDao.observeHistoryByOperation(projectId, filter)
        }
    }

    suspend fun retryItem(id: Long) = syncQueueDao.retryItem(id)
    suspend fun retryAllFailed(projectId: Long) = syncQueueDao.retryAllFailed(projectId)
    suspend fun clearCompletedQueue(projectId: Long) = syncQueueDao.clearCompleted(projectId)
    suspend fun clearHistory(projectId: Long) = historyDao.clearHistory(projectId)

    suspend fun resolveConflict(queueId: Long, overwriteRemote: Boolean) = withContext(Dispatchers.IO) {
        if (overwriteRemote) {
            // Re-queue for upload forcing latest local state
            syncQueueDao.updateStatus(queueId, "PENDING")
        } else {
            // Cancel local pending upload to keep remote state
            syncQueueDao.updateStatus(queueId, "CANCELLED", "User elected to keep remote version")
        }
    }

    private fun normalizePath(path: String): String {
        var clean = path.trim().replace('\\', '/')
        if (!clean.startsWith("/")) clean = "/$clean"
        if (!clean.endsWith("/")) clean = "$clean/"
        return clean
    }
}
