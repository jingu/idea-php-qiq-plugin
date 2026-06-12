package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost
import io.github.jingu.idea_qiq_plugin.psi.QiqPhpHost
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService
import io.github.jingu.idea_qiq_plugin.util.QiqTemplateResolver

/**
 * Finds every `setLayout`/`render`/`extends`/`include('...')` (and helper path
 * argument) that resolves to a given Qiq template file, powering both Find Usages
 * and Rename of the file.
 *
 * These references live in *injected* PHP, which the platform's word-index-driven
 * reference search does not reliably reach (the Qiq host file is not word-indexed,
 * mirroring why section navigation uses a custom index rather than references). So
 * this executor scans the template roots discovered for the target — the same walk
 * [io.github.jingu.idea_qiq_plugin.util.QiqSectionIndex] uses — enumerates each
 * candidate file's injected string literals, and reports the [QiqIncludeReference]s
 * whose [QiqIncludeReference.isReferenceTo] matches the target. A cheap text
 * pre-filter on the file's base name keeps it from parsing unrelated templates.
 */
class QiqTemplateReferenceSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val target = params.elementToSearch as? PsiFileSystemItem ?: return
        val targetFile = target.virtualFile ?: return
        val project = target.project
        if (!QiqTemplateResolver.isTemplateTarget(project, targetFile)) return

        val extensions = QiqTemplateResolver.candidateExtensions(project)
        val baseName = (QiqTemplateResolver.stripTemplateExtension(targetFile.name, extensions) ?: targetFile.name)
            .takeIf { it.isNotEmpty() } ?: return

        val settings = QiqSettingsService.getInstance(project) ?: return
        val roots = LinkedHashSet<VirtualFile>()
        roots.addAll(settings.resolveTemplateRoots(targetFile))
        settings.resolveTemplateBase(targetFile)?.let { roots.add(it) }
        if (roots.isEmpty()) return

        val scope = params.effectiveSearchScope
        val psiManager = PsiManager.getInstance(project)
        val ilm = InjectedLanguageManager.getInstance(project)
        val visited = HashSet<String>()
        val stopped = booleanArrayOf(false)
        var budget = MAX_FILES
        for (root in roots) {
            budget = scan(root, baseName, target, scope, psiManager, ilm, visited, consumer, stopped, budget)
            if (budget <= 0 || stopped[0]) break
        }
    }

    private fun scan(
        dir: VirtualFile,
        baseName: String,
        target: PsiFileSystemItem,
        scope: SearchScope,
        psiManager: PsiManager,
        ilm: InjectedLanguageManager,
        visited: MutableSet<String>,
        consumer: Processor<in PsiReference>,
        stopped: BooleanArray,
        budget: Int,
    ): Int {
        var remaining = budget
        for (child in dir.children) {
            ProgressManager.checkCanceled()
            if (remaining <= 0 || stopped[0]) return remaining
            if (child.isDirectory) {
                remaining = scan(child, baseName, target, scope, psiManager, ilm, visited, consumer, stopped, remaining)
            } else if (QiqInjectionSupport.isQiqTemplateFile(child) && visited.add(child.path)) {
                if (!scope.contains(child)) continue
                remaining--
                // Only parse files whose text mentions the renamed base name; a
                // template that never names it cannot reference the target.
                val text = readText(child) ?: continue
                if (!text.contains(baseName)) continue
                val psiFile = psiManager.findFile(child) ?: continue
                processReferences(psiFile, target, ilm, consumer, stopped)
            }
        }
        return remaining
    }

    private fun processReferences(
        psiFile: com.intellij.psi.PsiFile,
        target: PsiFileSystemItem,
        ilm: InjectedLanguageManager,
        consumer: Processor<in PsiReference>,
        stopped: BooleanArray,
    ) {
        val hosts = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java) +
            PsiTreeUtil.collectElementsOfType(psiFile, QiqPhpHost::class.java)
        for (host in hosts) {
            for (pair in ilm.getInjectedPsiFiles(host).orEmpty()) {
                val literals = PsiTreeUtil.collectElementsOfType(pair.first, StringLiteralExpression::class.java)
                for (literal in literals) {
                    for (reference in literal.references) {
                        if (reference is QiqIncludeReference && reference.isReferenceTo(target)) {
                            if (!consumer.process(reference)) {
                                stopped[0] = true
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    /** Unsaved editor content when the file is open, else the on-disk text. */
    private fun readText(file: VirtualFile): CharSequence? {
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.charsSequence }
        return runCatching { VfsUtilCore.loadText(file) }.getOrNull()
    }

    private companion object {
        private const val MAX_FILES = 2000
    }
}
