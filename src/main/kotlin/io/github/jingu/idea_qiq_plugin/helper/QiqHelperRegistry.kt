package io.github.jingu.idea_qiq_plugin.helper

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-scoped registry that maps a Qiq helper name (the string used in
 * templates such as `{{ myHelper(...) }}` / `{{ $this->myHelper(...) }}`) to
 * the PHP class that the corresponding `HelperLocator::set()` factory
 * returns.
 *
 * The mapping is built by statically inspecting one or more "bootstrap"
 * files that the user nominates in Settings. Inside each file we scan for
 * method calls of the form
 *
 * ```php
 * $locator->set('name', static function () use (...): ClassName {
 *     return new ClassName(...);
 * });
 * // or
 * $locator->set('name', static fn (): ClassName => new ClassName(...));
 * ```
 *
 * Resolution priority for the returned class is:
 *  1. The closure's declared return type (`function (): ClassName { ... }`)
 *  2. A `return new ClassName(...)` statement in the closure body
 *  3. For arrow functions, the body expression if it is `new ClassName(...)`
 *
 * Any closure that does none of the above is ignored — those entries cannot
 * be statically attributed and the user can supply an explicit override in
 * the future if that turns out to be needed.
 *
 * Cache invalidation is driven by the modification stamp of every
 * bootstrap file plus the configured set of bootstrap paths.
 */
@Service(Service.Level.PROJECT)
class QiqHelperRegistry(private val project: Project) {

    private data class CacheKey(val stamps: Map<String, Long>)
    private data class CacheValue(val nameToFqn: Map<String, String>)

    // ConcurrentHashMap because resolve() may be called from parallel
    // ReadActions (multiple PsiReference resolutions across files).
    private val cache = ConcurrentHashMap<CacheKey, CacheValue>()

    /**
     * Returns every helper name currently registered across all bootstrap
     * files. Useful for completion or diagnostics; not exercised by the
     * navigation path itself.
     */
    fun allHelperNames(): Set<String> = computeMap().keys

    /** Returns the FQN registered for [name], or null when unknown. */
    fun resolveFqn(name: String): String? = computeMap()[name]

    /** Resolves [name] to live [PhpClass] PSI elements via [PhpIndex]. */
    fun resolveClasses(name: String): Collection<PhpClass> {
        val fqn = resolveFqn(name) ?: return emptyList()
        return PhpIndex.getInstance(project).getAnyByFQN(fqn)
    }

    private fun computeMap(): Map<String, String> {
        val settings = QiqSettingsService.getInstance(project)
        val configured = settings.getHelperBootstrapFiles()
        if (configured.isEmpty()) {
            if (log.isDebugEnabled) log.debug("Qiq helper registry: no bootstrap files configured")
            return emptyMap()
        }

        val files = configured.mapNotNull { settings.resolveHelperBootstrapFile(it) }
        if (files.isEmpty()) {
            if (log.isDebugEnabled) {
                log.debug("Qiq helper registry: none of the configured paths resolved: $configured")
            }
            return emptyMap()
        }

        val stamps = files.associate { it.path to it.modificationStamp }
        val key = CacheKey(stamps)
        cache[key]?.let { return it.nameToFqn }

        // Drop entries with different snapshots to keep the cache bounded
        // even as users edit bootstrap files repeatedly.
        cache.keys.removeIf { it != key }

        val merged = mutableMapOf<String, String>()
        val pm = PsiManager.getInstance(project)
        for (vf in files) {
            val psi = pm.findFile(vf) ?: continue
            extractFromFile(psi, merged)
        }

        if (log.isDebugEnabled) {
            log.debug("Qiq helper registry: scanned ${files.size} file(s), ${merged.size} helper(s): ${merged.keys}")
        }

        val value = CacheValue(merged.toMap())
        cache[key] = value
        return value.nameToFqn
    }

    /**
     * Test-visible: extract `name → fqn` registrations from a single
     * bootstrap file PSI without touching settings or the cache. Tests
     * construct an in-memory PsiFile and invoke this directly so the
     * scanner can be exercised without the LocalFileSystem dance.
     */
    fun scanForTests(file: PsiFile): Map<String, String> {
        val sink = mutableMapOf<String, String>()
        extractFromFile(file, sink)
        return sink
    }

    private fun extractFromFile(file: PsiFile, sink: MutableMap<String, String>) {
        PsiTreeUtil.processElements(file) { element ->
            if (element is MethodReference && element.name in REGISTRATION_METHOD_NAMES) {
                handleRegistration(element, sink)
            }
            true
        }
    }

    private fun handleRegistration(ref: MethodReference, sink: MutableMap<String, String>) {
        val args = ref.parameters
        val nameLiteral = args.getOrNull(0) as? StringLiteralExpression ?: return
        val factory = factoryFunctionFromArg(args.getOrNull(1)) ?: return

        val name = nameLiteral.contents
        if (name.isBlank()) return

        val fqn = extractFactoryReturnFqn(factory) ?: return
        // Last write wins. Bootstrap files are typically authored with one
        // canonical registration per name, so collisions are unexpected.
        sink[name] = fqn
    }

    /**
     * PHP's PSI wraps anonymous closures in a generic expression node, so
     * `args[1]` is typically a `PhpExpressionImpl` containing a [Function]
     * child rather than a [Function] itself. Unwrap it here so both
     * shapes succeed:
     *
     * ```
     * $x->set('a', function () { ... })   // arg = PhpExpression > Function
     * $x->set('a', fn () => new X())       // same
     * ```
     */
    private fun factoryFunctionFromArg(arg: Any?): Function? {
        val element = arg as? com.intellij.psi.PsiElement ?: return null
        if (element is Function) return element
        // Look one level in; closures appear immediately under the wrapper.
        return PsiTreeUtil.findChildOfType(element, Function::class.java)
    }

    private fun extractFactoryReturnFqn(func: Function): String? {
        // 1. Declared return type. Hpplus.Spur's QiqHelperLocatorProvider
        // is annotated this way on every closure, so this path covers the
        // common case without descending into the body.
        declaredReturnFqn(func)?.let { return it }

        // 2. Walk the body for a `return new X(...)` statement (regular
        // closures) or look at the body expression directly (arrow
        // functions, which have no PhpReturn).
        bodyNewExpressionFqn(func)?.let { return it }

        return null
    }

    private fun declaredReturnFqn(func: Function): String? {
        val declared = func.getLocalType(false)
        val classFqns = declared.types.filter { isClassFqn(it) }
        if (classFqns.size != 1) return null
        return classFqns.first()
    }

    private fun bodyNewExpressionFqn(func: Function): String? {
        val returns = PsiTreeUtil.findChildrenOfType(func, PhpReturn::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, Function::class.java) === func }
        for (ret in returns) {
            val new = PsiTreeUtil.findChildOfType(ret, NewExpression::class.java) ?: continue
            classRefFqn(new.classReference)?.let { return it }
        }

        // Arrow functions: no PhpReturn — the body expression itself is the
        // value. Search shallowly within the function for a NewExpression
        // whose nearest enclosing Function is this arrow function.
        val arrowNews = PsiTreeUtil.findChildrenOfType(func, NewExpression::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, Function::class.java) === func }
        for (new in arrowNews) {
            classRefFqn(new.classReference)?.let { return it }
        }
        return null
    }

    private fun classRefFqn(ref: ClassReference?): String? {
        if (ref == null) return null
        val fqn = ref.fqn
        return if (!fqn.isNullOrBlank()) fqn else null
    }

    private fun isClassFqn(type: String): Boolean {
        // Class FQNs in PhpType start with `\` followed by an identifier
        // segment. Built-in scalar / pseudo types use the same prefix but
        // are excluded explicitly to avoid e.g. `\void` slipping through.
        if (!type.startsWith("\\")) return false
        if (type in BUILTIN_PSEUDO_TYPES) return false
        // Avoid generics / `?nullable` / intersection / array shapes.
        if (type.any { it == '|' || it == '&' || it == '?' || it == '<' || it == '(' }) return false
        return true
    }

    /**
     * Forget every cached entry. Called by [QiqProjectConfigurable] when
     * the bootstrap-file list changes (the modification stamps stay the
     * same so the natural snapshot-keyed invalidation does not fire), and
     * by tests that mutate fixtures between assertions.
     */
    fun invalidateCache() {
        cache.clear()
    }

    companion object {
        private val log = Logger.getInstance(QiqHelperRegistry::class.java)

        fun getInstance(project: Project): QiqHelperRegistry =
            project.getService(QiqHelperRegistry::class.java)

        // HelperLocator's public API in Qiq 1.x is `set(name, factory)`.
        // `setFactory` and `register` are accepted as common subclass
        // additions (e.g. BEAR\QiqModule\HelperLocator only uses `set`,
        // but other community wrappers occasionally expose either).
        private val REGISTRATION_METHOD_NAMES = setOf("set", "setFactory", "register")

        private val BUILTIN_PSEUDO_TYPES = setOf(
            "\\void", "\\mixed", "\\never", "\\null", "\\true", "\\false",
            "\\int", "\\integer", "\\bool", "\\boolean", "\\string",
            "\\float", "\\double", "\\array", "\\iterable", "\\object",
            "\\callable", "\\callback", "\\resource", "\\number",
            "\\class-string", "\\static", "\\self", "\\parent", "\\this"
        )
    }
}
