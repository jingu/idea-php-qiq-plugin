package io.github.jingu.idea_qiq_plugin.helper

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
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

    private fun buildIndex(): Map<String, List<Method>> {
        val index = PhpIndex.getInstance(project)
        val result = mutableMapOf<String, MutableList<Method>>()
        index.processAllSubclasses(HELPERS_BASE_FQN) { phpClass ->
            // Only user-defined subclasses contribute; library helper classes
            // (\Qiq\Helpers, \Qiq\Helper\Html\HtmlHelpers, ...) are covered by
            // the runtime stub already.
            if (!phpClass.fqn.startsWith(QIQ_NAMESPACE_PREFIX)) {
                for (method in phpClass.ownMethods) {
                    if (isHelperMethod(method)) {
                        result.getOrPut(method.name) { mutableListOf() }.add(method)
                    }
                }
            }
            true
        }
        return result
    }

    private fun isHelperMethod(method: Method): Boolean =
        method.access == PhpModifier.Access.PUBLIC &&
            !method.isStatic &&
            !method.isAbstract &&
            !method.name.startsWith("__")

    companion object {
        private const val HELPERS_BASE_FQN = "\\Qiq\\Helpers"
        private const val QIQ_NAMESPACE_PREFIX = "\\Qiq\\"

        fun getInstance(project: Project): QiqHelpersClassResolver =
            project.getService(QiqHelpersClassResolver::class.java)
    }
}
