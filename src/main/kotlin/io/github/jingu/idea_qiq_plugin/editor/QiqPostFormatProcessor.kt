package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

/**
 * Applies Qiq's block-aware reindent during Reformat Code.
 *
 * Qiq's flat PSI and HTML/PHP interleaving make the block-based formatter
 * unworkable (see [QiqFormattingModelBuilder]), so the indent is applied textually
 * here instead: [QiqReindent] computes the combined HTML-tag + Qiq-block indent for
 * every line, and this rewrites each line's leading whitespace to match. Only
 * leading whitespace changes — line content, and the interior of `<?php … ?>` /
 * multi-line `{{ … }}` regions, are preserved — so it is conservative and idempotent.
 */
class QiqPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement = source

    override fun processText(
        source: PsiFile,
        rangeToReformat: TextRange,
        settings: CodeStyleSettings,
    ): TextRange {
        // Only the Qiq base view drives the reindent; the HTML view of the same
        // file must not run it a second time.
        if (source.language !== QiqTemplateLanguage) return rangeToReformat
        val document = source.viewProvider.document ?: return rangeToReformat

        val text = document.immutableCharSequence
        val indentSize = settings.getIndentSize(QiqFileType).coerceAtLeast(1)
        val indents = QiqReindent.computeLineIndents(text, indentSize)

        var endDelta = 0
        // Bottom-up so each edit leaves the offsets of earlier lines unchanged.
        for (line in document.lineCount - 1 downTo 0) {
            if (line >= indents.size) continue
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            if (lineStart > rangeToReformat.endOffset || lineEnd < rangeToReformat.startOffset) continue

            var contentStart = lineStart
            while (contentStart < lineEnd && (text[contentStart] == ' ' || text[contentStart] == '\t')) contentStart++
            if (contentStart == lineEnd) continue // blank line: leave it alone

            val current = text.subSequence(lineStart, contentStart).toString()
            val desired = " ".repeat(indents[line])
            if (current != desired) {
                document.replaceString(lineStart, contentStart, desired)
                if (lineStart < rangeToReformat.endOffset) endDelta += desired.length - current.length
            }
        }

        val newEnd = (rangeToReformat.endOffset + endDelta).coerceIn(rangeToReformat.startOffset, document.textLength)
        return TextRange(rangeToReformat.startOffset, newEnd)
    }
}
