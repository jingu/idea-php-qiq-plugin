package io.github.jingu.idea_qiq_plugin.inspection

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FunctionReference
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperRegistry
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport

/**
 * Suppresses PhpStorm's "undefined function/method" warnings for Qiq helper
 * calls that the plugin can resolve via [QiqHelperRegistry].
 *
 * A bare `{{ myHelper(...) }}` compiles to `$this->myHelper(...)`, which Qiq
 * dispatches through `HelperLocator` at runtime. The injected PHP therefore
 * references a function/method that has no static declaration, so
 * PhpUndefinedFunctionInspection / PhpUndefinedMethodInspection flag it even
 * though [QiqHelperGotoDeclarationHandler] can navigate to the helper class.
 * This mirrors how the bundled Blade support suppresses PHP inspections
 * inside Blade-injected fragments.
 *
 * Only names registered in a configured bootstrap file are suppressed; truly
 * undefined calls keep their warning.
 */
class QiqHelperInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId !in SUPPRESSED_TOOLS) return false

        // The inspection registers the problem on the call (or its name leaf);
        // accept either shape. MethodReference also implements FunctionReference.
        val call = element as? FunctionReference
            ?: PsiTreeUtil.getParentOfType(element, FunctionReference::class.java, false)
            ?: return false

        if (!QiqInjectionSupport.isInQiqFile(call)) return false

        val name = call.name?.takeIf { it.isNotEmpty() } ?: return false
        return QiqHelperRegistry.getInstance(element.project).resolveFqn(name) != null
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY

    private companion object {
        private val SUPPRESSED_TOOLS = setOf(
            "PhpUndefinedFunctionInspection",
            "PhpUndefinedMethodInspection",
        )
    }
}
