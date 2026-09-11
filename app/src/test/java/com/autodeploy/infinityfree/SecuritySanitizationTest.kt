package com.autodeploy.infinityfree

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecuritySanitizationTest {

    private fun sanitizeErrorMessage(raw: String?): String {
        if (raw == null) return ""
        var sanitized = raw
        // Mask GitHub tokens
        sanitized = sanitized.replace(Regex("""(ghp_[a-zA-Z0-9]{20,}|github_pat_[a-zA-Z0-9_]{20,})"""), "[REDACTED_TOKEN]")
        // Mask Bearer tokens
        sanitized = sanitized.replace(Regex("""Bearer\s+[a-zA-Z0-9_\-\.]+""", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
        // Mask passwords in URLs or logs
        sanitized = sanitized.replace(Regex("""(:)[^:/@\s]+(@)"""), "$1[REDACTED]$2")
        sanitized = sanitized.replace(Regex("""password\s*=\s*['"]?[^\s,;&'"]+['"]?""", RegexOption.IGNORE_CASE), "password=[REDACTED]")
        return sanitized
    }

    @Test
    fun testGitHubTokenRedaction() {
        val raw = "Failed request with ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890 to GitHub"
        val sanitized = sanitizeErrorMessage(raw)
        assertFalse(sanitized.contains("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"))
        assertTrue(sanitized.contains("[REDACTED_TOKEN]"))
    }

    @Test
    fun testGitHubFineGrainedTokenRedaction() {
        val raw = "Error calling github_pat_11AAAAAAA0000000000000_1234567890abcdef"
        val sanitized = sanitizeErrorMessage(raw)
        assertFalse(sanitized.contains("github_pat_11AAAAAAA0000000000000_1234567890abcdef"))
        assertTrue(sanitized.contains("[REDACTED_TOKEN]"))
    }

    @Test
    fun testFtpUrlPasswordRedaction() {
        val raw = "Connection failed to ftp://if0_38402941:superSecretPass123!@ftpupload.net:21"
        val sanitized = sanitizeErrorMessage(raw)
        assertFalse(sanitized.contains("superSecretPass123!"))
        assertTrue(sanitized.contains("ftp://if0_38402941:[REDACTED]@ftpupload.net:21"))
    }

    @Test
    fun testBearerTokenRedaction() {
        val raw = "HTTP 401 Unauthorized for Authorization: Bearer secret_api_key_value_here"
        val sanitized = sanitizeErrorMessage(raw)
        assertFalse(sanitized.contains("secret_api_key_value_here"))
        assertTrue(sanitized.contains("Bearer [REDACTED]"))
    }

    @Test
    fun testPasswordAssignmentRedaction() {
        val raw = "Login failed with user=admin, password=mySecurePassword; please retry"
        val sanitized = sanitizeErrorMessage(raw)
        assertFalse(sanitized.contains("mySecurePassword"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
    }
}
