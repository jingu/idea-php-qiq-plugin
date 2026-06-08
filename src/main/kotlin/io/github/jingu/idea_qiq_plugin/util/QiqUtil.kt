package io.github.jingu.idea_qiq_plugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

object QiqUtil {
    private val reservedDirectiveNames = setOf("extends")

    fun findReservedDirectiveSpan(text: String, directives: Set<String> = reservedDirectiveNames): ReservedDirectiveSpan? {
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
        if (directives.none { it.equals(name, ignoreCase = true) }) return null

        var lookahead = index
        while (lookahead < length && text[lookahead].isWhitespace()) {
            lookahead++
        }

        if (lookahead >= length || text[lookahead] != '(') return null

        return ReservedDirectiveSpan(nameStart, index - nameStart)
    }

    fun isIncludePath(value: String): Boolean {
        val v = value.trim()

        // シンプルで柔軟な判定条件
        val result = v.isNotEmpty() &&
               v.length > 1 &&           // 最低限の長さ
               !v.contains(" ")          // スペースを含まない

        return result
    }

    /**
     * The template file a path argument resolves to, or null. A thin PSI adapter
     * over [QiqTemplateResolver.resolve] — the single resolver shared with
     * completion and the missing-template inspection — returning the first match.
     */
    fun findTemplateByPath(project: Project, path: String, contextFile: VirtualFile? = null): PsiElement? {
        val vf = QiqTemplateResolver.resolve(project, path, contextFile).firstOrNull() ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }
}

data class ReservedDirectiveSpan(val startOffset: Int, val length: Int) {
    fun toTextRange(baseOffset: Int): TextRange =
        TextRange(baseOffset + startOffset, baseOffset + startOffset + length)
}
