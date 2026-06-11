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

            val typedPrefix = inQuotePrefix(literal.text, parameters.offset - literal.textRange.startOffset) ?: return
            val sink = result.withPrefixMatcher(PlainPrefixMatcher(typedPrefix, false))

            for (name in QiqSectionIndex.definedNames(project, contextFile)) {
                sink.addElement(LookupElementBuilder.create(name).withTypeText("Qiq section", true))
            }
        }
    }

    companion object {
        /**
         * The in-quote text before the caret ([caretInLiteral] offset from the
         * literal start), excluding the surrounding quotes. Returns null when the
         * caret is at or past the closing quote, so the closing quote never leaks
         * into the prefix; an unterminated literal (still being typed) has no
         * closing quote, so its whole tail is included. Pure, unit-tested.
         */
        fun inQuotePrefix(text: String, caretInLiteral: Int): String? {
            if (text.isEmpty()) return null
            val quote = text[0]
            if (quote != '\'' && quote != '"') return null
            if (caretInLiteral < 1) return null
            // Exclude a closing quote when the literal is terminated.
            val contentEnd = if (text.length >= 2 && text.last() == quote) text.length - 1 else text.length
            if (caretInLiteral > contentEnd) return null
            return text.substring(1, caretInLiteral)
        }
    }
}
