package io.github.jingu.idea_qiq_plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.ui.QiqBundle
import io.github.jingu.idea_qiq_plugin.util.QiqSectionCall
import io.github.jingu.idea_qiq_plugin.util.QiqSectionIndex

/**
 * Warns when `getSection('x')` reads a name that no `setSection` defines anywhere
 * under the detected template roots.
 *
 * Deliberately conservative: only `getSection` is flagged — `hasSection('x')`
 * legitimately tests a possibly-absent name, so a missing one is not a bug — only
 * a plain string-literal name is checked, and the warning is suppressed when the
 * section index is empty (no templates scanned / roots unresolved), since we then
 * cannot be sure the name is truly undefined.
 */
class QiqSectionNameInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) = inspectCall(reference, holder)

            override fun visitPhpMethodReference(reference: MethodReference) = inspectCall(reference, holder)
        }

    private fun inspectCall(call: FunctionReference, holder: ProblemsHolder) {
        if (!QiqInjectionSupport.isInQiqFile(call)) return

        if (!QiqSectionCall.isInspectableReader(call)) return
        val arg = call.parameterList?.parameters?.firstOrNull() as? StringLiteralExpression ?: return
        val name = arg.contents
        if (name.isEmpty()) return

        val contextFile = InjectedLanguageManager.getInstance(call.project).getTopLevelFile(call)?.virtualFile
            ?: call.containingFile?.virtualFile
            ?: return

        val index = QiqSectionIndex.index(call.project, contextFile)
        // Skip only when nothing was scanned at all (roots unresolved); a project
        // that has getSection usages but no definitions is exactly what we want to
        // flag, so an empty *definitions* list alone must not silence the warning.
        if (index.definitions.isEmpty() && index.usages.isEmpty()) return
        if (index.definitions.any { it.name == name }) return

        val range = TextRange(1, arg.textLength - 1).takeIf { it.startOffset < it.endOffset } ?: return
        holder.registerProblem(arg, range, QiqBundle.message("inspection.section.undefined", name))
    }
}
