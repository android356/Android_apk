package com.autodeploy.infinityfree

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class SnapshotManifestIntegrityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    data class ManifestEntry(
        val path: String,
        val size: Long,
        val sha256: String
    )

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyIntegrity(baseDir: File, manifest: List<ManifestEntry>): Boolean {
        if (!baseDir.exists() || !baseDir.isDirectory) return false
        for (item in manifest) {
            val file = File(baseDir, item.path)
            if (!file.exists() || file.length() != item.size) {
                return false
            }
            val actualHash = calculateSha256(file)
            if (!actualHash.equals(item.sha256, ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    @Test
    fun testValidSnapshotPassesIntegrityCheck() {
        val baseDir = tempFolder.newFolder("snapshot_v001")
        val file1 = File(baseDir, "index.html").apply { writeText("<html><body>Hello World</body></html>") }
        val file2 = File(baseDir, "css/style.css").apply {
            parentFile.mkdirs()
            writeText("body { color: red; }")
        }

        val manifest = listOf(
            ManifestEntry("index.html", file1.length(), calculateSha256(file1)),
            ManifestEntry("css/style.css", file2.length(), calculateSha256(file2))
        )

        val isValid = verifyIntegrity(baseDir, manifest)
        assertTrue("Valid snapshot files should pass integrity verification", isValid)
    }

    @Test
    fun testModifiedContentFailsIntegrityCheck() {
        val baseDir = tempFolder.newFolder("snapshot_tampered")
        val file1 = File(baseDir, "index.html").apply { writeText("original content") }
        val originalSha = calculateSha256(file1)

        val manifest = listOf(
            ManifestEntry("index.html", file1.length(), originalSha)
        )

        // Modify content with same length
        file1.writeText("modified content")

        val isValid = verifyIntegrity(baseDir, manifest)
        assertFalse("Tampered content must fail integrity verification", isValid)
    }

    @Test
    fun testModifiedSizeFailsIntegrityCheck() {
        val baseDir = tempFolder.newFolder("snapshot_resized")
        val file1 = File(baseDir, "data.txt").apply { writeText("short text") }
        val originalSha = calculateSha256(file1)
        val originalSize = file1.length()

        val manifest = listOf(
            ManifestEntry("data.txt", originalSize, originalSha)
        )

        // Append more content changing size
        file1.appendText(" appended extra text")

        val isValid = verifyIntegrity(baseDir, manifest)
        assertFalse("Modified file size must fail integrity verification", isValid)
    }

    @Test
    fun testMissingFileFailsIntegrityCheck() {
        val baseDir = tempFolder.newFolder("snapshot_missing")
        val file1 = File(baseDir, "index.html").apply { writeText("content") }

        val manifest = listOf(
            ManifestEntry("index.html", file1.length(), calculateSha256(file1)),
            ManifestEntry("deleted.js", 123L, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        )

        val isValid = verifyIntegrity(baseDir, manifest)
        assertFalse("Missing file in snapshot must fail integrity verification", isValid)
    }
}
