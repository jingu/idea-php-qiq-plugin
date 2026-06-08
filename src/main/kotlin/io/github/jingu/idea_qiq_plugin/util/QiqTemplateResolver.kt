package io.github.jingu.idea_qiq_plugin.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * The single source of truth for Qiq template-path resolution, shared by
 * completion, Go to Declaration, and the missing-template inspection.
 *
 * Both the *listing* a completion offers ([listTemplatePaths]) and the
 * *resolution* navigation/inspection perform ([resolve]) are derived from the
 * same inputs — [QiqSettingsService.resolveTemplateRoots] (relative paths),
 * [QiqSettingsService.resolveTemplateBase] (root-absolute `/...` paths), and the
 * configured candidate extensions. Because generation and resolution use the
 * same roots/base/extensions, every path completion offers is guaranteed to
 * resolve, so the three features can no longer disagree (the divergence #22
 * fixes: completion offering a `/layout/base` that the old ancestor-only
 * resolver could not find, producing a false missing-template warning).
 */
object QiqTemplateResolver {

    private const val MAX_CANDIDATES = 2000
    private val MULTI_SLASH = Regex("/+")
    private val DEFAULT_EXTENSIONS = listOf(".qiq.php", ".qiq", ".php")

    /** The candidate template extensions from settings, with a built-in fallback. */
    fun candidateExtensions(project: Project): List<String> =
        QiqSettingsService.getInstance(project)?.state?.candidateExtensions ?: DEFAULT_EXTENSIONS

    /**
     * Every file a template path argument [path] resolves to, in priority order
     * and de-duplicated. Empty when [path] is blank/dynamic or nothing matches.
     *
     * A root-absolute path (leading `/`) is resolved against the template base;
     * a relative path is resolved against each detected template root (so it
     * matches what completion offers) and then via the ancestor walk from
     * [contextFile] (Qiq's runtime relative resolution, and the historical
     * Go to Declaration behaviour). Resolving to the union keeps every prior
     * navigation result working while closing the completion/resolution gap.
     */
    fun resolve(project: Project, path: String, contextFile: VirtualFile?): List<VirtualFile> {
        val raw = path.trim()
        if (raw.isEmpty() || raw.contains(' ')) return emptyList()
        if (contextFile == null) return emptyList()

        val rootAbsolute = raw.startsWith("/")
        val normalized = raw.removePrefix("/").replace(MULTI_SLASH, "/")
        if (normalized.isEmpty()) return emptyList()

        val exts = candidateExtensions(project)
        val candidates = buildCandidatePaths(normalized, exts)
        val settings = QiqSettingsService.getInstance(project)
        val out = LinkedHashSet<VirtualFile>()

        if (rootAbsolute) {
            // Root-absolute paths resolve from the engine's template directory.
            settings?.resolveTemplateBase(contextFile)?.let { base ->
                addMatchesUnder(base, candidates, out)
            }
        } else {
            // Detected roots first — these are what completion lists relative paths
            // against — then the ancestor walk for context-relative resolution.
            settings?.resolveTemplateRoots(contextFile)?.forEach { root ->
                addMatchesUnder(root, candidates, out)
            }
            addAncestorWalkMatches(contextFile, candidates, out)
        }
        return out.toList()
    }

    /**
     * The template paths to offer at a path-argument completion for [contextFile]:
     * relative paths (no leading slash) for each detected root, plus root-absolute
     * `/...` paths for the template base, each with its Qiq extension stripped.
     */
    fun listTemplatePaths(project: Project, contextFile: VirtualFile): List<String> {
        val settings = QiqSettingsService.getInstance(project) ?: return emptyList()
        val exts = candidateExtensions(project)
        val out = LinkedHashSet<String>()

        for (root in settings.resolveTemplateRoots(contextFile)) {
            collectTemplatePaths(root, root, exts, out) { it }
        }
        settings.resolveTemplateBase(contextFile)?.let { base ->
            collectTemplatePaths(base, base, exts, out) { "/$it" }
        }
        return out.toList()
    }

    /** Path-as-is (extension already present) or path + each candidate extension. */
    fun buildCandidatePaths(normalizedPath: String, extensions: List<String>): List<String> = buildList {
        add(normalizedPath)
        if (extensions.none { normalizedPath.endsWith(it, ignoreCase = true) }) {
            extensions.forEach { add("$normalizedPath$it") }
        }
    }

    /**
     * Strip the first matching [extensions] entry from [relativePath], or return
     * null when the file is not a template. [extensions] is checked in order, so
     * list longer suffixes first (e.g. `.qiq.php` before `.php`) to avoid leaving
     * a dangling `.qiq`. Pure helper, unit-tested independently of the VFS walk.
     */
    fun stripTemplateExtension(relativePath: String, extensions: List<String>): String? {
        for (ext in extensions) {
            if (relativePath.endsWith(ext, ignoreCase = true)) {
                return relativePath.dropLast(ext.length)
            }
        }
        return null
    }

    private fun addMatchesUnder(root: VirtualFile, candidates: List<String>, out: MutableSet<VirtualFile>) {
        for (candidate in candidates) {
            val vf = root.findFileByRelativePath(candidate) ?: continue
            if (!vf.isDirectory) out.add(vf)
        }
    }

    private fun addAncestorWalkMatches(
        contextFile: VirtualFile,
        candidates: List<String>,
        out: MutableSet<VirtualFile>,
    ) {
        var dir = if (contextFile.isDirectory) contextFile else contextFile.parent
        while (dir != null) {
            for (candidate in candidates) {
                val vf = dir.findFileByRelativePath(candidate) ?: continue
                if (!vf.isDirectory) out.add(vf)
            }
            dir = dir.parent
        }
    }

    private fun collectTemplatePaths(
        dir: VirtualFile,
        root: VirtualFile,
        extensions: List<String>,
        out: MutableSet<String>,
        format: (String) -> String,
    ) {
        if (out.size >= MAX_CANDIDATES) return
        for (child in dir.children) {
            ProgressManager.checkCanceled()
            if (out.size >= MAX_CANDIDATES) return
            if (child.isDirectory) {
                collectTemplatePaths(child, root, extensions, out, format)
            } else {
                val relative = VfsUtilCore.getRelativePath(child, root) ?: continue
                stripTemplateExtension(relative, extensions)?.let { out.add(format(it)) }
            }
        }
    }
}
