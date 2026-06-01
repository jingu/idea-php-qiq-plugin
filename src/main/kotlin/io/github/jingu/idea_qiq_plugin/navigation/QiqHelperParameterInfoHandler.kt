package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUtils
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperRegistry
import io.github.jingu.idea_qiq_plugin.helper.QiqHelpersClassResolver
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport

/**
 * Parameter-info (Ctrl/Cmd+P, and the auto popup after `(`) for Qiq helper
 * calls inside templates.
 *
 * A helper call such as `{{= renderAd($this, '/x', [...]) }}` is injected as a
 * bare global call `renderAd(...)` (or a `$this->renderAd(...)` method call),
 * neither of which the PHP runtime can statically resolve — Qiq dispatches the
 * name through HelperLocator / a `Qiq\Helpers` subclass. PhpStorm's own
 * parameter-info handler therefore finds nothing and shows no hint. This
 * handler fills that gap by rendering the signature of the resolved helper
 * target — the same target [QiqHelperGotoDeclarationHandler] jumps to: a 1.x
 * helper class's `__invoke`, or a 2.x/3.x `Qiq\Helpers` subclass method.
 *
 * The two handlers are mutually exclusive (PhpStorm resolves real functions,
 * this one resolves helpers), so registration order does not matter.
 */
class QiqHelperParameterInfoHandler : ParameterInfoHandler<ParameterList, Function> {

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): ParameterList? {
        val parameterList = parameterListAt(context.file, context.offset) ?: return null
        val call = parameterList.parent as? FunctionReference ?: return null
        if (!QiqInjectionSupport.isInQiqFile(call)) return null

        val name = call.name?.takeIf { it.isNotEmpty() } ?: return null
        val targets = resolveHelperTargets(call.project, name)
        if (targets.isEmpty()) return null

        context.itemsToShow = targets.toTypedArray()
        return parameterList
    }

    override fun showParameterInfo(element: ParameterList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ParameterList? =
        parameterListAt(context.file, context.offset)

    override fun updateParameterInfo(parameterOwner: ParameterList, context: UpdateParameterInfoContext) {
        // Dismiss if the caret left the call we started on.
        if (context.parameterOwner != null && context.parameterOwner !== parameterOwner) {
            context.removeHint()
            return
        }
        val index = ParameterInfoUtils.getCurrentParameterIndex(
            parameterOwner.node,
            context.offset,
            PhpTokenTypes.opCOMMA,
        )
        context.setCurrentParameter(index)
    }

    override fun updateUI(p: Function, context: ParameterInfoUIContext) {
        val params = p.parameters
        if (params.isEmpty()) {
            context.setupUIComponentPresentation(
                NO_PARAMETERS, -1, -1, false, false, false, context.defaultParameterColor,
            )
            return
        }

        val fragments = params.map(::renderParameter)
        val text = fragments.joinToString(SEPARATOR)

        // Highlight the fragment for the parameter the caret currently sits on.
        val current = context.currentParameterIndex
        var highlightStart = -1
        var highlightEnd = -1
        if (current in fragments.indices) {
            highlightStart = fragments.take(current).sumOf { it.length + SEPARATOR.length }
            highlightEnd = highlightStart + fragments[current].length
        }

        context.setupUIComponentPresentation(
            text, highlightStart, highlightEnd, false, false, false, context.defaultParameterColor,
        )
    }

    private fun parameterListAt(file: PsiFile, offset: Int): ParameterList? {
        val element = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return null
        // Caret inside the argument list.
        PsiTreeUtil.getParentOfType(element, ParameterList::class.java, false)?.let { return it }
        // Caret on the call name or the parentheses, which are siblings of the
        // ParameterList under the call. Climb to the call and take its list.
        return PsiTreeUtil.getParentOfType(element, FunctionReference::class.java, false)?.parameterList
    }

    /**
     * Resolve [name] to the callable target(s) whose signature to show: a 1.x
     * helper class's `__invoke` (classes without one are dropped — there is no
     * signature to present) plus any 2.x/3.x helper methods.
     */
    private fun resolveHelperTargets(project: Project, name: String): List<Function> {
        val targets = mutableListOf<Function>()
        QiqHelperRegistry.getInstance(project).resolveClasses(name)
            .mapNotNull { it.findMethodByName("__invoke") }
            .forEach(targets::add)
        targets.addAll(QiqHelpersClassResolver.getInstance(project).resolve(name))
        return targets
    }

    private fun renderParameter(parameter: Parameter): String = buildString {
        val type = parameter.declaredType.toString()
        if (type.isNotEmpty()) append(type).append(' ')
        if (parameter.isPassByRef) append('&')
        if (parameter.isVariadic) append("...")
        append('$').append(parameter.name)
        parameter.defaultValuePresentation?.let { append(" = ").append(it) }
    }

    private companion object {
        private const val SEPARATOR = ", "
        private const val NO_PARAMETERS = "<no parameters>"
    }
}
