package io.github.jingu.idea_qiq_plugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MemberReference
import com.jetbrains.php.lang.psi.elements.Variable
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperRegistry
import io.github.jingu.idea_qiq_plugin.helper.QiqHelpersClassResolver
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport

/**
 * Completes custom Qiq helper names inside templates.
 *
 * Built-in helpers are global functions in the bundled qiq_runtime.php stub,
 * so PhpStorm already completes them. Custom helpers are not: 1.x helpers are
 * registered at runtime via `HelperLocator::set()`, and 2.x/3.x helpers are
 * methods on a `Qiq\Helpers` subclass invoked through an untyped `$this`.
 * This contributor surfaces those names — the same set the helper navigation
 * ([io.github.jingu.idea_qiq_plugin.navigation.QiqHelperGotoDeclarationHandler])
 * can resolve — at call-name positions like `{{ helperName| }}` and
 * `{{ $this->helperName| }}`.
 */
class QiqHelperCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            // Broad position pattern; the real gating (Qiq file + call-name
            // position) happens in the provider where the PSI context is known.
            PlatformPatterns.psiElement(),
            HelperCompletionProvider(),
        )
    }

    private class HelperCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            if (!QiqInjectionSupport.isInQiqFile(position)) return

            if (!isHelperCallNamePosition(position.parent)) return

            val project = position.project
            val names = LinkedHashSet<String>().apply {
                addAll(QiqHelperRegistry.getInstance(project).allHelperNames())
                addAll(QiqHelpersClassResolver.getInstance(project).allHelperNames())
            }
            if (names.isEmpty()) return

            for (name in names) {
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withTypeText("Qiq helper", true)
                        .withInsertHandler(CALL_INSERT_HANDLER),
                )
            }
        }
    }

    companion object {
        /**
         * A helper is invoked either as a bare call/name (`{{ helper| }}`,
         * which is a [ConstantReference] before the `(` is typed and a
         * [FunctionReference] after) or on `$this` (`{{ $this->helper| }}`).
         * Member references on any other qualifier (e.g. `$article->title`)
         * are not helpers, so they are excluded to avoid noise.
         *
         * Order matters: [com.jetbrains.php.lang.psi.elements.MethodReference]
         * is both a MemberReference and a FunctionReference, so the
         * MemberReference branch (with the `$this` check) must come first.
         */
        fun isHelperCallNamePosition(parent: PsiElement?): Boolean = when (parent) {
            is MemberReference -> (parent.classReference as? Variable)?.name == "this"
            is FunctionReference -> true
            is ConstantReference -> true
            else -> false
        }

        // Append `()` and place the caret between the parentheses, unless the
        // call already has an opening paren (e.g. re-completing an existing
        // call). Mirrors how PhpStorm completes function calls.
        private val CALL_INSERT_HANDLER = InsertHandler<LookupElement> { context: InsertionContext, _ ->
            val editor = context.editor
            val document = context.document
            val tailOffset = context.tailOffset
            val alreadyHasParen = document.charsSequence.let { text ->
                tailOffset < text.length && text[tailOffset] == '('
            }
            if (!alreadyHasParen) {
                document.insertString(tailOffset, "()")
            }
            editor.caretModel.moveToOffset(tailOffset + 1)
        }
    }
}
