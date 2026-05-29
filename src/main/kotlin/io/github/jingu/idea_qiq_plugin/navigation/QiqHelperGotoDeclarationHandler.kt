package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperRegistry
import io.github.jingu.idea_qiq_plugin.helper.QiqHelpersClassResolver
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport

/**
 * Go to Declaration for Qiq helper calls inside templates.
 *
 * A bare `{{ myHelper(...) }}` is compiled by Qiq into `$this->myHelper(...)`
 * and surfaces in the injected PHP as a [FunctionReference] (bare call) or a
 * MethodReference (`$this->myHelper(...)`). Both implement
 * `FunctionReference`. Because those elements carry their own (failing)
 * resolution for an unregistered helper name, a `PsiReferenceContributor`
 * is never consulted for them — only string literals aggregate contributed
 * references. The canonical mechanism for adding navigation to call names is
 * this [GotoDeclarationHandler].
 *
 * Targets are sourced from [QiqHelperRegistry], which scans the bootstrap
 * files configured in Settings > Languages & Frameworks > Qiq Templates.
 */
class QiqHelperGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val src = sourceElement ?: return null

        // Climb to the enclosing call. MethodReference also implements
        // FunctionReference, so this catches both `helper(...)` and
        // `$this->helper(...)`.
        val call = PsiTreeUtil.getParentOfType(src, FunctionReference::class.java, false) ?: return null

        // Only fire when the caret sits on the call *name*, not on an
        // argument. textRange comparison stays within a single PSI tree
        // (the injected file), avoiding host/injected offset confusion.
        val nameNode = call.nameNode ?: return null
        if (!nameNode.textRange.contains(src.textRange)) return null

        if (!QiqInjectionSupport.isInQiqFile(call)) return null

        val name = call.name?.takeIf { it.isNotEmpty() } ?: return null
        val project = call.project

        // 1.x HelperLocator style: name -> invokable Qiq\Helper\<X> class.
        val classes = QiqHelperRegistry.getInstance(project).resolveClasses(name)
        // 2.x/3.x style: name -> public method on a Qiq\Helpers subclass.
        val methods = QiqHelpersClassResolver.getInstance(project).resolve(name)

        if (log.isDebugEnabled) {
            log.debug("Qiq helper goto: name='$name' locatorClasses=${classes.size} helperMethods=${methods.size}")
        }
        if (classes.isEmpty() && methods.isEmpty()) return null

        val targets = mutableListOf<PsiElement>()
        // Prefer the __invoke method — the actual call target Qiq dispatches
        // to — falling back to the class declaration when none is declared.
        classes.forEach { phpClass -> targets.add(invokeMethodOf(phpClass) ?: phpClass) }
        targets.addAll(methods)
        return targets.toTypedArray()
    }

    private fun invokeMethodOf(phpClass: PhpClass): PsiElement? =
        phpClass.findMethodByName("__invoke")

    private companion object {
        private val log = Logger.getInstance(QiqHelperGotoDeclarationHandler::class.java)
    }
}
