package io.github.jingu.idea_qiq_plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.ui.QiqBundle
import io.github.jingu.idea_qiq_plugin.util.QiqTemplateResolver
import io.github.jingu.idea_qiq_plugin.util.QiqUtil

/**
 * Warns when a Qiq template-referencing call points at a template that does not
 * exist — the editor-time counterpart to the Go to Declaration on the same
 * argument.
 *
 * Only the calls that take a template path are inspected — `setLayout`,
 * `render`, `extends`, `include` — and only their first argument when it is a
 * plain string literal (interpolated or concatenated paths are left alone).
 * Existence is decided by [QiqUtil.findTemplateByPath], the exact resolver
 * Go to Declaration uses, so a path is flagged precisely when Cmd/Ctrl+click
 * would fail to navigate. A method call is treated as a template call only when
 * its receiver is `$this`, to avoid flagging an unrelated `$other->render(...)`.
 */
class QiqTemplatePathInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) = inspectCall(reference, holder)

            override fun visitPhpMethodReference(reference: MethodReference) = inspectCall(reference, holder)
        }

    private fun inspectCall(call: FunctionReference, holder: ProblemsHolder) {
        if (!QiqInjectionSupport.isInQiqFile(call)) return

        val name = call.name?.takeIf { it.isNotEmpty() } ?: return
        if (name !in TEMPLATE_FUNCTIONS) return
        // Bare calls (`render('...')`) and the injected static directive
        // (`\QiqRuntimeFunctions::extends('...')`) are template calls. An
        // *instance* call is only one when the receiver is `$this`; skip
        // `$other->render(...)`, which is some unrelated object's method.
        val receiver = (call as? MethodReference)?.classReference
        if (receiver is Variable && receiver.name != "this") return

        val arg = call.parameterList?.parameters?.firstOrNull() as? StringLiteralExpression ?: return
        val path = arg.contents
        // Skip anything dynamic (interpolation / any whitespace) we cannot
        // resolve statically; share the same static-path gate as the resolver.
        if (QiqTemplateResolver.normalizePath(path) == null) return

        val contextFile = InjectedLanguageManager.getInstance(call.project).getTopLevelFile(call)?.virtualFile
            ?: call.containingFile?.virtualFile
            ?: return
        if (QiqUtil.findTemplateByPath(call.project, path, contextFile) != null) return

        val range = TextRange(1, arg.textLength - 1).takeIf { it.startOffset < it.endOffset } ?: return
        holder.registerProblem(arg, range, QiqBundle.message("inspection.template.path.missing", path))
    }

    private companion object {
        private val TEMPLATE_FUNCTIONS = setOf("setLayout", "render", "extends", "include")
    }
}
