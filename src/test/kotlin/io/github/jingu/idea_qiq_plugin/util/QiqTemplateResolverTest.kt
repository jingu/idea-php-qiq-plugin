package io.github.jingu.idea_qiq_plugin.completion

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure extension-stripping helper that turns a
 * root-relative template file path into the path users type in
 * `setLayout('...')` / `render('...')` etc.
 *
 * The VFS walk and completion firing are exercised manually / by the
 * planned HeavyPlatformTestCase integration suite.
 */
class QiqTemplatePathCompletionContributorTest {

    private val exts = listOf(".qiq.php", ".qiq", ".php")

    @Test
    fun stripsLongestMatchingExtensionFirst() {
        // `.qiq.php` must win over `.php`, otherwise a dangling `.qiq` is left.
        assertEquals(
            "layout/base",
            QiqTemplatePathCompletionContributor.stripTemplateExtension("layout/base.qiq.php", exts),
        )
    }

    @Test
    fun stripsPlainQiqAndPhpExtensions() {
        assertEquals("partials/menu", QiqTemplatePathCompletionContributor.stripTemplateExtension("partials/menu.qiq", exts))
        assertEquals("page/index", QiqTemplatePathCompletionContributor.stripTemplateExtension("page/index.php", exts))
    }

    @Test
    fun returnsNullForNonTemplateFiles() {
        assertNull(QiqTemplatePathCompletionContributor.stripTemplateExtension("assets/app.css", exts))
        assertNull(QiqTemplatePathCompletionContributor.stripTemplateExtension("README.md", exts))
    }

    @Test
    fun isCaseInsensitiveOnExtension() {
        assertEquals("Page/Home", QiqTemplatePathCompletionContributor.stripTemplateExtension("Page/Home.PHP", exts))
    }

    @Test
    fun respectsConfiguredExtensionOrder() {
        // With only ".php" configured, a ".qiq.php" file keeps its ".qiq" stem.
        assertEquals(
            "layout/base.qiq",
            QiqTemplatePathCompletionContributor.stripTemplateExtension("layout/base.qiq.php", listOf(".php")),
        )
    }
}
