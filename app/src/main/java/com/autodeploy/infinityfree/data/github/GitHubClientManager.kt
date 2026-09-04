package com.autodeploy.infinityfree.data.github

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class GitHubResult<out T> {
    data class Success<out T>(val data: T) : GitHubResult<T>()
    data class Error(val message: String, val statusCode: Int = -1, val cause: Throwable? = null) : GitHubResult<Nothing>()
}

class GitHubClientManager {

    companion object {
        private const val TAG = "GitHubClientManager"
        private const val BASE_URL = "https://api.github.com"
        private const val TIMEOUT_MS = 15000
    }

    suspend fun testConnection(
        owner: String,
        repo: String,
        branch: String,
        token: String
    ): GitHubResult<String> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            val cleanBranch = branch.trim()

            // 1. Check repository accessibility
            val repoUrl = "$BASE_URL/repos/$cleanOwner/$cleanRepo"
            val repoResponse = makeHttpRequest(repoUrl, "GET", token = token)
            if (repoResponse.statusCode !in 200..299) {
                return@withContext GitHubResult.Error(
                    "Repository '$cleanOwner/$cleanRepo' inaccessible: ${repoResponse.body}",
                    repoResponse.statusCode
                )
            }

            // 2. Check branch
            val branchUrl = "$BASE_URL/repos/$cleanOwner/$cleanRepo/branches/$cleanBranch"
            val branchResponse = makeHttpRequest(branchUrl, "GET", token = token)
            if (branchResponse.statusCode !in 200..299) {
                return@withContext GitHubResult.Error(
                    "Branch '$cleanBranch' not found in '$cleanOwner/$cleanRepo': ${branchResponse.body}",
                    branchResponse.statusCode
                )
            }

            val branchJson = JSONObject(branchResponse.body)
            val commitSha = branchJson.getJSONObject("commit").getString("sha").take(7)

            GitHubResult.Success("Connected Successfully to $cleanOwner/$cleanRepo ($cleanBranch @ $commitSha)")
        } catch (e: Exception) {
            Log.e(TAG, "GitHub test connection error", e)
            GitHubResult.Error("Connection test failed: ${e.localizedMessage ?: "Unknown error"}", cause = e)
        }
    }

    suspend fun getFileSha(
        owner: String,
        repo: String,
        branch: String,
        filePath: String,
        token: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val encodedPath = encodePath(filePath)
            val url = "$BASE_URL/repos/${owner.trim()}/${repo.trim()}/contents/$encodedPath?ref=${branch.trim()}"
            val response = makeHttpRequest(url, "GET", token = token)
            if (response.statusCode in 200..299) {
                val json = JSONObject(response.body)
                return@withContext json.optString("sha", null)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error checking SHA for $filePath: ${e.message}")
            null
        }
    }

    suspend fun uploadOrUpdateFile(
        owner: String,
        repo: String,
        branch: String,
        filePath: String,
        fileBytes: ByteArray,
        commitMessage: String,
        existingSha: String?,
        token: String
    ): GitHubResult<String> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = encodePath(filePath)
            val url = "$BASE_URL/repos/${owner.trim()}/${repo.trim()}/contents/$encodedPath"

            val base64Content = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("message", commitMessage)
                put("content", base64Content)
                put("branch", branch.trim())
                if (!existingSha.isNullOrEmpty()) {
                    put("sha", existingSha)
                }
            }

            val response = makeHttpRequest(url, "PUT", payload.toString(), token)
            if (response.statusCode in 200..299) {
                val json = JSONObject(response.body)
                val newSha = json.getJSONObject("content").getString("sha")
                GitHubResult.Success(newSha)
            } else if (response.statusCode == 409) {
                GitHubResult.Error("Conflict: Remote file was modified concurrently (409 Conflict)", 409)
            } else {
                GitHubResult.Error("GitHub upload failed (${response.statusCode}): ${response.body}", response.statusCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub upload error for $filePath", e)
            GitHubResult.Error("Upload error: ${e.localizedMessage ?: "Unknown"}", cause = e)
        }
    }

    suspend fun deleteFile(
        owner: String,
        repo: String,
        branch: String,
        filePath: String,
        commitMessage: String,
        existingSha: String,
        token: String
    ): GitHubResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = encodePath(filePath)
            val url = "$BASE_URL/repos/${owner.trim()}/${repo.trim()}/contents/$encodedPath"

            val payload = JSONObject().apply {
                put("message", commitMessage)
                put("sha", existingSha)
                put("branch", branch.trim())
            }

            val response = makeHttpRequest(url, "DELETE", payload.toString(), token)
            if (response.statusCode in 200..299) {
                GitHubResult.Success(true)
            } else {
                GitHubResult.Error("GitHub delete failed (${response.statusCode}): ${response.body}", response.statusCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub delete error for $filePath", e)
            GitHubResult.Error("Delete error: ${e.localizedMessage ?: "Unknown"}", cause = e)
        }
    }

    private fun encodePath(path: String): String {
        return path.split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    }

    private data class HttpResponse(val statusCode: Int, val body: String)

    private fun makeHttpRequest(
        urlStr: String,
        method: String,
        body: String? = null,
        token: String? = null
    ): HttpResponse {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "AndroidAutoDeploy-App")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

            if (!token.isNullOrEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }

            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val statusCode = conn.responseCode
            val inputStream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val responseBody = inputStream?.use { stream ->
                BufferedReader(InputStreamReader(stream, "UTF-8")).readText()
            } ?: ""

            return HttpResponse(statusCode, responseBody)
        } finally {
            conn.disconnect()
        }
    }
}
