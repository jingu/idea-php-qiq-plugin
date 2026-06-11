package io.github.jingu.idea_qiq_plugin.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.github.jingu.idea_qiq_plugin.block.QiqSectionModel
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * A section name occurrence (a `setSection` definition or a `getSection`/
 * `hasSection` usage) and the file it is in. [head] is the directive name
 * (`setSection` / `getSection` / `hasSection`) used to label a navigation target.
 */
data class QiqSectionLocation(
    val file: VirtualFile,
    val name: String,
    val head: String,
    val nameRange: TextRange,
)

/**
 * A template-root-wide index of Qiq section name occurrences — both definitions
 * (`setSection`) and usages (`getSection` / `hasSection`) — backing name
 * completion, Go to Declaration (both directions), and the undefined-name
 * inspection.
 *
 * Qiq's layout direction is page -> layout (a page `setSection`s content and
 * declares its layout; the layout `getSection`s it back), so a name's definition
 * and usages live in different files and neither is on the other's own
 * `setLayout`/`extends` chain. Resolution is therefore scoped to all templates
 * under the detected roots ([QiqSettingsService.resolveTemplateRoots] /
 * [QiqSettingsService.resolveTemplateBase]) rather than a single chain.
 *
 * Cached per context file, invalidated on any PSI change
 * ([PsiModificationTracker.MODIFICATION_COUNT]); a file-based index is a possible
 * later optimisation (tracked with #23/#32).
 */
object QiqSectionIndex {

    private const val MAX_FILES = 2000
    private val log = Logger.getInstance(QiqSectionIndex::class.java)

    /**
     * Definitions and usages found under the roots discovered for [contextFile].
     * [truncated] is true when the [MAX_FILES] scan budget was exhausted, so the
     * index may be incomplete — callers that must not produce false negatives
     * (e.g. the undefined-name inspection) should back off when it is set.
     */
    data class Index(
        val definitions: List<QiqSectionLocation>,
        val usages: List<QiqSectionLocation>,
        val truncated: Boolean,
    )

    fun index(project: Project, contextFile: VirtualFile): Index {
        val psiFile = PsiManager.getInstance(project).findFile(contextFile) ?: return EMPTY
        return CachedValuesManager.getCachedValue(psiFile) {
            CachedValueProvider.Result.create(
                compute(project, contextFile),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }

    /** The defined section names visible from [contextFile]. */
    fun definedNames(project: Project, contextFile: VirtualFile): Set<String> =
        index(project, contextFile).definitions.asSequence().map { it.name }.toSet()

    /** Every `setSection` definition of [name] visible from [contextFile]. */
    fun definitionsByName(project: Project, contextFile: VirtualFile, name: String): List<QiqSectionLocation> =
        index(project, contextFile).definitions.filter { it.name == name }

    /** Every `getSection`/`hasSection` usage of [name] visible from [contextFile]. */
    fun usagesByName(project: Project, contextFile: VirtualFile, name: String): List<QiqSectionLocation> =
        index(project, contextFile).usages.filter { it.name == name }

    private val EMPTY = Index(emptyList(), emptyList(), truncated = false)

    private fun compute(project: Project, contextFile: VirtualFile): Index {
        val settings = QiqSettingsService.getInstance(project) ?: return EMPTY
        val exts = QiqTemplateResolver.candidateExtensions(project)
        val roots = LinkedHashSet<VirtualFile>()
        roots.addAll(settings.resolveTemplateRoots(contextFile))
        settings.resolveTemplateBase(contextFile)?.let { roots.add(it) }
        if (roots.isEmpty()) return EMPTY

        val definitions = ArrayList<QiqSectionLocation>()
        val usages = ArrayList<QiqSectionLocation>()
        val visited = HashSet<String>()
        var budget = MAX_FILES
        for (root in roots) {
            budget = collect(root, exts, visited, definitions, usages, budget)
            if (budget <= 0) break
        }
        // budget == 0 means the file cap was reached, so some templates may be
        // unscanned; mark the index incomplete.
        val truncated = budget <= 0
        if (truncated) {
            log.warn("Qiq section index hit the $MAX_FILES-file scan cap; results may be incomplete for ${contextFile.path}")
        }
        return Index(definitions, usages, truncated)
    }

    private fun collect(
        dir: VirtualFile,
        extensions: List<String>,
        visited: MutableSet<String>,
        definitions: MutableList<QiqSectionLocation>,
        usages: MutableList<QiqSectionLocation>,
        budget: Int,
    ): Int {
        var remaining = budget
        for (child in dir.children) {
            ProgressManager.checkCanceled()
            if (remaining <= 0) return remaining
            if (child.isDirectory) {
                remaining = collect(child, extensions, visited, definitions, usages, remaining)
            } else if (isTemplateFile(child, extensions) && visited.add(child.path)) {
                remaining--
                val text = readText(child) ?: continue
                for (def in QiqSectionModel.definitions(text)) {
                    definitions.add(QiqSectionLocation(child, def.name, "setSection", def.nameRange))
                }
                for (use in QiqSectionModel.usages(text)) {
                    usages.add(QiqSectionLocation(child, use.name, use.head, use.nameRange))
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
