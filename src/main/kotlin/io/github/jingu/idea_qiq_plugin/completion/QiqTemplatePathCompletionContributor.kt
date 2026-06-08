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
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.util.QiqTemplateResolver

/**
 * Completes template paths in the first string argument of Qiq's
 * template-referencing calls — `setLayout('...')`, `render('...')`,
 * `extends('...')`, `include('...')` — inside Qiq templates.
 *
 * Candidates come from [QiqTemplateResolver.listTemplatePaths] for the current
 * file — root-relative and root-absolute (`/...`) template paths with the Qiq
 * extension stripped (so `layout/base.qiq.php` is offered as `layout/base`).
 * The same resolver backs Go to Declaration and the missing-template
 * inspection, so every path offered here resolves.
 */
class QiqTemplatePathCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            TemplatePathCompletionProvider(),
        )
    }

    private class TemplatePathCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            if (!QiqInjectionSupport.isInQiqFile(position)) return

            val stringLiteral = PsiTreeUtil.getParentOfType(position, StringLiteralExpression::class.java) ?: return
            if (!isFirstArgOfTemplateCall(stringLiteral)) return

            val project = position.project
            val ilm = InjectedLanguageManager.getInstance(project)
            val contextFile = (ilm.getTopLevelFile(parameters.originalFile) ?: parameters.originalFile)
                .virtualFile ?: return

            // Replace the already-typed in-quote text, so `layout/ba` is
            // matched/replaced rather than matching against the whole literal.
            val typedPrefix = inQuotePrefix(stringLiteral, parameters.offset) ?: return

            // Substring matching (PlainPrefixMatcher in contains mode, not the
            // default camel-hump matcher): the leading `/` is optional in Qiq, so
            // `partial/ad` must surface `/partial/ad/adtag`, and any path that
            // contains the typed fragment is offered. True prefix hits still rank
            // first via the matcher's isStartMatch.
            val pathResult = result.withPrefixMatcher(PlainPrefixMatcher(typedPrefix, false))

            val seen = HashSet<String>()
            fun offer(path: String) {
                if (seen.add(path)) {
                    pathResult.addElement(LookupElementBuilder.create(path).withTypeText("Qiq template", true))
                }
            }

            // Both the relative paths (bare `partial/...`, resolved against the
            // detected template roots) and the root-absolute paths (`/layout/base`
            // from the template base) come from QiqTemplateResolver, the same
            // resolver Go to Declaration and the missing-template inspection use,
            // so every path offered here is guaranteed to resolve.
            QiqTemplateResolver.listTemplatePaths(project, contextFile).forEach(::offer)
        }

        private fun isFirstArgOfTemplateCall(stringLiteral: StringLiteralExpression): Boolean {
            val parameterList = stringLiteral.parent as? ParameterList ?: return false
            val call = parameterList.parent as? FunctionReference ?: return false
            if (call.name !in TEMPLATE_FUNCTIONS) return false
            return parameterList.parameters.firstOrNull() === stringLiteral
        }

        /** The in-quote text before the caret, excluding the surrounding quote. */
        private fun inQuotePrefix(stringLiteral: StringLiteralExpression, caretOffset: Int): String? {
            val caretInLiteral = caretOffset - stringLiteral.textRange.startOffset
            val text = stringLiteral.text
            // index 0 is the opening quote; require the caret to sit past it.
            if (caretInLiteral < 1 || caretInLiteral > text.length) return null
            return text.substring(1, caretInLiteral)
        }
    }

    companion object {
        private val TEMPLATE_FUNCTIONS = setOf("setLayout", "render", "extends", "include")
    }
}
