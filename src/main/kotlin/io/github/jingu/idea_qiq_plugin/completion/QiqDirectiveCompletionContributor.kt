package io.github.jingu.idea_qiq_plugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost

/**
 * Completes Qiq directive keywords at the head of a plain `{{ | }}` block:
 * control directives (`if`/`foreach`/`for` and their `else`/`end*` partners) and
 * the template API (`setSection`/`getSection`/`setLayout`/`extends`, ...).
 *
 * Fires only at the *head* of a non-print code block — where nothing but
 * whitespace precedes the caret inside the `{{ }}` — so keywords are not offered
 * mid-expression or inside `{{= }}` / `{{h }}` output tags (those already get
 * helper and section-name completion). Call-style directives insert with `()` and
 * the caret between the parens, mirroring [QiqHelperCompletionContributor].
 */
class QiqDirectiveCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            // Broad position pattern; the real gating (Qiq file + directive-head
            // position) happens in the provider where the injection is known.
            PlatformPatterns.psiElement(),
            DirectiveCompletionProvider(),
        )
    }

    private class DirectiveCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            if (!QiqInjectionSupport.isInQiqFile(position)) return

            val ilm = InjectedLanguageManager.getInstance(position.project)
            val host = ilm.getInjectionHost(position) as? QiqCodeHost ?: return
            // Output tags (`{{= }}`, `{{h }}`, ...) take an expression, not a
            // control directive; leave them to helper / section-name completion.
            if (host.isPrintLike()) return

            val caretInHost = ilm.injectedToHost(position, position.textRange.startOffset) - host.textRange.startOffset
            if (!isDirectiveHead(host.text, caretInHost)) return

            // Keywords are case-insensitive (PHP keyword / method semantics), like
            // the built-in PHP completion.
            val sink = result.caseInsensitive()
            for (directive in DIRECTIVES) {
                var element = LookupElementBuilder.create(directive.keyword).withTypeText("Qiq directive", true)
                if (directive.call) element = element.withInsertHandler(CALL_INSERT_HANDLER)
                sink.addElement(element)
            }
        }
    }

    /** A completable directive keyword. [call] directives are written `name(...)`. */
    private data class Directive(val keyword: String, val call: Boolean)

    companion object {
        /**
         * True when the caret sits at the directive head of a `{{ }}` block: only
         * whitespace precedes it inside the block content ([caretInHost] is the
         * caret offset within the host text). Pure, unit-tested.
         */
        fun isDirectiveHead(hostText: String, caretInHost: Int): Boolean =
            caretInHost in 0..hostText.length && hostText.substring(0, caretInHost).isBlank()

        // The directive vocabulary. Control directives (if/foreach/for + else/end*)
        // are bare keywords; the template API (set/get/has Section, set/end Block,
        // setLayout, extends) are call-style. Kept aligned with the opener/closer
        // set in io.github.jingu.idea_qiq_plugin.block.QiqBlockType.
        private val DIRECTIVES: List<Directive> = listOf(
            Directive("if", call = false),
            Directive("elseif", call = false),
            Directive("else", call = false),
            Directive("endif", call = false),
            Directive("foreach", call = false),
            Directive("endforeach", call = false),
            Directive("for", call = false),
            Directive("endfor", call = false),
            Directive("setSection", call = true),
            Directive("appendSection", call = true),
            Directive("prependSection", call = true),
            Directive("getSection", call = true),
            Directive("hasSection", call = true),
            Directive("endSection", call = true),
            Directive("setBlock", call = true),
            Directive("getBlock", call = true),
            Directive("endBlock", call = true),
            Directive("setLayout", call = true),
            Directive("extends", call = true),
        )

        /** Keywords offered, for tests. */
        val keywords: List<String> get() = DIRECTIVES.map { it.keyword }

        // Append `()` and place the caret between the parens, unless a `(` already
        // follows (re-completing an existing call). Mirrors QiqHelperCompletionContributor.
        private val CALL_INSERT_HANDLER = InsertHandler<LookupElement> { context, _ ->
            val document = context.document
            val tailOffset = context.tailOffset
            val alreadyHasParen = tailOffset < document.charsSequence.length &&
                document.charsSequence[tailOffset] == '('
            if (!alreadyHasParen) {
                document.insertString(tailOffset, "()")
            }
            context.editor.caretModel.moveToOffset(tailOffset + 1)
        }
    }
}
