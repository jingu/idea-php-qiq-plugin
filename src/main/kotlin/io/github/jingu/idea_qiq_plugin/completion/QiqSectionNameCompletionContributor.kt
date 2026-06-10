package io.github.jingu.idea_qiq_plugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.util.QiqSectionCall
import io.github.jingu.idea_qiq_plugin.util.QiqSectionIndex

/**
 * Completes section names in the first argument of `getSection('...')` /
 * `hasSection('...')` (bare or `$this->`) inside Qiq templates.
 *
 * Candidates are the `setSection` names from [QiqSectionIndex] (template-root-
 * wide). Pairs with the Go to Declaration on the same argument.
 */
class QiqSectionNameCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            SectionNameCompletionProvider(),
        )
    }

    private class SectionNameCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            if (!QiqInjectionSupport.isInQiqFile(position)) return

            val literal = PsiTreeUtil.getParentOfType(position, StringLiteralExpression::class.java) ?: return
            if (!QiqSectionCall.isReaderArg(literal)) return

            val project = position.project
            val ilm = InjectedLanguageManager.getInstance(project)
            val contextFile = (ilm.getTopLevelFile(parameters.originalFile) ?: parameters.originalFile)
                .virtualFile ?: return

            val typedPrefix = inQuotePrefix(literal, parameters.offset) ?: return
            val sink = result.withPrefixMatcher(PlainPrefixMatcher(typedPrefix, false))

            for (name in QiqSectionIndex.definedNames(project, contextFile)) {
                sink.addElement(LookupElementBuilder.create(name).withTypeText("Qiq section", true))
            }
        }

        /** The in-quote text before the caret, excluding the surrounding quote. */
        private fun inQuotePrefix(literal: StringLiteralExpression, caretOffset: Int): String? {
            val caretInLiteral = caretOffset - literal.textRange.startOffset
            val text = literal.text
            if (caretInLiteral < 1 || caretInLiteral > text.length) return null
            return text.substring(1, caretInLiteral)
        }
    }
}
