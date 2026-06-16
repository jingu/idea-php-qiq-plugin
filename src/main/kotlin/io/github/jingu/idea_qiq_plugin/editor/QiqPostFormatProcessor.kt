package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

/**
 * Applies Qiq's block-aware reindent during Reformat Code.
 *
 * Qiq's flat PSI and HTML/PHP interleaving make a block-based formatting model
 * unworkable, so the indent is applied textually here instead: [QiqReindent]
 * computes the combined HTML-tag + Qiq-block indent for every line, and this
 * rewrites each line's leading whitespace to match, materialising it as tabs or
 * spaces according to the code style's "Use tab character" option. Only leading
 * whitespace changes; line content is untouched, whitespace-only lines are stripped
 * to empty, and lines that [QiqReindent] marks [QiqReindent.LEAVE_AS_IS] (the
 * interior of `<?php … ?>` islands and `<!-- … -->` comments) are skipped entirely
 * so their original indentation — tabs included — is preserved verbatim. The pass is
 * conservative and idempotent.
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
        val indentOptions = settings.getIndentOptions(QiqFileType)
        val indentSize = indentOptions.INDENT_SIZE.coerceAtLeast(1)
        val indents = QiqReindent.computeLineIndents(text, indentSize)

        // Let a RangeMarker track the reformat range across edits: it stays accurate
        // even when a boundary falls inside a rewritten leading-whitespace run, which
        // manual offset bookkeeping cannot get right.
        val marker = document.createRangeMarker(
            rangeToReformat.startOffset.coerceIn(0, document.textLength),
            rangeToReformat.endOffset.coerceIn(0, document.textLength),
        )
        try {
            // Bottom-up so each edit leaves the offsets of earlier lines unchanged.
            for (line in document.lineCount - 1 downTo 0) {
                if (line >= indents.size) continue
                val target = indents[line]
                if (target == QiqReindent.LEAVE_AS_IS) continue // preserved verbatim
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                // TextRange is half-open [start, end): skip lines that do not overlap it.
                if (lineStart >= rangeToReformat.endOffset || lineEnd <= rangeToReformat.startOffset) continue

                var contentStart = lineStart
                while (contentStart < lineEnd && (text[contentStart] == ' ' || text[contentStart] == '\t')) contentStart++
                // contentStart == lineEnd means a whitespace-only line: target is 0,
                // so the replacement empties it (stray indentation is stripped).

                val current = text.subSequence(lineStart, contentStart).toString()
                val desired = buildIndent(indentOptions, target)
                if (current != desired) {
                    document.replaceString(lineStart, contentStart, desired)
                }
            }
            return TextRange(marker.startOffset, marker.endOffset)
        } finally {
            marker.dispose()
        }
    }

    /**
     * Render an indent of [width] columns as tabs and/or spaces per the code style.
     * With "Use tab character" on, full tab stops become tabs and the remainder
     * spaces; otherwise all spaces.
     */
    private fun buildIndent(options: CommonCodeStyleSettings.IndentOptions, width: Int): String {
        if (width <= 0) return ""
        if (!options.USE_TAB_CHARACTER) return " ".repeat(width)
        val tabSize = options.TAB_SIZE.coerceAtLeast(1)
        return "\t".repeat(width / tabSize) + " ".repeat(width % tabSize)
    }
}
