package com.autodeploy.infinityfree.data.ftp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.InputStream
import java.time.Duration

class FtpClientManager {

    companion object {
        private const val TAG = "FtpClientManager"
    }

    suspend fun testConnection(config: FtpConnectionConfig): FtpResult<String> = withContext(Dispatchers.IO) {
        val ftp = FTPClient()
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

            // Normalize remote root directory
            val normalizedRoot = normalizeRemotePath(config.remoteRootDirectory)
            val dirExists = ftp.changeWorkingDirectory(normalizedRoot)
            if (!dirExists) {
                // Try creating remote root directory if not present
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
            FtpResult.Success("Connected Successfully to $currentWorkingDir")
        } catch (e: Exception) {
            Log.e(TAG, "FTP connection test failed", e)
            try {
                if (ftp.isConnected) {
                    ftp.disconnect()
                }
            } catch (ignored: Exception) {}
            FtpResult.Error("Connection failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}", e)
        }
    }

    suspend fun uploadFile(
        config: FtpConnectionConfig,
        localStream: InputStream,
        remoteRelativePath: String
    ): FtpResult<Boolean> = withContext(Dispatchers.IO) {
        val ftp = FTPClient()
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

            val cleanRoot = normalizeRemotePath(config.remoteRootDirectory)
            val fullRemotePath = if (cleanRoot.endsWith("/")) {
                cleanRoot + remoteRelativePath.trimStart('/')
            } else {
                "$cleanRoot/${remoteRelativePath.trimStart('/')}"
            }

            // Ensure directory path exists
            val parentDir = fullRemotePath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty()) {
                if (!createDirectoryTree(ftp, parentDir)) {
                    ftp.logout()
                    ftp.disconnect()
                    return@withContext FtpResult.Error("Failed to create remote directory structure: $parentDir")
                }
            }

            val uploaded = ftp.storeFile(fullRemotePath, localStream)
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
        val ftp = FTPClient()
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
            val fullRemotePath = if (cleanRoot.endsWith("/")) {
                cleanRoot + remoteRelativePath.trimStart('/')
            } else {
                "$cleanRoot/${remoteRelativePath.trimStart('/')}"
            }

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

    private fun normalizeRemotePath(path: String): String {
        var clean = path.trim()
        if (!clean.startsWith("/")) clean = "/$clean"
        return clean
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
}
