package io.github.jingu.idea_qiq_plugin.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.jingu.idea_qiq_plugin.util.QiqComposerVersionResolver
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Service(Service.Level.PROJECT)
@State(name = "QiqSettings", storages = [Storage("qiq_settings.xml")])
class QiqSettingsService(private val project: Project) : PersistentStateComponent<QiqSettingsService.State> {
    /**
     * State: only explicit roots as a last resort, content-driven discovery is default.
     */
    data class State(
        var explicitRoots: MutableList<String> = mutableListOf(),
        var candidateExtensions: MutableList<String> = mutableListOf(".qiq.php", ".qiq", ".php"),
        var maxAscendDepth: Int = 8,
        var maxFilesPerDir: Int = 400,
        var maxBytesPerFile: Int = 8192,
        // When true, QiqPhpInjector prepends `<?php declare(strict_types=1); ?>`
        // to each Qiq file's injected PHP so that scalar literal misuses in
        // escape directives (e.g. `{{h true }}`, `{{h 123 }}`) surface as
        // PhpStorm type warnings. Off by default to match Qiq's runtime
        // behaviour, which performs implicit scalar→string casts.
        var enableStrictTypes: Boolean = false
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    companion object {
        const val DEFAULT_MAJOR_VERSION = 3
        fun getInstance(project: Project) = project.getService(QiqSettingsService::class.java)
    }

    /** Whether strict_types should be injected into Qiq template PHP fragments. */
    fun isStrictTypesEnabled(): Boolean = state.enableStrictTypes

    fun setStrictTypesEnabled(enabled: Boolean) {
        state.enableStrictTypes = enabled
    }


    /**
     * Entry point for contributors (completion/navigation/etc.).
     */
    fun resolveTemplateRoots(contextFile: VirtualFile): List<VirtualFile> {
        val cached = getCached(contextFile)
        if (cached.isNotEmpty()) return cached

        val discovered = Resolver.discover(project, contextFile, state)
        val withFallback = if (discovered.isNotEmpty()) discovered else appendExplicitRoots(contextFile)
        cache(contextFile, withFallback)
        return withFallback
    }

    /**
     * Returns the qiq/qiq major version declared in composer.lock for the
     * content root that owns [contextFile]. Falls back to 3 when the lock
     * file is missing, the package is not present, or the version string is
     * unparseable (e.g. `dev-main`). The plugin uses this to decide which
     * QiqRuntimeFunctions* stub class to route escape directives through:
     *   - v1 → QiqRuntimeFunctionsStrict (string-only signatures)
     *   - v2+ → QiqRuntimeFunctions (null|scalar|\Stringable signatures)
     */
    fun resolveQiqMajorVersion(contextFile: VirtualFile): Int {
        val lockFile = findComposerLock(contextFile) ?: return DEFAULT_MAJOR_VERSION
        val cached = composerLockCache[lockFile.path]
        if (cached != null && cached.modificationStamp == lockFile.modificationStamp) {
            return cached.majorVersion
        }
        val text = runCatching { VfsUtilCore.loadText(lockFile) }.getOrNull()
            ?: return DEFAULT_MAJOR_VERSION
        val parsed = QiqComposerVersionResolver.parseMajorVersion(text) ?: DEFAULT_MAJOR_VERSION
        composerLockCache[lockFile.path] = ComposerLockEntry(lockFile.modificationStamp, parsed)
        return parsed
    }

    private fun findComposerLock(contextFile: VirtualFile): VirtualFile? {
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(contextFile)
            ?: return null
        return contentRoot.findChild("composer.lock")?.takeIf { it.isValid && !it.isDirectory }
    }

    private data class ComposerLockEntry(val modificationStamp: Long, val majorVersion: Int)
    // ConcurrentHashMap: resolveQiqMajorVersion may be invoked from parallel
    // ReadActions (e.g. concurrent PSI injection across files), so the cache
    // backing store must tolerate concurrent put/get without map corruption.
    private val composerLockCache = ConcurrentHashMap<String, ComposerLockEntry>()

    // ---- simple cache ----

    private data class CacheKey(val path: String)
    // Same rationale as composerLockCache: resolveTemplateRoots is called
    // from completion/navigation contributors running on parallel ReadActions.
    private val cacheMap = ConcurrentHashMap<CacheKey, List<VirtualFile>>()

    private fun getCached(contextFile: VirtualFile): List<VirtualFile> =
        cacheMap[CacheKey(contextFile.path)] ?: emptyList()

    private fun cache(contextFile: VirtualFile, value: List<VirtualFile>) {
        cacheMap[CacheKey(contextFile.path)] = value
    }

    private fun appendExplicitRoots(contextFile: VirtualFile): List<VirtualFile> {
        if (state.explicitRoots.isEmpty()) return emptyList()
        val fileIndex = ProjectFileIndex.getInstance(project)
        val contentRoot = fileIndex.getContentRootForFile(contextFile)
        val lfs = LocalFileSystem.getInstance()
        return state.explicitRoots.mapNotNull { raw ->
            val path = raw.trim()
            val vf = if (File(path).isAbsolute) lfs.findFileByPath(path)
            else contentRoot?.findFileByRelativePath(path.removePrefix("/"))
            vf?.takeIf { it.isDirectory && it.isValid }
        }
    }

    /**
     * Content-driven resolver. Does not rely on fixed directory names or config files.
     */
    private object Resolver : DumbAware {
        private val tokenPatterns = listOf("{{h", "{{=", "{{!", "{{", "}}")

        fun discover(project: Project, contextFile: VirtualFile, st: State): List<VirtualFile> {
            val fileIndex = ProjectFileIndex.getInstance(project)
            val contentRoot = fileIndex.getContentRootForFile(contextFile) ?: return emptyList()

            val candidates = linkedMapOf<VirtualFile, Double>()
            ascend(contextFile, st.maxAscendDepth, stopAt = contentRoot) { dir, depth ->
                val dirs = dir.children?.filter { it.isDirectory }?.takeIf { it.isNotEmpty() } ?: listOf(dir)
                for (d in dirs) {
                    val stats = quickScanDir(d, st)
                    if (stats.totalFiles == 0) continue
                    val score = score(stats) * Math.pow(0.9, depth.toDouble())
                    if (score > 0.0) candidates[d] = max(candidates[d] ?: 0.0, score)
                }
            }
            if (candidates.isEmpty()) return emptyList()
            val sorted = candidates.entries.sortedByDescending { it.value }
            val top = sorted.first().value
            return sorted.takeWhile { it.value >= top * 0.85 }.map { it.key }
        }

        private data class DirStats(
            val totalFiles: Int,
            val qiqExtFiles: Int,
            val qiqTokenFiles: Int,
            val tokenHits: Int,
            val breadth: Int,
            val layoutHints: Int
        )

        private fun quickScanDir(root: VirtualFile, st: State): DirStats {
            var total = 0
            var qiqExt = 0
            var qiqTokenFiles = 0
            var tokenHits = 0
            val subdirsWithQiq = HashSet<VirtualFile>()
            var layoutHints = 0

            fun visit(dir: VirtualFile) {
                if (total >= st.maxFilesPerDir) return
                for (c in dir.children.orEmpty()) {
                    if (total >= st.maxFilesPerDir) return
                    if (c.isDirectory) {
                        visit(c)
                    } else {
                        total++
                        val name = c.name.lowercase()
                        val hasQiqExt = st.candidateExtensions.any { name.endsWith(it) }
                        if (hasQiqExt) qiqExt++

                        val text = runCatching { VfsUtilCore.loadText(c) }.getOrNull()?.let {
                            if (it.length > st.maxBytesPerFile) it.substring(0, st.maxBytesPerFile) else it
                        } ?: continue

                        val hits = countQiqTokens(text)
                        if (hits > 0) {
                            qiqTokenFiles++
                            tokenHits += hits
                            subdirsWithQiq += dir
                        }

                        if (Regex("""(^|[./])(?:index|layout|base|default)(\.[\n\r\t \w-]+)?$""").containsMatchIn(name)) {
                            layoutHints++
                        }
                    }
                }
            }
            visit(root)
            return DirStats(
                totalFiles = total,
                qiqExtFiles = qiqExt,
                qiqTokenFiles = qiqTokenFiles,
                tokenHits = tokenHits,
                breadth = subdirsWithQiq.size,
                layoutHints = layoutHints
            )
        }

        private fun countQiqTokens(text: String): Int {
            var count = 0
            for (p in tokenPatterns) {
                var i = 0
                while (true) {
                    i = text.indexOf(p, i)
                    if (i < 0) break
                    count++; i += p.length
                }
            }
            return if (count >= 2) count else 0
        }

        private fun score(s: DirStats): Double {
            val extDensity = if (s.totalFiles == 0) 0.0 else s.qiqExtFiles.toDouble() / s.totalFiles
            val tokenDensity = if (s.totalFiles == 0) 0.0 else s.qiqTokenFiles.toDouble() / s.totalFiles
            val wExt = 0.45
            val wTok = 0.45
            val wBreadth = 0.06
            val wHint = 0.04
            // bounded alternatives to tanh
            val breadthScore = s.breadth.toDouble() / (s.breadth + 5.0)
            val hintScore = s.layoutHints.toDouble() / (s.layoutHints + 5.0)
            return wExt * extDensity +
                    wTok * tokenDensity +
                    wBreadth * breadthScore +
                    wHint * hintScore
        }

        private inline fun ascend(start: VirtualFile, maxDepth: Int, stopAt: VirtualFile, block: (VirtualFile, Int) -> Unit) {
            var cur: VirtualFile? = if (start.isDirectory) start else start.parent
            var depth = 0
            while (cur != null && depth <= maxDepth) {
                block(cur, depth)
                if (cur == stopAt) break
                cur = cur.parent; depth++
            }
        }
    }
}
