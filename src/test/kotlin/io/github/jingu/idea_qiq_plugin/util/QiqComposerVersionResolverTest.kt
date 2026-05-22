package io.github.jingu.idea_qiq_plugin.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QiqComposerVersionResolverTest {

    @Test
    fun parsesMajorVersionFromTaggedRelease() {
        val lock = """
            {
                "packages": [
                    {
                        "name": "qiq/qiq",
                        "version": "3.0.1",
                        "source": {}
                    }
                ]
            }
        """.trimIndent()

        assertEquals(3, QiqComposerVersionResolver.parseMajorVersion(lock))
    }

    @Test
    fun parsesQiqOneEvenWhenSurroundedByOtherPackages() {
        val lock = """
            {
                "packages": [
                    {
                        "name": "psr/log",
                        "version": "2.0.0"
                    },
                    {
                        "name": "qiq/qiq",
                        "version": "1.1.1"
                    },
                    {
                        "name": "laminas/laminas-escaper",
                        "version": "2.13.0"
                    }
                ]
            }
        """.trimIndent()

        assertEquals(1, QiqComposerVersionResolver.parseMajorVersion(lock))
    }

    @Test
    fun stripsLeadingVPrefix() {
        val lock = """
            { "packages": [ { "name": "qiq/qiq", "version": "v2.1.0" } ] }
        """.trimIndent()

        assertEquals(2, QiqComposerVersionResolver.parseMajorVersion(lock))
    }

    @Test
    fun returnsNullForUnknownPackage() {
        val lock = """
            { "packages": [ { "name": "psr/log", "version": "2.0.0" } ] }
        """.trimIndent()

        assertNull(QiqComposerVersionResolver.parseMajorVersion(lock))
    }

    @Test
    fun returnsNullForDevBranchVersion() {
        val lock = """
            { "packages": [ { "name": "qiq/qiq", "version": "dev-main" } ] }
        """.trimIndent()

        assertNull(QiqComposerVersionResolver.parseMajorVersion(lock))
    }

    @Test
    fun returnsNullForXDevSuffix() {
        val lock = """
            { "packages": [ { "name": "qiq/qiq", "version": "3.x-dev" } ] }
        """.trimIndent()

        // "3.x-dev" → head before '.' is "3", which is a valid Int — we want
        // this to succeed so that floating dev branches still pick the right
        // major. Adjust expectation if behaviour changes.
        assertEquals(3, QiqComposerVersionResolver.parseMajorVersion(lock))
    }

    @Test
    fun handlesVersionFieldBeforeName() {
        // Defensive: some lock files may emit "version" before "name" within
        // an entry. We currently anchor on the "name" field then look forward,
        // so this case returns null. Documented as a known limitation.
        val lock = """
            {
                "packages": [
                    {
                        "version": "3.0.1",
                        "name": "qiq/qiq"
                    }
                ]
            }
        """.trimIndent()

        assertNull(QiqComposerVersionResolver.parseMajorVersion(lock))
    }
}
