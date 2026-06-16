package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.editor.QiqBlockPairHighlightUsagesHandlerFactory

/**
 * Integration coverage for QiqBlockPairHighlightUsagesHandlerFactory (#28 / #32):
 * with the caret on a block opener or closer, the handler highlights both the
 * opener and the closer delimiters.
 */
class QiqBlockPairHighlightFixtureTest : BasePlatformTestCase() {

    private val source = "{{ if (\$x): }}\n<p>a</p>\n{{ endif }}"

    private fun usagesWithCaretOn(token: String): List<String> {
        myFixture.configureByText("page.qiq", source)
        myFixture.editor.caretModel.moveToOffset(source.indexOf(token) + 3)
        val target = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
            ?: error("no element at caret")
        val handler = QiqBlockPairHighlightUsagesHandlerFactory()
            .createHighlightUsagesHandler(myFixture.editor, myFixture.file, target)
            ?: error("no block-pair highlight handler at caret")
        handler.highlightUsages()
        return handler.readUsages.map { r: TextRange -> source.substring(r.startOffset, r.endOffset) }
    }

    fun testCaretOnOpenerHighlightsBothDelimiters() {
        val texts = usagesWithCaretOn("{{ if")
        assertSize(2, texts)
        assertTrue(texts.toString(), texts.any { it.startsWith("{{ if") })
        assertTrue(texts.toString(), texts.any { it.startsWith("{{ endif") })
    }

    fun testCaretOnCloserHighlightsBothDelimiters() {
        val texts = usagesWithCaretOn("{{ endif")
        assertSize(2, texts)
        assertTrue(texts.toString(), texts.any { it.startsWith("{{ if") })
        assertTrue(texts.toString(), texts.any { it.startsWith("{{ endif") })
    }
}
