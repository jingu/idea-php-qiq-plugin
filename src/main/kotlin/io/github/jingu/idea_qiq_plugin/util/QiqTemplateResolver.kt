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

    /** A normalized template path: whether it was root-absolute, and the relative remainder. */
    data class NormalizedPath(val rootAbsolute: Boolean, val relative: String)

    /** The candidate template extensions from settings, normalized, with a built-in fallback. */
    fun candidateExtensions(project: Project): List<String> =
        normalizeExtensions(QiqSettingsService.getInstance(project)?.state?.candidateExtensions)

    /**
     * Normalize a configured extension list: drop blanks, ensure each starts with
     * a `.` (since [buildCandidatePaths] appends them verbatim), and fall back to
     * the built-in defaults when the list is null *or* empty — an empty list would
     * otherwise silently disable both completion listing and path resolution.
     */
    fun normalizeExtensions(configured: List<String>?): List<String> =
        configured
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.map { if (it.startsWith(".")) it else ".$it" }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_EXTENSIONS

    /**
     * Normalize a raw template path argument, or null when it is empty or dynamic.
     * A path is dynamic — and so not statically resolvable — when it embeds any
     * whitespace (including leading/trailing, which is significant to Qiq at
     * runtime, so it is *not* trimmed away) or a `$` (PHP interpolation). This is
     * the single static-path gate every resolver consumer shares: navigation and
     * [resolve] via this method, and [QiqTemplatePathInspection] which skips a
     * path exactly when this returns null — so an interpolated `"/layout/$name"`
     * is never resolved and never warned about. Collapses runs of `/` *before*
     * stripping the leading one, so multiple leading slashes (`///layout`) still
     * yield a clean relative path (`layout`) rather than leaving a stray leading
     * slash that would break
     * [com.intellij.openapi.vfs.VirtualFile.findFileByRelativePath].
     */
    fun normalizePath(path: String): NormalizedPath? {
        if (path.isEmpty() || path.any { it.isWhitespace() } || path.contains('$')) return null
        val collapsed = path.replace(MULTI_SLASH, "/")
        val rootAbsolute = collapsed.startsWith("/")
        val relative = collapsed.removePrefix("/")
        if (relative.isEmpty()) return null
        return NormalizedPath(rootAbsolute, relative)
    }

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
        if (contextFile == null) return emptyList()
        val normalized = normalizePath(path) ?: return emptyList()

        val exts = candidateExtensions(project)
        val candidates = buildCandidatePaths(normalized.relative, exts)
        val settings = QiqSettingsService.getInstance(project)
        val out = LinkedHashSet<VirtualFile>()

        if (normalized.rootAbsolute) {
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
     *
     * [MAX_CANDIDATES] is applied per source (each root, and the base) rather than
     * to the merged result, so a very large root cannot exhaust the budget and
     * drop the base-rooted `/...` suggestions (or another root) entirely.
     */
    fun listTemplatePaths(project: Project, contextFile: VirtualFile): List<String> {
        val settings = QiqSettingsService.getInstance(project) ?: return emptyList()
        val exts = candidateExtensions(project)
        val out = LinkedHashSet<String>()

        for (root in settings.resolveTemplateRoots(contextFile)) {
            val rel = LinkedHashSet<String>()
            collectTemplatePaths(root, root, exts, rel) { it }
            out.addAll(rel)
        }
        settings.resolveTemplateBase(contextFile)?.let { base ->
            val abs = LinkedHashSet<String>()
            collectTemplatePaths(base, base, exts, abs) { "/$it" }
            out.addAll(abs)
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
