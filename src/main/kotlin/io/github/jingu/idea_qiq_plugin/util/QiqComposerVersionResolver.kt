package io.github.jingu.idea_qiq_plugin.util

/**
 * Parses a `composer.lock` payload to find the major version of `qiq/qiq`.
 *
 * Kept as a pure function (no IntelliJ Platform deps) so the parsing logic
 * is unit-testable without spinning up a fixture project.
 *
 * The lock file is generated machine-output JSON, so a single tolerant regex
 * is sufficient. We avoid pulling in a JSON library to keep plugin
 * dependencies minimal.
 */
object QiqComposerVersionResolver {

    // The Composer package name is "qiq/qiq" (not "qiqphp/qiq", which is the
    // GitHub org/repo). Verified across Qiq 1.x, 2.x and 3.x composer.json.
    private const val PACKAGE_NAME = "qiq/qiq"

    /**
     * Returns the major version (e.g. 1, 2, 3) of qiq/qiq declared in the
     * given composer.lock content, or null when the package is not present
     * or the version string cannot be parsed (e.g. `dev-main`, `dev-master`).
     */
    fun parseMajorVersion(lockContent: String): Int? {
        val nameIndex = findPackageNameIndex(lockContent) ?: return null
        val version = findVersionAfter(lockContent, nameIndex) ?: return null
        return extractMajor(version)
    }

    private fun findPackageNameIndex(content: String): Int? {
        // Match `"name": "qiq/qiq"` with flexible whitespace.
        val nameRegex = Regex(
            """"name"\s*:\s*"${Regex.escape(PACKAGE_NAME)}"""",
        )
        return nameRegex.find(content)?.range?.first
    }

    private fun findVersionAfter(content: String, fromIndex: Int): String? {
        // composer.lock package entries are JSON objects; we walk forward from
        // the matched "name" key and grab the next "version" field within the
        // same object (i.e. before the closing `}` of that entry).
        val objectEnd = findObjectEnd(content, fromIndex) ?: content.length
        val slice = content.substring(fromIndex, objectEnd)
        val versionRegex = Regex(""""version"\s*:\s*"([^"]+)"""")
        return versionRegex.find(slice)?.groupValues?.get(1)
    }

    private fun findObjectEnd(content: String, fromIndex: Int): Int? {
        // Walk forward respecting nesting depth and quoted strings.
        var depth = 1 // we're already inside the object that holds "name"
        var i = fromIndex
        var inString = false
        var escaped = false
        while (i < content.length) {
            val c = content[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> { /* skip */ }
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private fun extractMajor(version: String): Int? {
        // Strip a leading `v` (e.g. "v3.0.1") and take the prefix up to the
        // first dot. Reject anything that does not parse as an integer (e.g.
        // "dev-main", "3.x-dev").
        val trimmed = version.trim().removePrefix("v")
        val head = trimmed.substringBefore('.')
        return head.toIntOrNull()
    }
}
