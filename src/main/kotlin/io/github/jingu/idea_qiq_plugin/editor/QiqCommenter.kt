package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.codeInsight.generation.CommenterWithLineSuffix
import com.intellij.codeInsight.generation.SelfManagingCommenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class QiqCommenter : CommenterWithLineSuffix, SelfManagingCommenter<CommenterDataHolder> {

    override fun getLineCommentPrefix(): String = "{{ // "

    override fun getLineCommentSuffix(): String = " }}"

    override fun getBlockCommentPrefix(): String? = "{{ /* "

    override fun getBlockCommentSuffix(): String? = " */ }}"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null

    override fun createLineCommentingState(
        start: Int,
        end: Int,
        document: Document,
        file: PsiFile
    ): CommenterDataHolder = SelfManagingCommenter.EMPTY_STATE

    override fun createBlockCommentingState(
        start: Int,
        end: Int,
        document: Document,
        file: PsiFile
    ): CommenterDataHolder = SelfManagingCommenter.EMPTY_STATE

    override fun commentLine(line: Int, offset: Int, document: Document, state: CommenterDataHolder) {
        val range = lineRange(line, document) ?: return
        val original = document.getText(range)
        if (original.isBlank() || isAlreadyCommented(original)) {
            return
        }

        val (indent, content, trailing) = splitLine(original)
        if (content.isEmpty()) {
            return
        }

        val updated = when {
            shouldUseHtmlComment(content) -> indent + formatAsHtmlComment(content) + trailing
            else -> indent + formatAsQiqComment(content) + trailing
        }

        document.replaceString(range.startOffset, range.endOffset, updated)
    }

    override fun uncommentLine(line: Int, offset: Int, document: Document, state: CommenterDataHolder) {
        val range = lineRange(line, document) ?: return
        val current = document.getText(range)
        if (current.isBlank()) {
            return
        }

        val (indent, content, trailing) = splitLine(current)
        if (content.isEmpty()) {
            return
        }

        val restored = when {
            content.startsWith("<!-- ") && content.endsWith(" -->") ->
                indent + restoreFromHtmlComment(content) + trailing
            content.startsWith("{{ // ") && content.endsWith(" }}") ->
                indent + content.removePrefix("{{ // ").removeSuffix(" }}") + trailing
            else -> return
        }

        document.replaceString(range.startOffset, range.endOffset, restored)
    }

    override fun isLineCommented(line: Int, offset: Int, document: Document, state: CommenterDataHolder): Boolean {
        val range = lineRange(line, document) ?: return false
        val text = document.getText(range)
        if (text.isBlank()) {
            return false
        }
        val (_, content, _) = splitLine(text)
        if (content.isEmpty()) {
            return false
        }
        return isAlreadyCommented(content)
    }

    override fun getCommentPrefix(line: Int, document: Document, data: CommenterDataHolder): String? = null

    override fun getBlockCommentPrefix(
        startLine: Int,
        document: Document,
        data: CommenterDataHolder
    ): String? = getBlockCommentPrefix()

    override fun getBlockCommentSuffix(
        endLine: Int,
        document: Document,
        data: CommenterDataHolder
    ): String? = getBlockCommentSuffix()

    override fun getBlockCommentRange(
        start: Int,
        end: Int,
        document: Document,
        data: CommenterDataHolder
    ): TextRange? = null

    override fun uncommentBlockComment(
        start: Int,
        end: Int,
        document: Document,
        data: CommenterDataHolder
    ) {
        // Fallback to default behaviour via prefixes/suffixes
    }

    override fun insertBlockComment(
        start: Int,
        end: Int,
        document: Document,
        data: CommenterDataHolder
    ): TextRange? = null

    private fun lineRange(line: Int, document: Document): TextRange? {
        if (line < 0 || line >= document.lineCount) return null
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        return TextRange.create(start, end)
    }

    private fun splitLine(line: String): Triple<String, String, String> {
        val length = line.length
        var startIndex = 0
        while (startIndex < length && line[startIndex].isWhitespace()) {
            startIndex++
        }
        if (startIndex == length) {
            return Triple(line, "", "")
        }
        var endIndex = length
        while (endIndex > startIndex && line[endIndex - 1].isWhitespace()) {
            endIndex--
        }
        val indent = line.substring(0, startIndex)
        val content = line.substring(startIndex, endIndex)
        val trailing = line.substring(endIndex)
        return Triple(indent, content, trailing)
    }

    private fun shouldUseHtmlComment(content: String): Boolean {
        if (!content.contains("{{")) return false
        return HTML_TAG_REGEX.containsMatchIn(content)
    }

    private fun formatAsHtmlComment(content: String): String {
        val commentedExpressions = commentQiqExpressions(content)
        return "<!-- $commentedExpressions -->"
    }

    private fun formatAsQiqComment(content: String): String = "{{ // $content }}"

    private fun restoreFromHtmlComment(content: String): String {
        val inner = content.removePrefix("<!-- ").removeSuffix(" -->")
        return restoreQiqExpressions(inner)
    }

    private fun commentQiqExpressions(content: String): String =
        if (content.contains("{{")) COMMENT_INSERT_REGEX.replace(content) { "{{//" } else content

    private fun restoreQiqExpressions(content: String): String =
        if (content.contains("{{//")) COMMENT_REMOVE_REGEX.replace(content, "{{") else content

    private fun isAlreadyCommented(line: String): Boolean {
        val trimmed = line.trim()
        return (trimmed.startsWith("<!-- ") && trimmed.endsWith(" -->")) ||
            (trimmed.startsWith("{{ // ") && trimmed.endsWith(" }}"))
    }

    companion object {
        private val HTML_TAG_REGEX = Regex("<[/a-zA-Z][^>]*>")
        private val COMMENT_INSERT_REGEX = Regex("\\{\\{(?!\\s*//)")
        private val COMMENT_REMOVE_REGEX = Regex("\\{\\{//")
    }
}
