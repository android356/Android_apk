package com.autodeploy.infinityfree.data.saf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafScanner(private val context: Context) {

    suspend fun scanDirectory(treeUri: Uri): List<SafFileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SafFileItem>()
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()

        if (!rootDoc.exists() || !rootDoc.canRead()) {
            return@withContext emptyList()
        }

        scanRecursive(rootDoc, "", results)
        results
    }

    private fun scanRecursive(
        currentDoc: DocumentFile,
        currentRelativeDir: String,
        results: MutableList<SafFileItem>
    ) {
        val children = currentDoc.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            val relPath = if (currentRelativeDir.isEmpty()) name else "$currentRelativeDir/$name"

            if (child.isDirectory) {
                results.add(
                    SafFileItem(
                        displayName = name,
                        relativePath = relPath,
                        uri = child.uri,
                        isDirectory = true,
                        size = 0L,
                        lastModified = child.lastModified()
                    )
                )
                scanRecursive(child, relPath, results)
            } else if (child.isFile) {
                results.add(
                    SafFileItem(
                        displayName = name,
                        relativePath = relPath,
                        uri = child.uri,
                        isDirectory = false,
                        size = child.length(),
                        lastModified = child.lastModified()
                    )
                )
            }
        }
    }

    fun hasPersistedPermission(uri: Uri): Boolean {
        val persistedUriPermissions = context.contentResolver.persistedUriPermissions
        return persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
    }
}
