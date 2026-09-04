package com.autodeploy.infinityfree.data.ignore

class IgnoreRuleMatcher(customPatterns: List<String> = emptyList()) {

    private val defaultIgnorePatterns = listOf(
        ".git/**",
        ".git",
        ".gitignore",
        ".DS_Store",
        "Thumbs.db",
        "*.tmp",
        "*.temp",
        "*~",
        ".idea/**",
        ".idea",
        ".vscode/**",
        ".vscode",
        "node_modules/**",
        "node_modules",
        ".gradle/**",
        ".gradle",
        "build/**",
        "build"
    )

    private val allPatterns: List<String> = (defaultIgnorePatterns + customPatterns)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    fun isIgnored(relativePath: String): Boolean {
        val normalized = relativePath.trim().replace('\\', '/').trimStart('/')
        if (normalized.isEmpty()) return false

        for (pattern in allPatterns) {
            if (matchPattern(normalized, pattern)) {
                return true
            }
        }
        return false
    }

    private fun matchPattern(path: String, pattern: String): Boolean {
        val cleanPattern = pattern.trim().replace('\\', '/').trimStart('/')
        if (cleanPattern.endsWith("/**")) {
            val prefix = cleanPattern.removeSuffix("/**")
            if (path == prefix || path.startsWith("$prefix/")) return true
        }
        if (cleanPattern.endsWith("/")) {
            val prefix = cleanPattern.removeSuffix("/")
            if (path == prefix || path.startsWith("$prefix/")) return true
        }
        if (cleanPattern.startsWith("*.")) {
            val ext = cleanPattern.removePrefix("*")
            if (path.endsWith(ext, ignoreCase = true)) return true
        }
        if (cleanPattern.contains("/")) {
            if (path == cleanPattern || path.startsWith("$cleanPattern/")) return true
        } else {
            // Pattern matches file or directory name anywhere in hierarchy
            val segments = path.split("/")
            if (cleanPattern.startsWith("*") && cleanPattern.endsWith("*")) {
                val sub = cleanPattern.removePrefix("*").removeSuffix("*")
                if (segments.any { it.contains(sub, ignoreCase = true) }) return true
            } else if (cleanPattern.startsWith("*")) {
                val suffix = cleanPattern.removePrefix("*")
                if (segments.any { it.endsWith(suffix, ignoreCase = true) }) return true
            } else {
                if (segments.any { it.equals(cleanPattern, ignoreCase = true) }) return true
            }
        }
        return false
    }
}
