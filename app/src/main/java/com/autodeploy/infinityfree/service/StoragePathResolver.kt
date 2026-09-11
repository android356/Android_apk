package com.autodeploy.infinityfree.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.security.MessageDigest

object StoragePathResolver {
    private const val TAG = "StoragePathResolver"

    /**
     * Attempts to resolve a Uri string (SAF tree URI, file://, or raw filesystem path)
     * to a direct filesystem File directory.
     */
    fun resolveToFile(context: Context? = null, uriString: String?): File? {
        if (uriString.isNullOrBlank()) return null

        try {
            // Case 1: Direct filesystem path
            if (uriString.startsWith("/") && !uriString.startsWith("/tree/")) {
                val file = File(uriString)
                if (file.exists()) return file
            }

            // Case 2: file:// URI
            if (uriString.startsWith("file://")) {
                val path = Uri.parse(uriString).path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) return file
                }
            }

            // Case 3: SAF Tree URI or content URI
            val uri = Uri.parse(uriString)
            if (DocumentsContract.isTreeUri(uri) || uri.scheme == "content") {
                val docId = try {
                    DocumentsContract.getTreeDocumentId(uri)
                } catch (e: Exception) {
                    val path = uri.path ?: ""
                    if (path.contains("/tree/")) {
                        URLDecoder.decode(path.substringAfter("/tree/"), "UTF-8")
                    } else null
                }

                if (docId != null) {
                    val decodedDocId = try {
                        URLDecoder.decode(docId, "UTF-8")
                    } catch (e: Exception) {
                        docId
                    }
                    val file = resolveDocIdToFile(decodedDocId)
                    if (file != null && file.exists()) {
                        return file
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve uriString $uriString to file", e)
        }

        return null
    }

    private fun resolveDocIdToFile(docId: String): File? {
        // e.g. "primary:Documents/Project"
        if (docId.startsWith("primary:", ignoreCase = true)) {
            val relativePath = docId.substringAfter(":")
            val externalDir = Environment.getExternalStorageDirectory()
            val file = if (relativePath.isEmpty()) externalDir else File(externalDir, relativePath)
            return file
        }

        // e.g. "1234-5678:MyProject" (SD Card)
        if (docId.contains(":")) {
            val parts = docId.split(":", limit = 2)
            val volumeId = parts[0]
            val relativePath = parts.getOrElse(1) { "" }

            val sdCardDir = File("/storage/$volumeId")
            if (sdCardDir.exists()) {
                val file = if (relativePath.isEmpty()) sdCardDir else File(sdCardDir, relativePath)
                return file
            }

            val mediaRwDir = File("/mnt/media_rw/$volumeId")
            if (mediaRwDir.exists()) {
                val file = if (relativePath.isEmpty()) mediaRwDir else File(mediaRwDir, relativePath)
                return file
            }
        }

        // Downloads raw document ID
        if (docId.startsWith("raw:")) {
            val rawPath = docId.substringAfter("raw:")
            val file = File(rawPath)
            if (file.exists()) return file
        }

        return null
    }

    /**
     * Computes the SHA-256 hash of a given file.
     */
    fun calculateSha256(file: File): String {
        if (!file.exists() || !file.canRead()) return ""
        return file.inputStream().use { input ->
            calculateSha256(input)
        }
    }

    /**
     * Computes the SHA-256 hash from an InputStream.
     */
    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
