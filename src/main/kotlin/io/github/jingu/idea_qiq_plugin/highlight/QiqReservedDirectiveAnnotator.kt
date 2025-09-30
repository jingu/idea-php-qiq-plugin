package io.github.jingu.idea_qiq_plugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost

/**
 * ハイライトから外れてしまう予約語系ディレクティブ (extends 等) の識別子に
 * Qiq 固有のテキスト属性を適用するための Annotator。
 */
class QiqReservedDirectiveAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is QiqCodeHost) return

        val match = findReservedDirectiveSpan(element.text) ?: return

        val startOffset = element.textRange.startOffset + match.startOffset
        val endOffset = startOffset + match.length

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startOffset, endOffset))
            .textAttributes(QiqHighlighterKeys.FUNCTION)
            .create()
    }

    companion object {
        private val RESERVED_DIRECTIVE_FUNCTIONS = setOf("extends")

        internal fun findReservedDirectiveSpan(text: String): Span? {
            var index = 0
            val length = text.length

            while (index < length && text[index].isWhitespace()) {
                index++
            }

            if (index >= length) return null
            val nameStart = index

            while (index < length && (text[index].isLetterOrDigit() || text[index] == '_')) {
                index++
            }

            if (index == nameStart) return null
            val name = text.substring(nameStart, index)
            if (RESERVED_DIRECTIVE_FUNCTIONS.none { it.equals(name, ignoreCase = true) }) return null

            var lookahead = index
            while (lookahead < length && text[lookahead].isWhitespace()) {
                lookahead++
            }

            if (lookahead >= length || text[lookahead] != '(') return null

            return Span(nameStart, index - nameStart)
        }
    }

    internal data class Span(val startOffset: Int, val length: Int)
}
