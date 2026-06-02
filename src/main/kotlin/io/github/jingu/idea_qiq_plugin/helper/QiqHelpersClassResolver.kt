package io.github.jingu.idea_qiq_plugin.helper

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpModifier

/**
 * Resolves Qiq 2.x / 3.x custom helpers.
 *
 * The official docs define a custom helper as a public method on a subclass
 * of `Qiq\Helpers` (typically extending `Qiq\Helper\Html\HtmlHelpers`),
 * passed via `Template::new(helpers: new CustomHelpers())`. Templates then
 * call it as `{{ name(...) }}` / `{{ $this->name(...) }}`.
 *
 * Unlike the 1.x HelperLocator style ([QiqHelperRegistry], which needs the
 * user to nominate bootstrap files), this resolver is fully automatic: every
 * project-defined `Qiq\Helpers` subclass is discovered through [PhpIndex].
 * Library classes under the `\Qiq\` namespace (Helpers / HtmlHelpers and the
 * built-in helper methods) are skipped — those built-ins already resolve via
 * the bundled qiq_runtime.php stub.
 *
 * Self-gating: in a 1.x project `\Qiq\Helpers` does not exist, so the
 * subclass walk yields nothing and this resolver is a no-op.
 */
@Service(Service.Level.PROJECT)
class QiqHelpersClassResolver(private val project: Project) {

    // helperName -> public method declarations across user-defined
    // Qiq\Helpers subclasses. Rebuilt whenever PSI changes.
    private val methodIndex: CachedValue<Map<String, List<Method>>> =
        CachedValuesManager.getManager(project).createCachedValue {
            CachedValueProvider.Result.create(
                buildIndex(),
                PsiModificationTracker.getInstance(project),
            )
        }

    /** Public helper methods declared for [name], or empty when unknown. */
    fun resolve(name: String): List<Method> = methodIndex.value[name].orEmpty()

    fun hasHelper(name: String): Boolean = methodIndex.value.containsKey(name)

    /** Every auto-discovered Qiq 2.x/3.x helper name. */
    fun allHelperNames(): Set<String> = methodIndex.value.keys

    private fun buildIndex(): Map<String, List<Method>> {
        val index = PhpIndex.getInstance(project)
        val result = mutableMapOf<String, MutableList<Method>>()
        index.processAllSubclasses(HELPERS_BASE_FQN) { phpClass ->
            for (method in helperMethodsOf(phpClass)) {
                result.getOrPut(method.name) { mutableListOf() }.add(method)
            }
            true
        }
        return result
    }

    companion object {
        private const val HELPERS_BASE_FQN = "\\Qiq\\Helpers"
        private const val QIQ_NAMESPACE_PREFIX = "\\Qiq\\"

        fun getInstance(project: Project): QiqHelpersClassResolver =
            project.getService(QiqHelpersClassResolver::class.java)

        /**
         * The public helper methods a single `Qiq\Helpers` subclass
         * contributes. Library classes under `\Qiq\` contribute nothing
         * (their built-ins resolve via the runtime stub); only public,
         * non-static, non-magic own methods of user classes qualify.
         *
         * Pure PSI logic (no index access) so it can be unit-tested against
         * an in-memory file.
         */
        fun helperMethodsOf(phpClass: PhpClass): List<Method> {
            if (phpClass.fqn.startsWith(QIQ_NAMESPACE_PREFIX)) return emptyList()
            return phpClass.ownMethods.filter(::isHelperMethod)
        }

        private fun isHelperMethod(method: Method): Boolean =
            method.access == PhpModifier.Access.PUBLIC &&
                !method.isStatic &&
                !method.isAbstract &&
                !method.name.startsWith("__")
    }
}
