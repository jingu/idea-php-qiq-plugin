package io.github.jingu.idea_qiq_plugin.block

import com.intellij.openapi.util.TextRange
import java.util.Locale

/**
 * The Qiq block directives that come in opener/closer pairs.
 *
 * Shares its directive set with [io.github.jingu.idea_qiq_plugin.editor.QiqEnterHandler]
 * (which auto-closes the same blocks). `if`/`foreach`/`for` use PHP alternative syntax,
 * so an opener is only a block when it is colon-terminated (`{{ if (...): }}`);
 * `setSection` / `setBlock` are call-style openers. The exact opener/closer parsing
 * here is deliberately stricter than the keyword set alone to avoid pairing
 * syntactically invalid directives. Heads are matched case-insensitively, as PHP
 * keywords and method names are.
 */
enum class QiqBlockType(
    val openHead: String,
    val closeHead: String,
    val closeText: String,
    val requiresColon: Boolean,
) {
    IF("if", "endif", "endif", true),
    FOREACH("foreach", "endforeach", "endforeach", true),
    FOR("for", "endfor", "endfor", true),
    SECTION("setSection", "endSection", "endSection()", false),
    BLOCK("setBlock", "endBlock", "endBlock()", false);

    companion object {
        private val byOpenHead = entries.associateBy { it.openHead.lowercase(Locale.ROOT) }
        private val byCloseHead = entries.associateBy { it.closeHead.lowercase(Locale.ROOT) }

        fun forOpenHead(head: String): QiqBlockType? = byOpenHead[head.lowercase(Locale.ROOT)]
        fun forCloseHead(head: String): QiqBlockType? = byCloseHead[head.lowercase(Locale.ROOT)]
    }
}

/**
 * A balanced pair of Qiq block directives.
 *
 * [open] and [close] are the `{{ ... }}` delimiter spans of the opener and closer.
 */
data class QiqBlockRange(
    val type: QiqBlockType,
    val open: TextRange,
    val close: TextRange,
) {
    /** The block body: everything between the opener and closer delimiters. */
    val bodyRange: TextRange get() = TextRange(open.endOffset, close.startOffset)

    /** The whole block, from the start of the opener to the end of the closer. */
    val fullRange: TextRange get() = TextRange(open.startOffset, close.endOffset)
}

/** A structurally invalid block directive found by [QiqBlockModel.validate]. */
data class QiqBlockProblem(
    val kind: Kind,
    /** The offending `{{ ... }}` delimiter span. */
    val range: TextRange,
    /** The offending directive's own block type. */
    val type: QiqBlockType,
    /** For [Kind.MISMATCHED_CLOSER]: the nearest open block the closer failed to match. */
    val expected: QiqBlockType? = null,
) {
    enum class Kind {
        /** An opener that is never closed. */
        UNCLOSED_OPENER,

        /** A closer with no open block at all. */
        UNMATCHED_CLOSER,

        /** A closer whose type matches no open block, while a different block is open. */
        MISMATCHED_CLOSER,
    }
}

/**
 * Pairs Qiq block openers with their closers from raw template text.
 *
 * This is the shared foundation for folding, block matching, and the structure view.
 * It is intentionally text-based (not PSI-based): the lexer flattens `{{ ... }}` into
 * injection-host content, so the `if`/`endif` distinction only survives as text.
 */
object QiqBlockModel {

    /**
     * Scan [text] and return every balanced block pair, ordered by opener offset.
     *
     * Unbalanced directives (an opener with no closer, or a closer with no opener)
     * are dropped rather than paired, so callers never see a broken range. Inner
     * blocks left unclosed when their parent closes are dropped the same way.
     */
    fun computeBlockRanges(text: CharSequence): List<QiqBlockRange> {
        val openers = ArrayDeque<Pending>()
        val result = ArrayList<QiqBlockRange>()

        for (directive in scanDirectives(text)) {
            accept(directive, openers, result)
        }

        result.sortBy { it.open.startOffset }
        return result
    }

    /** A single `{{ ... }}` directive: its delimiter span, [head] keyword and trimmed [inner] text. */
    internal data class Directive(val range: TextRange, val head: String, val inner: String)

    /**
     * Every `{{ ... }}` directive in [text], in document order.
     *
     * Shared by [computeBlockRanges] and the structure view so the delimiter scan
     * and head extraction live in one place.
     */
    internal fun scanDirectives(text: CharSequence): List<Directive> {
        val directives = ArrayList<Directive>()
        var i = 0
        val n = text.length
        while (i < n - 1) {
            if (text[i] == '{' && text[i + 1] == '{') {
                val closeStart = indexOfDoubleBrace(text, i + 2) ?: break
                // If another `{{` opens before this one's `}}`, this opener is unclosed
                // (e.g. a half-typed `{{ if (` mid-edit). Skip it and resume at the inner
                // `{{` so the later, well-formed directives are still detected.
                val nextOpen = indexOfDoubleOpen(text, i + 2)
                if (nextOpen != null && nextOpen < closeStart) {
                    i = nextOpen
                    continue
                }
                val openEnd = closeStart + 2
                val inner = text.subSequence(i + 2, closeStart).toString().trim()
                // The head is taken past any `$this->` so `{{ $this->endSection() }}`
                // yields the same head as the bare `{{ endSection() }}`.
                directives.add(Directive(TextRange(i, openEnd), headOf(stripThisReceiver(inner)), inner))
                i = openEnd
            } else {
                i++
            }
        }
        return directives
    }

    /**
     * The block whose opener or closer delimiter [offset] falls on, if any.
     *
     * Offsets follow IntelliJ's half-open `[start, end)` semantics, so the position
     * right after a `}}` is outside its delimiter: where two delimiters are adjacent
     * (`}}{{`), the boundary offset resolves only to the following block. Used to
     * drive block-pair highlighting.
     */
    fun blockAtDelimiter(text: CharSequence, offset: Int): QiqBlockRange? =
        computeBlockRanges(text).firstOrNull { block ->
            block.open.contains(offset) || block.close.contains(offset)
        }

    /**
     * Report every structurally invalid block directive in [text]: openers that are
     * never closed, closers with no opener, and closers that match no open block
     * while a different block is open. Well-formed nested blocks produce nothing.
     */
    fun validate(text: CharSequence): List<QiqBlockProblem> {
        val openers = ArrayDeque<Pending>()
        val problems = ArrayList<QiqBlockProblem>()

        for (directive in scanDirectives(text)) {
            val openType = openerTypeOf(directive.inner)
            if (openType != null) {
                openers.addLast(Pending(openType, directive.range))
                continue
            }

            val closeType = closeTypeOf(directive) ?: continue
            if (openers.isEmpty()) {
                problems.add(QiqBlockProblem(QiqBlockProblem.Kind.UNMATCHED_CLOSER, directive.range, closeType))
                continue
            }
            if (openers.last().type == closeType) {
                openers.removeLast() // proper match
                continue
            }

            val matchIndex = openers.indexOfLast { it.type == closeType }
            if (matchIndex >= 0) {
                // A matching opener is open deeper down; the inner ones above it are unclosed.
                for (k in openers.indices.reversed()) {
                    if (k <= matchIndex) break
                    problems.add(QiqBlockProblem(QiqBlockProblem.Kind.UNCLOSED_OPENER, openers[k].delimiter, openers[k].type))
                }
                while (openers.size > matchIndex) openers.removeLast()
            } else {
                // No opener of this type anywhere: the closer is wrong for the nearest opener.
                problems.add(
                    QiqBlockProblem(
                        QiqBlockProblem.Kind.MISMATCHED_CLOSER,
                        directive.range,
                        closeType,
                        expected = openers.last().type,
                    ),
                )
            }
        }

        for (opener in openers) {
            problems.add(QiqBlockProblem(QiqBlockProblem.Kind.UNCLOSED_OPENER, opener.delimiter, opener.type))
        }

        return problems.sortedBy { it.range.startOffset }
    }

    private fun accept(
        directive: Directive,
        openers: ArrayDeque<Pending>,
        result: MutableList<QiqBlockRange>,
    ) {
        val openType = openerTypeOf(directive.inner)
        if (openType != null) {
            openers.addLast(Pending(openType, directive.range))
            return
        }

        val closeType = closeTypeOf(directive) ?: return
        val matchIndex = openers.indexOfLast { it.type == closeType }
        if (matchIndex < 0) return // unmatched closer

        val opener = openers[matchIndex]
        // Drop the matched opener and any inner blocks left unclosed above it.
        while (openers.size > matchIndex) openers.removeLast()
        result.add(QiqBlockRange(closeType, opener.delimiter, directive.range))
    }

    /**
     * The block type an opener `{{ ... }}` whose content is [inner] opens, or null if
     * [inner] is not a block opener.
     *
     * Every opener is a call/condition form whose '(' follows the head directly
     * (whitespace allowed); requiring it there rejects bare `{{ if $x: }}` /
     * `{{ setSection 'a' }}` and avoids a false positive when an inner call supplies the
     * '(' (e.g. `{{ if $x && foo(): }}`). if/foreach/for additionally require the
     * trailing alternative-syntax colon; testing the trailing colon (not any colon)
     * keeps a ternary `?:` from being mistaken for one.
     *
     * The single source of truth for "is this a block opener", shared by the block
     * pairing here and [io.github.jingu.idea_qiq_plugin.editor.QiqEnterHandler].
     */
    fun openerTypeOf(inner: String): QiqBlockType? {
        val normalized = stripThisReceiver(inner)
        val head = headOf(normalized)
        return QiqBlockType.forOpenHead(head)?.takeIf {
            normalized.substring(head.length).trimStart().startsWith("(") &&
                (!it.requiresColon || normalized.trimEnd().endsWith(':'))
        }
    }

    /**
     * The block type this directive closes, or null. `setSection`/`setBlock` are
     * call-style: only an empty-arg call (`endSection()` / `endBlock()`, optional
     * trailing `;`) closes them; no parens, or arguments, is invalid and must not pair.
     */
    private fun closeTypeOf(directive: Directive): QiqBlockType? {
        val type = QiqBlockType.forCloseHead(directive.head) ?: return null
        if ((type == QiqBlockType.SECTION || type == QiqBlockType.BLOCK) &&
            !isEmptyArgClose(stripThisReceiver(directive.inner), type)
        ) {
            return null
        }
        return type
    }

    /** True if [inner] is an empty-arg call closer, e.g. `endSection()` / `endBlock() ;`. */
    private fun isEmptyArgClose(inner: String, type: QiqBlockType): Boolean =
        Regex("""(?i)${Regex.escape(type.closeHead)}\s*\(\s*\)\s*;?""").matches(inner)

    /**
     * The leading keyword of a directive's [inner] text. Stops at '(' (call/condition),
     * ':' (alt-syntax opener) or ';' so a semicolon-terminated closer such as
     * `{{ endif; }}` still yields "endif".
     */
    private fun headOf(inner: String): String =
        inner.takeWhile { !it.isWhitespace() && it != '(' && it != ':' && it != ';' }

    private val THIS_RECEIVER = Regex("^\\\$this\\s*->\\s*")

    /**
     * Drops a leading `$this->` so the explicit method form is recognised the same
     * as the bare one: Qiq compiles `{{ setSection('x') }}` to `$this->setSection('x')`,
     * and authors may write either, so `{{ $this->setSection('x') }}` … `{{ $this->endSection() }}`
     * must pair just like the bare form.
     */
    private fun stripThisReceiver(inner: String): String = inner.replaceFirst(THIS_RECEIVER, "")

    private fun indexOfDoubleBrace(text: CharSequence, from: Int): Int? {
        var j = from
        while (j < text.length - 1) {
            if (text[j] == '}' && text[j + 1] == '}') return j
            j++
        }
        return null
    }

    private fun indexOfDoubleOpen(text: CharSequence, from: Int): Int? {
        var j = from
        while (j < text.length - 1) {
            if (text[j] == '{' && text[j + 1] == '{') return j
            j++
        }
        return null
    }

    private data class Pending(val type: QiqBlockType, val delimiter: TextRange)
}
