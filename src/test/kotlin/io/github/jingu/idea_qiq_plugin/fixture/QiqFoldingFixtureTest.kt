package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.editor.QiqFoldingBuilder

/**
 * Integration coverage for QiqFoldingBuilder (#27 / #32): runs the real builder over
 * the parsed Qiq PSI/document. We call the builder directly rather than reading the
 * editor's fold regions, which would also include HTML / injected-PHP folds.
 */
class QiqFoldingFixtureTest : BasePlatformTestCase() {

    private fun descriptors(text: String): List<FoldingDescriptor> {
        val file = myFixture.configureByText("page.qiq", text)
        return QiqFoldingBuilder().buildFoldRegions(file, myFixture.editor.document, false).toList()
    }

    fun testMultiLineBlockFolds() {
        val d = descriptors("{{ if (\$x): }}\n<p>a</p>\n<p>b</p>\n{{ endif }}")
        assertSize(1, d)
        assertEquals("…", QiqFoldingBuilder().getPlaceholderText(d.first().element))
    }

    fun testSingleLineBlockDoesNotFold() {
        // Opener and closer on one line: nothing to collapse.
        assertEmpty(descriptors("{{ if (\$x): }}{{ endif }}"))
    }

    fun testNestedBlocksProduceTwoRegions() {
        val text = "{{ foreach (\$xs as \$x): }}\n{{ if (\$x): }}\n<p>a</p>\n{{ endif }}\n{{ endforeach }}"
        assertSize(2, descriptors(text))
    }
}
