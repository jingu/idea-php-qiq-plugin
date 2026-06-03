package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import io.github.jingu.idea_qiq_plugin.block.QiqBlockModel
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

/**
 * Highlights the matching Qiq block directive when the caret rests on either the
 * opener or the closer — `{{ if (...): }}` ↔ `{{ endif }}`, and likewise for
 * `foreach`, `for`, `setSection`, `setBlock`.
 *
 * Block pairs span several tokens and (for nested same-keyword blocks) cannot be
 * matched by the token-level brace matcher, so they are resolved through the shared
 * [QiqBlockModel] instead.
 */
class QiqBlockPairHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactoryBase() {

    override fun createHighlightUsagesHandler(
        editor: Editor,
        file: PsiFile,
        target: PsiElement,
    ): HighlightUsagesHandlerBase<PsiElement>? {
        if (!file.viewProvider.languages.any { it.isKindOf(QiqTemplateLanguage) }) return null

        val offset = editor.caretModel.offset
        val block = QiqBlockModel.blockAtDelimiter(editor.document.charsSequence, offset) ?: return null

        return Handler(editor, file, listOf(block.open, block.close))
    }

    private class Handler(
        editor: Editor,
        file: PsiFile,
        private val ranges: List<TextRange>,
    ) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {

        override fun getTargets(): List<PsiElement> = listOf(myFile)

        override fun selectTargets(
            targets: List<PsiElement>,
            selectionConsumer: Consumer<in List<PsiElement>>,
        ) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: List<PsiElement>) {
            ranges.forEach(myReadUsages::add)
        }
    }
}
