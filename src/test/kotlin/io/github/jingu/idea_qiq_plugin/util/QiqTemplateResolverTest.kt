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
}
