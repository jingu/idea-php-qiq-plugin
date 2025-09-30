package io.github.jingu.idea_qiq_plugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

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

    fun findTemplateByPath(project: Project, path: String, contextFile: VirtualFile? = null): PsiElement? {
        val raw = path.trim()
        if (raw.isEmpty() || raw.contains(' ')) return null

        val normalized = raw.removePrefix("/").replace(Regex("/+"), "/")
        val settings = QiqSettingsService.getInstance(project)?.state
        val exts = settings?.candidateExtensions ?: listOf(".qiq.php", ".qiq", ".php")

        // 候補生成（既に拡張子が付いていればそれを優先）
        val candidates = buildCandidatePaths(normalized, exts)

        // コンテキストファイルを起点に対象ファイルを相対的に探索する
        contextFile?.let { ctxFile ->
            searchRelativeToContext(project, ctxFile, candidates)?.let { return it }
        }

        return null
    }

    private fun buildCandidatePaths(normalizedPath: String, extensions: List<String>): List<String> = buildList {
        add(normalizedPath)
        if (extensions.none { normalizedPath.endsWith(it) }) {
            extensions.forEach { add("$normalizedPath$it") }
        }
    }

    private fun searchRelativeToContext(
        project: Project,
        contextFile: VirtualFile,
        candidates: List<String>,
    ): PsiElement? {
        val psiManager = PsiManager.getInstance(project)
        var currentDir = if (contextFile.isDirectory) contextFile else contextFile.parent

        while (currentDir != null) {
            for (candidate in candidates) {
                currentDir.findFileByRelativePath(candidate)?.let { vf ->
                    psiManager.findFile(vf)?.let { psi ->
                        return psi
                    }
                }
            }
            currentDir = currentDir.parent
        }

        return null
    }
}

data class ReservedDirectiveSpan(val startOffset: Int, val length: Int) {
    fun toTextRange(baseOffset: Int): TextRange =
        TextRange(baseOffset + startOffset, baseOffset + startOffset + length)
}
