package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService
import java.util.LinkedHashSet

class QiqPathReference(
    element: StringLiteralExpression
) : PsiPolyVariantReferenceBase<StringLiteralExpression>(
    element,
    // クォート内の範囲だけを下線化
    TextRange(1, element.textLength - 1),
    true
) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = myElement.project
        val path = (myElement as StringLiteralExpression).contents
        if (path.isBlank()) return ResolveResult.EMPTY_ARRAY

        val contextVFile = myElement.containingFile.virtualFile ?: return ResolveResult.EMPTY_ARRAY
        val vfs = resolveAllViaSettings(project, contextVFile, path)
        val psi = vfs.mapNotNull { PsiManager.getInstance(project).findFile(it) }
        return psi.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    // 単一候補なら resolve() でそのまま
    override fun resolve(): PsiElement? =
        multiResolve(false).singleOrNull()?.element

    override fun getVariants(): Array<Any> {
        // ここで拡張子を付けた実体を tailText に表示（挿入は拡張子なしを推奨なら調整）
        val project = myElement.project
        val input = (myElement as StringLiteralExpression).contents
        val base = input.substringAfterLast('/')
        val prefix = input.removeSuffix(base)

        val contextVFile = myElement.containingFile.virtualFile ?: return emptyArray()
        val vfs = resolveAllViaSettings(project, contextVFile, input)
        return vfs.map { vf ->
            val name = vf.name // 例: base.qiq.php
            LookupElementBuilder.create(prefix + base) // 挿入は拡張子なし
                .withPresentableText(base)
                .withTailText("  ($name)", true)
                .withTypeText(vf.parent?.name ?: "", true)
        }.toTypedArray()
    }

    // リネーム対応（任意）
    override fun handleElementRename(newName: String): PsiElement {
        // 省略：必要なら拡張子を外す/付けるポリシーをここで
        return super.handleElementRename(newName)
    }

    private fun resolveAllViaSettings(
        project: com.intellij.openapi.project.Project,
        contextFile: com.intellij.openapi.vfs.VirtualFile,
        input: String
    ): List<com.intellij.openapi.vfs.VirtualFile> {
        val service = QiqSettingsService.getInstance(project)
        val state = service.state
        val roots = service.resolveTemplateRoots(contextFile)
        if (roots.isEmpty()) return emptyList()

        val rel = input.trimStart('/')
        val hasKnownExt = state.candidateExtensions.any { rel.endsWith(it) }

        // build candidate relative paths: as-is, and if no known ext then with each ext appended
        val candidates = mutableListOf(rel)
        if (!hasKnownExt) {
            for (ext in state.candidateExtensions) {
                val dotExt = if (ext.startsWith('.')) ext else ".$ext"
                candidates += rel + dotExt
            }
        }

        val results = LinkedHashSet<com.intellij.openapi.vfs.VirtualFile>()
        for (root in roots) {
            for (cand in candidates) {
                root.findFileByRelativePath(cand)?.let { results += it }
            }
        }
        return results.toList()
    }
}
