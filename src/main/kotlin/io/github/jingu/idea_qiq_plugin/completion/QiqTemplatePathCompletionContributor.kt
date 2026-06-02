package io.github.jingu.idea_qiq_plugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * Completes template paths in the first string argument of Qiq's
 * template-referencing calls — `setLayout('...')`, `render('...')`,
 * `extends('...')`, `include('...')` — inside Qiq templates.
 *
 * Candidates are the template files found under
 * [QiqSettingsService.resolveTemplateRoots] for the current file, listed as
 * root-relative paths with the Qiq extension stripped (so `layout/base.qiq.php`
 * is offered as `layout/base`). Pairs with the existing Go to Declaration on
 * the same arguments.
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

            val settings = QiqSettingsService.getInstance(project)
            val extensions = settings.state.candidateExtensions

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

            // Relative paths, resolved against the directory the current template
            // lives in (a bare `partial/...` as written without a leading slash).
            for (root in settings.resolveTemplateRoots(contextFile)) {
                val rel = LinkedHashSet<String>()
                collectTemplatePaths(root, root, extensions, rel)
                rel.forEach(::offer)
            }

            // Root-absolute paths from the template base (`/layout/base`) — the
            // form layouts and shared partials are usually referenced by. Offered
            // with the leading `/` so the slash is part of the completed text;
            // PlainPrefixMatcher keeps `/l` matching only `/layout/...`.
            settings.resolveTemplateBase(contextFile)?.let { base ->
                val abs = LinkedHashSet<String>()
                collectTemplatePaths(base, base, extensions, abs)
                abs.forEach { offer("/$it") }
            }
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

        private fun collectTemplatePaths(
            dir: VirtualFile,
            root: VirtualFile,
            extensions: List<String>,
            out: MutableSet<String>,
        ) {
            if (out.size >= MAX_CANDIDATES) return
            for (child in dir.children) {
                ProgressManager.checkCanceled()
                if (out.size >= MAX_CANDIDATES) return
                if (child.isDirectory) {
                    collectTemplatePaths(child, root, extensions, out)
                } else {
                    val relative = VfsUtilCore.getRelativePath(child, root) ?: continue
                    stripTemplateExtension(relative, extensions)?.let(out::add)
                }
            }
        }
    }

    companion object {
        private const val MAX_CANDIDATES = 2000
        private val TEMPLATE_FUNCTIONS = setOf("setLayout", "render", "extends", "include")

        /**
         * Strip the first matching Qiq [extensions] entry from [relativePath],
         * or return null when the file is not a template. [extensions] is
         * checked in order, so list longer suffixes first (e.g. `.qiq.php`
         * before `.php`) to avoid leaving a dangling `.qiq`.
         *
         * Pure helper, unit-tested independently of the VFS walk.
         */
        fun stripTemplateExtension(relativePath: String, extensions: List<String>): String? {
            for (ext in extensions) {
                if (relativePath.endsWith(ext, ignoreCase = true)) {
                    return relativePath.dropLast(ext.length)
                }
            }
            return null
        }
    }
}
