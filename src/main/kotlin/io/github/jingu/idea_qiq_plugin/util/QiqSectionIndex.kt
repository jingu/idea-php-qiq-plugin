package io.github.jingu.idea_qiq_plugin.util

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.github.jingu.idea_qiq_plugin.block.QiqBlockType
import io.github.jingu.idea_qiq_plugin.block.QiqSectionDef
import io.github.jingu.idea_qiq_plugin.block.QiqSectionModel
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/** A section/block definition together with the template file it was found in. */
data class QiqSectionLocation(val file: VirtualFile, val def: QiqSectionDef)

/**
 * A template-root-wide index of Qiq section/block name definitions
 * (`setSection`/`setBlock`), backing name completion, Go to Declaration, and the
 * undefined-name inspection for `getSection`/`getBlock`.
 *
 * Qiq's layout direction is page -> layout: a page `setSection('x')`s content and
 * declares its layout, while the layout `getSection('x')`s it back. The matching
 * definition therefore lives *downstream* of a `getSection` in the layout, not on
 * the file's own `setLayout`/`extends` chain — so resolution is scoped to all
 * templates under the detected roots ([QiqSettingsService.resolveTemplateRoots] /
 * [QiqSettingsService.resolveTemplateBase]) rather than a single chain.
 *
 * The scan is cached per context file and invalidated on any PSI change
 * ([PsiModificationTracker.MODIFICATION_COUNT]); a file-based index is a possible
 * later optimisation (tracked with #23/#32).
 */
object QiqSectionIndex {

    private const val MAX_FILES = 2000

    /** Every section/block definition under the roots discovered for [contextFile]. */
    fun allDefinitions(project: Project, contextFile: VirtualFile): List<QiqSectionLocation> {
        val psiFile = PsiManager.getInstance(project).findFile(contextFile) ?: return emptyList()
        return CachedValuesManager.getCachedValue(psiFile) {
            CachedValueProvider.Result.create(
                compute(project, contextFile),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }

    /** The defined names of [type] visible from [contextFile]. */
    fun definedNames(project: Project, contextFile: VirtualFile, type: QiqBlockType): Set<String> =
        allDefinitions(project, contextFile).asSequence()
            .filter { it.def.type == type }
            .map { it.def.name }
            .toSet()

    /** Every definition of [name] with the given [type] visible from [contextFile]. */
    fun definitionsByName(
        project: Project,
        contextFile: VirtualFile,
        name: String,
        type: QiqBlockType,
    ): List<QiqSectionLocation> =
        allDefinitions(project, contextFile).filter { it.def.type == type && it.def.name == name }

    private fun compute(project: Project, contextFile: VirtualFile): List<QiqSectionLocation> {
        val settings = QiqSettingsService.getInstance(project) ?: return emptyList()
        val exts = QiqTemplateResolver.candidateExtensions(project)
        val roots = LinkedHashSet<VirtualFile>()
        roots.addAll(settings.resolveTemplateRoots(contextFile))
        settings.resolveTemplateBase(contextFile)?.let { roots.add(it) }
        if (roots.isEmpty()) return emptyList()

        val out = ArrayList<QiqSectionLocation>()
        val visited = HashSet<String>()
        var budget = MAX_FILES
        for (root in roots) {
            budget = collect(project, root, exts, visited, out, budget)
            if (budget <= 0) break
        }
        return out
    }

    private fun collect(
        project: Project,
        dir: VirtualFile,
        extensions: List<String>,
        visited: MutableSet<String>,
        out: MutableList<QiqSectionLocation>,
        budget: Int,
    ): Int {
        var remaining = budget
        for (child in dir.children) {
            ProgressManager.checkCanceled()
            if (remaining <= 0) return remaining
            if (child.isDirectory) {
                remaining = collect(project, child, extensions, visited, out, remaining)
            } else if (isTemplateFile(child, extensions) && visited.add(child.path)) {
                remaining--
                val text = readText(child) ?: continue
                for (def in QiqSectionModel.definitions(text)) {
                    out.add(QiqSectionLocation(child, def))
                }
            }
        }
        return remaining
    }

    private fun isTemplateFile(file: VirtualFile, extensions: List<String>): Boolean =
        extensions.any { file.name.endsWith(it, ignoreCase = true) }

    /** Unsaved editor content when the file is open, else the on-disk text. */
    private fun readText(file: VirtualFile): CharSequence? {
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.charsSequence }
        return runCatching { LoadTextUtil.loadText(file) }.getOrNull()
    }
}
