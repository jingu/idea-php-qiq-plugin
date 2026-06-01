package io.github.jingu.idea_qiq_plugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpTypedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperRegistry
import io.github.jingu.idea_qiq_plugin.helper.QiqHelpersClassResolver
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService
import io.github.jingu.idea_qiq_plugin.ui.QiqBundle

/**
 * Validates Qiq helper calls inside templates against the resolved helper
 * target's signature: the number of arguments, and the type of each positional
 * argument.
 *
 * PhpStorm's own argument inspections never fire here: the injected
 * `helper(...)` is an unresolved global call (Qiq dispatches helpers at
 * runtime), so there is no declaration for the platform to check against.
 * This inspection compares the call to the same target
 * [QiqHelperGotoDeclarationHandler] resolves — a 1.x helper class's `__invoke`,
 * or a 2.x/3.x `Qiq\Helpers` subclass method.
 *
 * Both checks are conservative — they only report what is unambiguous, so a
 * false positive never fires:
 *  - count: skipped when named arguments or spread (`...`) make the positional
 *    count meaningless; with several resolved targets a call is flagged only
 *    when it is invalid for every one.
 *  - type: only when exactly one target resolves, and only for a positional
 *    argument whose inferred type and the parameter's declared type are both
 *    fully known (anything `mixed` / unresolved is left alone). Compatibility
 *    is delegated to PhpStorm's own [PhpType.isConvertibleFromGlobal], so
 *    inheritance, unions, nullables and scalar widening are honoured.
 */
class QiqHelperArgumentsInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) = inspectCall(reference, holder)

            override fun visitPhpMethodReference(reference: MethodReference) = inspectCall(reference, holder)
        }

    private fun inspectCall(call: FunctionReference, holder: ProblemsHolder) {
        if (!QiqInjectionSupport.isInQiqFile(call)) return

        val name = call.name?.takeIf { it.isNotEmpty() } ?: return
        val targets = resolveHelperTargets(call, name)
        if (targets.isEmpty()) return

        val parameterList = call.parameterList ?: return
        // Named / spread arguments make positional matching meaningless.
        if (hasUncountableArgument(parameterList.text)) return

        val args = parameterList.parameters
        if (checkArgumentCount(name, args.size, targets, parameterList, holder)) return

        // Type-check only against a single unambiguous target.
        targets.singleOrNull()?.let { checkArgumentTypes(name, args, it, call.project, holder) }
    }

    /** @return true when a count problem was reported (skip the type pass then). */
    private fun checkArgumentCount(
        name: String,
        argCount: Int,
        targets: List<Function>,
        parameterList: ParameterList,
        holder: ProblemsHolder,
    ): Boolean {
        val arity = Arity.of(targets)
        val message = when {
            argCount < arity.minRequired ->
                QiqBundle.message("inspection.helper.argcount.few", name, arity.minRequired, argCount)
            arity.maxAllowed != null && argCount > arity.maxAllowed ->
                QiqBundle.message("inspection.helper.argcount.many", name, arity.maxAllowed, argCount)
            else -> return false
        }
        holder.registerProblem(parameterList, message)
        return true
    }

    private fun checkArgumentTypes(
        name: String,
        args: Array<PsiElement>,
        target: Function,
        project: Project,
        holder: ProblemsHolder,
    ) {
        val params = target.parameters
        // Under strict_types, a non-nullable scalar parameter rejects null even
        // though PhpStorm treats null as loosely convertible to a scalar.
        val strict = QiqSettingsService.getInstance(project).isStrictTypesEnabled()
        for (i in args.indices) {
            val param = params.getOrNull(i) ?: break
            if (param.isVariadic) break // the variadic tail accepts any extra args

            val expected = param.declaredType.global(project)
            if (isUncheckable(expected)) continue

            val arg = args[i] as? PhpTypedElement ?: continue
            val actual = arg.globalType
            if (isUncheckable(actual)) continue

            val mismatch = !expected.isConvertibleFromGlobal(project, actual) ||
                (strict && isNull(actual) && !isNullable(expected))
            if (mismatch) {
                holder.registerProblem(
                    arg,
                    QiqBundle.message(
                        "inspection.helper.argtype.mismatch",
                        i + 1, name, present(expected), present(actual),
                    ),
                )
            }
        }
    }

    private fun resolveHelperTargets(call: FunctionReference, name: String): List<Function> {
        val project = call.project
        val targets = mutableListOf<Function>()
        QiqHelperRegistry.getInstance(project).resolveClasses(name)
            .mapNotNull { it.findMethodByName("__invoke") }
            .forEach(targets::add)
        targets.addAll(QiqHelpersClassResolver.getInstance(project).resolve(name))
        return targets
    }

    /**
     * The accepted argument-count window across all resolved targets: the call
     * is valid when its count is `>= minRequired` and (if bounded) `<= maxAllowed`.
     * A variadic target makes the upper bound unbounded ([maxAllowed] = null).
     */
    private data class Arity(val minRequired: Int, val maxAllowed: Int?) {
        companion object {
            fun of(targets: List<Function>): Arity {
                val minRequired = targets.minOf { requiredCount(it) }
                val maxAllowed = if (targets.any(::hasVariadic)) null else targets.maxOf { it.parameters.size }
                return Arity(minRequired, maxAllowed)
            }

            private fun requiredCount(function: Function): Int =
                function.parameters.count { !it.isOptional && !it.isVariadic }

            private fun hasVariadic(function: Function): Boolean =
                function.parameters.any(Parameter::isVariadic)
        }
    }

    companion object {
        /** A type we cannot reliably compare — no constraint, or not fully inferred. */
        private fun isUncheckable(type: PhpType): Boolean =
            type.isEmpty || type.hasUnknown() || type.types.any { it == PhpType._MIXED }

        /** The value is exactly `null` (e.g. the `null` literal), with no other type. */
        private fun isNull(type: PhpType): Boolean =
            type.types.isNotEmpty() && type.types.all { it == PhpType._NULL }

        /** The parameter declares it accepts null (`?T` / a `null` union / mixed). */
        private fun isNullable(type: PhpType): Boolean =
            type.types.any { it == PhpType._NULL || it == PhpType._MIXED }

        private fun present(type: PhpType): String = type.toString().removePrefix("\\")

        /**
         * True when [parameterListText] (the call's `( ... )`) contains a
         * top-level named argument (`name:`) or a spread (`...`), which make a
         * positional count unreliable. String literals and nested
         * `()` / `[]` / `{}` are ignored so only argument-level syntax counts.
         *
         * Pure text logic, unit-tested independently of PSI.
         */
        fun hasUncountableArgument(parameterListText: String): Boolean {
            val text = parameterListText.trim()
            // The PSI text may or may not include the wrapping parentheses;
            // arguments sit one level deeper when it does.
            val argDepth = if (text.startsWith("(")) 1 else 0
            var depth = 0
            var i = 0
            while (i < text.length) {
                when (val c = text[i]) {
                    '\'', '"' -> {
                        i = skipString(text, i, c)
                        continue
                    }
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    '.' -> if (depth == argDepth && text.startsWith("...", i)) return true
                    ':' -> {
                        // A single top-level colon separates `name: value`.
                        // `::` (static access) and ternary colons nested in an
                        // argument are excluded by the depth / double-colon guard.
                        val prev = text.getOrNull(i - 1)
                        val next = text.getOrNull(i + 1)
                        if (depth == argDepth && prev != ':' && next != ':') return true
                    }
                }
                i++
            }
            return false
        }

        /** Returns the index just past the closing quote of the literal opened at [start]. */
        private fun skipString(text: String, start: Int, quote: Char): Int {
            var i = start + 1
            while (i < text.length) {
                when (text[i]) {
                    '\\' -> i++ // skip the escaped char
                    quote -> return i + 1
                }
                i++
            }
            return i
        }
    }
}
