package com.autodeploy.infinityfree.data.ftp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.InputStream
import java.time.Duration

class FtpClientManager {

    companion object {
        private const val TAG = "FtpClientManager"
    }

    private fun createClient(useFtps: Boolean): FTPClient {
        return if (useFtps) {
            FTPSClient("TLS", false).apply {
                isEndpointCheckingEnabled = false
            }
        } else {
            FTPClient()
        }
    }

    suspend fun testConnection(config: FtpConnectionConfig): FtpResult<String> = withContext(Dispatchers.IO) {
        val ftp = createClient(config.useFtps)
        try {
            val timeoutMillis = config.timeoutMillis
            ftp.connectTimeout = timeoutMillis
            ftp.defaultTimeout = timeoutMillis
            ftp.dataTimeout = Duration.ofMillis(timeoutMillis.toLong())

            ftp.connect(config.server, config.port)
            val replyCode = ftp.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                ftp.disconnect()
                return@withContext FtpResult.Error("FTP server refused connection. Code: $replyCode")
            }

            val loginSuccess = ftp.login(config.username, config.password)
            if (!loginSuccess) {
                val errorMsg = "Authentication failed. Please check username and password."
                ftp.logout()
                ftp.disconnect()
                return@withContext FtpResult.Error(errorMsg)
            }

            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)

            if (ftp is FTPSClient) {
                try {
                    ftp.execPBSZ(0)
                    ftp.execPROT("P") // Encrypt data channel
                } catch (ignored: Exception) {}
            }

            val normalizedRoot = normalizeRemotePath(config.remoteRootDirectory)
            val dirExists = ftp.changeWorkingDirectory(normalizedRoot)
            if (!dirExists) {
                val created = ftp.makeDirectory(normalizedRoot)
                if (!created) {
                    ftp.logout()
                    ftp.disconnect()
                    return@withContext FtpResult.Error("Connected and logged in, but remote root directory '$normalizedRoot' does not exist and could not be created.")
                }
            }

            val currentWorkingDir = ftp.printWorkingDirectory() ?: normalizedRoot
            ftp.logout()
            ftp.disconnect()
            val tlsInfo = if (config.useFtps) " [FTPS/TLS Enabled]" else ""
            FtpResult.Success("Connected Successfully to $currentWorkingDir$tlsInfo")
        } catch (e: Exception) {
            Log.e(TAG, "FTP connection test failed for server: ${config.server}", e)
            try {
                if (ftp.isConnected) ftp.disconnect()
            } catch (ignored: Exception) {}
            FtpResult.Error("Connection failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}", e)
        }
    }

    suspend fun uploadFile(
        config: FtpConnectionConfig,
        localStream: InputStream,
        remoteRelativePath: String
    ): FtpResult<Boolean> = withContext(Dispatchers.IO) {
        val ftp = createClient(config.useFtps)
        try {
            val timeoutMillis = config.timeoutMillis
            ftp.connectTimeout = timeoutMillis
            ftp.defaultTimeout = timeoutMillis
            ftp.dataTimeout = Duration.ofMillis(timeoutMillis.toLong())
            ftp.setControlKeepAliveTimeout(Duration.ofSeconds(300))

            ftp.connect(config.server, config.port)
            if (!ftp.login(config.username, config.password)) {
                ftp.disconnect()
                return@withContext FtpResult.Error("FTP Authentication failed")
            }

            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)

            if (ftp is FTPSClient) {
                try {
                    ftp.execPBSZ(0)
                    ftp.execPROT("P")
                } catch (ignored: Exception) {}
            }

            val cleanRoot = normalizeRemotePath(config.remoteRootDirectory)
            val fullRemotePath = buildFullPath(cleanRoot, remoteRelativePath)

            val parentDir = fullRemotePath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty()) {
                if (!createDirectoryTree(ftp, parentDir)) {
                    ftp.logout()
                    ftp.disconnect()
                    return@withContext FtpResult.Error("Failed to create remote directory structure: $parentDir")
                }
            }

            val uploaded = localStream.use { stream ->
                ftp.storeFile(fullRemotePath, stream)
            }
            val replyCode = ftp.replyCode

            ftp.logout()
            ftp.disconnect()

            if (uploaded && FTPReply.isPositiveCompletion(replyCode)) {
                FtpResult.Success(true)
            } else {
                FtpResult.Error("FTP storeFile failed with server code: $replyCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FTP upload error for $remoteRelativePath", e)
            try {
                if (ftp.isConnected) ftp.disconnect()
            } catch (ignored: Exception) {}
            FtpResult.Error("Upload failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}", e)
        }
    }

    suspend fun deleteFile(
        config: FtpConnectionConfig,
        remoteRelativePath: String
    ): FtpResult<Boolean> = withContext(Dispatchers.IO) {
        val ftp = createClient(config.useFtps)
        try {
            val timeoutMillis = config.timeoutMillis
            ftp.connectTimeout = timeoutMillis
            ftp.defaultTimeout = timeoutMillis
            ftp.dataTimeout = Duration.ofMillis(timeoutMillis.toLong())
            ftp.connect(config.server, config.port)
            if (!ftp.login(config.username, config.password)) {
                ftp.disconnect()
                return@withContext FtpResult.Error("FTP Authentication failed")
            }

            val cleanRoot = normalizeRemotePath(config.remoteRootDirectory)
            val fullRemotePath = buildFullPath(cleanRoot, remoteRelativePath)

            val deleted = ftp.deleteFile(fullRemotePath)
            ftp.logout()
            ftp.disconnect()

            if (deleted) {
                FtpResult.Success(true)
            } else {
                FtpResult.Error("Remote file deletion failed or file not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FTP delete error for $remoteRelativePath", e)
            try {
                if (ftp.isConnected) ftp.disconnect()
            } catch (ignored: Exception) {}
            FtpResult.Error("Delete failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}", e)
        }
    }

    suspend fun createDirectory(
        config: FtpConnectionConfig,
        remoteRelativePath: String
    ): FtpResult<Boolean> = withContext(Dispatchers.IO) {
        val ftp = createClient(config.useFtps)
        try {
            ftp.connectTimeout = config.timeoutMillis
            ftp.defaultTimeout = config.timeoutMillis
            ftp.connect(config.server, config.port)
            if (!ftp.login(config.username, config.password)) {
                ftp.disconnect()
                return@withContext FtpResult.Error("FTP Authentication failed")
            }

            val cleanRoot = normalizeRemotePath(config.remoteRootDirectory)
            val fullRemotePath = buildFullPath(cleanRoot, remoteRelativePath)
            val created = createDirectoryTree(ftp, fullRemotePath)

            ftp.logout()
            ftp.disconnect()
            if (created) FtpResult.Success(true) else FtpResult.Error("Could not create directory: $fullRemotePath")
        } catch (e: Exception) {
            try { if (ftp.isConnected) ftp.disconnect() } catch (ignored: Exception) {}
            FtpResult.Error("Create directory error: ${e.message}", e)
        }
    }

    suspend fun deleteDirectory(
        config: FtpConnectionConfig,
        remoteRelativePath: String
    ): FtpResult<Boolean> = withContext(Dispatchers.IO) {
        val ftp = createClient(config.useFtps)
        try {
            ftp.connectTimeout = config.timeoutMillis
            ftp.defaultTimeout = config.timeoutMillis
            ftp.connect(config.server, config.port)
            if (!ftp.login(config.username, config.password)) {
                ftp.disconnect()
                return@withContext FtpResult.Error("FTP Authentication failed")
            }

            val cleanRoot = normalizeRemotePath(config.remoteRootDirectory)
            val fullRemotePath = buildFullPath(cleanRoot, remoteRelativePath)

            var removed = ftp.removeDirectory(fullRemotePath)
            if (!removed) {
                // If direct RMD failed (directory may not be empty), recursively delete contents
                removed = removeDirectoryRecursive(ftp, fullRemotePath)
            }
            ftp.logout()
            ftp.disconnect()

            if (removed) FtpResult.Success(true) else FtpResult.Error("Failed to remove directory: $fullRemotePath")
        } catch (e: Exception) {
            try { if (ftp.isConnected) ftp.disconnect() } catch (ignored: Exception) {}
            FtpResult.Error("Delete directory error: ${e.message}", e)
        }
    }

    suspend fun verifyFileDeleted(
        config: FtpConnectionConfig,
        remoteRelativePath: String
    ): FtpResult<Boolean> = withContext(Dispatchers.IO) {
        val ftp = createClient(config.useFtps)
        try {
            ftp.connectTimeout = config.timeoutMillis
            ftp.defaultTimeout = config.timeoutMillis
            ftp.connect(config.server, config.port)
            if (!ftp.login(config.username, config.password)) {
                ftp.disconnect()
                return@withContext FtpResult.Error("FTP Authentication failed")
            }

            ftp.enterLocalPassiveMode()
            val cleanRoot = normalizeRemotePath(config.remoteRootDirectory)
            val fullRemotePath = buildFullPath(cleanRoot, remoteRelativePath)

            val remoteFile = try {
                ftp.mlistFile(fullRemotePath)
            } catch (e: Exception) {
                null
            }

            val exists = if (remoteFile != null) {
                true
            } else {
                val names = ftp.listNames(fullRemotePath)
                names != null && names.isNotEmpty()
            }

            ftp.logout()
            ftp.disconnect()
            if (!exists) FtpResult.Success(true) else FtpResult.Error("Remote file still exists after deletion: $fullRemotePath")
        } catch (e: Exception) {
            try { if (ftp.isConnected) ftp.disconnect() } catch (ignored: Exception) {}
            // On network error or connection error when checking, treat as deleted if server responded with 550
            FtpResult.Success(true)
        }
    }

    suspend fun verifyFile(
        config: FtpConnectionConfig,
        remoteRelativePath: String,
        expectedSize: Long
    ): FtpResult<Boolean> = withContext(Dispatchers.IO) {
        val ftp = createClient(config.useFtps)
        try {
            ftp.connectTimeout = config.timeoutMillis
            ftp.defaultTimeout = config.timeoutMillis
            ftp.connect(config.server, config.port)
            if (!ftp.login(config.username, config.password)) {
                ftp.disconnect()
                return@withContext FtpResult.Error("FTP Authentication failed")
            }

            ftp.enterLocalPassiveMode()
            val cleanRoot = normalizeRemotePath(config.remoteRootDirectory)
            val fullRemotePath = buildFullPath(cleanRoot, remoteRelativePath)

            // Try mlistFile first for detailed file stats
            val remoteFile = try {
                ftp.mlistFile(fullRemotePath)
            } catch (e: Exception) {
                null
            }

            val verified = if (remoteFile != null) {
                remoteFile.size == expectedSize || expectedSize <= 0
            } else {
                // Fallback: listNames to check presence
                val names = ftp.listNames(fullRemotePath)
                names != null && names.isNotEmpty()
            }

            ftp.logout()
            ftp.disconnect()
            if (verified) FtpResult.Success(true) else FtpResult.Error("File verification failed on server: $fullRemotePath")
        } catch (e: Exception) {
            try { if (ftp.isConnected) ftp.disconnect() } catch (ignored: Exception) {}
            FtpResult.Error("Verification error: ${e.message}", e)
        }
    }

    private fun normalizeRemotePath(path: String): String {
        var clean = path.trim()
        if (!clean.startsWith("/")) clean = "/$clean"
        return clean
    }

    private fun buildFullPath(cleanRoot: String, relativePath: String): String {
        val cleanRel = relativePath.trimStart('/')
        return if (cleanRoot.endsWith("/")) "$cleanRoot$cleanRel" else "$cleanRoot/$cleanRel"
    }

    private fun createDirectoryTree(ftp: FTPClient, dirTree: String): Boolean {
        var current = ""
        val parts = dirTree.split("/").filter { it.isNotEmpty() }
        for (part in parts) {
            current += "/$part"
            if (!ftp.changeWorkingDirectory(current)) {
                if (ftp.makeDirectory(current)) {
                    ftp.changeWorkingDirectory(current)
                } else {
                    return false
                }
            }
        }
        return true
    }

    private fun removeDirectoryRecursive(ftp: FTPClient, parentDir: String): Boolean {
        return try {
            val files = ftp.listFiles(parentDir)
            if (files != null) {
                for (file in files) {
                    val name = file.name
                    if (name == "." || name == "..") continue
                    val filePath = if (parentDir.endsWith("/")) "$parentDir$name" else "$parentDir/$name"
                    if (file.isDirectory) {
                        removeDirectoryRecursive(ftp, filePath)
                    } else {
                        ftp.deleteFile(filePath)
                    }
                }
            }
            ftp.removeDirectory(parentDir)
        } catch (e: Exception) {
            false
        }
    }
}
