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
    val requiresColon: Boolean,
) {
    IF("if", "endif", true),
    FOREACH("foreach", "endforeach", true),
    FOR("for", "endfor", true),
    SECTION("setSection", "endSection", false),
    BLOCK("setBlock", "endBlock", false);

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
                val openStart = i
                val openEnd = closeStart + 2
                val inner = text.subSequence(i + 2, closeStart).toString().trim()
                accept(inner, TextRange(openStart, openEnd), openers, result)
                i = openEnd
            } else {
                i++
            }
        }

        result.sortBy { it.open.startOffset }
        return result
    }

    private fun accept(
        inner: String,
        delimiter: TextRange,
        openers: ArrayDeque<Pending>,
        result: MutableList<QiqBlockRange>,
    ) {
        val head = inner.takeWhile { !it.isWhitespace() && it != '(' && it != ':' }

        val openType = QiqBlockType.forOpenHead(head)
        // Every opener is a call/condition form, so it must contain '(' — this rejects
        // bare `{{ if $x: }}` / `{{ setSection 'a' }}`. if/foreach/for additionally
        // require the trailing alternative-syntax colon; testing the trailing colon
        // (not any colon) keeps a ternary `?:` in the condition from being mistaken
        // for one.
        if (openType != null && '(' in inner && (!openType.requiresColon || inner.trimEnd().endsWith(':'))) {
            openers.addLast(Pending(openType, delimiter))
            return
        }

        val closeType = QiqBlockType.forCloseHead(head) ?: return
        // setSection/setBlock are call-style: only an empty-arg call closes them
        // (`{{ endSection() }}`, optional trailing `;`). Anything else — no parens,
        // or arguments — is invalid and must not pair.
        if ((closeType == QiqBlockType.SECTION || closeType == QiqBlockType.BLOCK) &&
            !isEmptyArgClose(inner, closeType)
        ) {
            return
        }
        val matchIndex = openers.indexOfLast { it.type == closeType }
        if (matchIndex < 0) return // unmatched closer

        val opener = openers[matchIndex]
        // Drop the matched opener and any inner blocks left unclosed above it.
        while (openers.size > matchIndex) openers.removeLast()
        result.add(QiqBlockRange(closeType, opener.delimiter, delimiter))
    }

    /** True if [inner] is an empty-arg call closer, e.g. `endSection()` / `endBlock() ;`. */
    private fun isEmptyArgClose(inner: String, type: QiqBlockType): Boolean =
        Regex("""(?i)${Regex.escape(type.closeHead)}\s*\(\s*\)\s*;?""").matches(inner)

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
