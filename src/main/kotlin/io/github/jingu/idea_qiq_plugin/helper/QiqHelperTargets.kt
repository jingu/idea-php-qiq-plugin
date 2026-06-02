package io.github.jingu.idea_qiq_plugin.helper

import com.intellij.openapi.project.Project
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Variable

/**
 * Resolves a Qiq helper name to the callable PHP declaration(s) it dispatches
 * to, across both supported styles:
 *  - 1.x HelperLocator: the registered class's `__invoke` ([QiqHelperRegistry]).
 *  - 2.x/3.x: a public method on a `Qiq\Helpers` subclass ([QiqHelpersClassResolver]).
 *
 * Shared by every consumer that needs the helper's *signature* — parameter
 * info, the argument inspection, and quick documentation — so they always agree
 * on what a call resolves to. (Go to Declaration resolves separately because it
 * also offers the class itself as a fallback navigation target.)
 */
object QiqHelperTargets {

    /** Every callable target for [name]; empty when it is not a known helper. */
    fun functions(project: Project, name: String): List<Function> {
        val targets = mutableListOf<Function>()
        QiqHelperRegistry.getInstance(project).resolveClasses(name)
            .mapNotNull { it.findMethodByName("__invoke") }
            .forEach(targets::add)
        targets.addAll(QiqHelpersClassResolver.getInstance(project).resolve(name))
        return targets
    }

    /**
     * Whether [call] is shaped like a Qiq helper dispatch: a bare call
     * (`helper(...)`) or a `$this->helper(...)` method call. A call on any other
     * receiver — `$other->helper(...)`, `Foo::helper(...)` — is an ordinary PHP
     * call that merely shares a name with a helper, not a helper invocation, so
     * the signature-based features must not treat it as one.
     */
    fun isHelperDispatch(call: FunctionReference): Boolean =
        call !is MethodReference || (call.classReference as? Variable)?.name == "this"
}
