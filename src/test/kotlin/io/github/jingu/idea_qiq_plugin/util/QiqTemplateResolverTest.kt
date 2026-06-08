package io.github.jingu.idea_qiq_plugin.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure path helpers of [QiqTemplateResolver] — extension
 * stripping (file path -> typed path) and candidate generation (typed path ->
 * files to look up). The VFS walk and the `resolve`/`listTemplatePaths` lookups
 * are exercised manually / by the planned HeavyPlatformTestCase suite (#32).
 */
class QiqTemplateResolverTest {

    private val exts = listOf(".qiq.php", ".qiq", ".php")

    // --- stripTemplateExtension ---------------------------------------------

    @Test
    fun stripsLongestMatchingExtensionFirst() {
        // `.qiq.php` must win over `.php`, otherwise a dangling `.qiq` is left.
        assertEquals("layout/base", QiqTemplateResolver.stripTemplateExtension("layout/base.qiq.php", exts))
    }

    @Test
    fun stripsPlainQiqAndPhpExtensions() {
        assertEquals("partials/menu", QiqTemplateResolver.stripTemplateExtension("partials/menu.qiq", exts))
        assertEquals("page/index", QiqTemplateResolver.stripTemplateExtension("page/index.php", exts))
    }

    @Test
    fun returnsNullForNonTemplateFiles() {
        assertNull(QiqTemplateResolver.stripTemplateExtension("assets/app.css", exts))
        assertNull(QiqTemplateResolver.stripTemplateExtension("README.md", exts))
    }

    @Test
    fun isCaseInsensitiveOnExtension() {
        assertEquals("Page/Home", QiqTemplateResolver.stripTemplateExtension("Page/Home.PHP", exts))
    }

    @Test
    fun respectsConfiguredExtensionOrder() {
        // With only ".php" configured, a ".qiq.php" file keeps its ".qiq" stem.
        assertEquals("layout/base.qiq", QiqTemplateResolver.stripTemplateExtension("layout/base.qiq.php", listOf(".php")))
    }

    // --- buildCandidatePaths -------------------------------------------------

    @Test
    fun appendsEachExtensionWhenPathHasNone() {
        assertEquals(
            listOf("layout/base", "layout/base.qiq.php", "layout/base.qiq", "layout/base.php"),
            QiqTemplateResolver.buildCandidatePaths("layout/base", exts),
        )
    }

    @Test
    fun keepsPathAsIsWhenExtensionAlreadyPresent() {
        // An explicit extension is honoured; no extra candidates are appended.
        assertEquals(listOf("layout/base.qiq.php"), QiqTemplateResolver.buildCandidatePaths("layout/base.qiq.php", exts))
        assertEquals(listOf("page/index.php"), QiqTemplateResolver.buildCandidatePaths("page/index.php", exts))
    }

    @Test
    fun extensionDetectionIsCaseInsensitive() {
        assertEquals(listOf("Page/Home.PHP"), QiqTemplateResolver.buildCandidatePaths("Page/Home.PHP", exts))
    }

    // --- normalizePath -------------------------------------------------------

    @Test
    fun normalizesRelativePath() {
        val n = QiqTemplateResolver.normalizePath("layout/base")!!
        assertEquals(false, n.rootAbsolute)
        assertEquals("layout/base", n.relative)
    }

    @Test
    fun normalizesRootAbsolutePath() {
        val n = QiqTemplateResolver.normalizePath("/layout/base")!!
        assertEquals(true, n.rootAbsolute)
        assertEquals("layout/base", n.relative)
    }

    @Test
    fun collapsesSlashesBeforeStrippingLeadingOne() {
        // Multiple leading slashes must not leave a stray leading slash in the
        // relative part (which would break findFileByRelativePath).
        val n = QiqTemplateResolver.normalizePath("///layout//base")!!
        assertEquals(true, n.rootAbsolute)
        assertEquals("layout/base", n.relative)
    }

    @Test
    fun rejectsBlankAndDynamicPaths() {
        assertNull(QiqTemplateResolver.normalizePath(""))
        assertNull(QiqTemplateResolver.normalizePath("   "))
        assertNull(QiqTemplateResolver.normalizePath("/"))
        assertNull(QiqTemplateResolver.normalizePath("layout \$name"))
        // PHP interpolation is dynamic even without a space, matching the
        // missing-template inspection's static-path gate.
        assertNull(QiqTemplateResolver.normalizePath("/layout/\$name"))
        // Any embedded whitespace (not just a plain space) is dynamic.
        assertNull(QiqTemplateResolver.normalizePath("layout\tbase"))
        // Leading/trailing whitespace is significant at runtime and not trimmed
        // away: such a path is rejected rather than silently resolved as trimmed.
        assertNull(QiqTemplateResolver.normalizePath(" layout/base"))
        assertNull(QiqTemplateResolver.normalizePath("layout/base "))
    }

    // --- normalizeExtensions -------------------------------------------------

    @Test
    fun fallsBackToDefaultsWhenNullOrEmpty() {
        val defaults = listOf(".qiq.php", ".qiq", ".php")
        assertEquals(defaults, QiqTemplateResolver.normalizeExtensions(null))
        assertEquals(defaults, QiqTemplateResolver.normalizeExtensions(emptyList()))
        // A list of only blanks collapses to empty -> defaults.
        assertEquals(defaults, QiqTemplateResolver.normalizeExtensions(listOf("", "   ")))
    }

    @Test
    fun ensuresLeadingDotAndDropsBlanks() {
        assertEquals(
            listOf(".qiq.php", ".php"),
            QiqTemplateResolver.normalizeExtensions(listOf("qiq.php", "  ", ".php")),
        )
    }
}
