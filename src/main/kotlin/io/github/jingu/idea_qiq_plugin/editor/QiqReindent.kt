package io.github.jingu.idea_qiq_plugin.editor

import io.github.jingu.idea_qiq_plugin.block.QiqBlockModel

/**
 * Computes the target leading indentation of every line of a Qiq template.
 *
 * Qiq has no nested PSI, so this is a single text pass that tracks two stacks at
 * once — HTML tag depth and Qiq block depth (`{{ if }}`…`{{ endif }}`, foreach,
 * for, setSection, setBlock) — and indents each line to the combined depth. The
 * classic reindent rule applies: a line that *begins* with a closer (`</div>`,
 * `{{ endif }}`, `{{ else }}`) dedents one level for its own line.
 *
 * Multi-line regions are handled by kind:
 * - A multi-line `{{ … }}` directive (e.g. a `render([ … ])` whose array spans
 *   several lines) has its interior indented one level past the opening line and
 *   its closing `}} ` line aligned with the opening line.
 * - A `<?php … ?>` island and `<!-- … -->` comment keep their interior verbatim,
 *   since reflowing PHP/comment bodies is out of scope.
 * HTML inside any of these (and inside `{{ … }}`) is masked so it does not perturb
 * the tag count.
 *
 * Pure and text-based so the indentation logic is unit-tested without the platform.
 */
object QiqReindent {

    /** Sentinel in [computeLineIndents]'s result: leave the line's indent verbatim. */
    const val LEAVE_AS_IS = -1

    // Elements that never have a closing tag, so they must not push HTML depth.
    private val VOID_ELEMENTS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    )

    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9-]*)([^>]*?)(/?)>")
    private val THIS_RECEIVER = Regex("^\\\$this\\s*->\\s*")
    // Require an identifier boundary after the bare `end*` keywords so a directive
    // like `endifoo()` is not mistaken for `endif` (matching QiqBlockModel's stricter
    // head parsing). The `(…)` form of endsection/endblock already ends on a paren.
    private val CLOSER = Regex(
        "(?i)^(?:end(?:if|foreach|for)(?![A-Za-z0-9_])|endsection\\s*\\(\\s*\\)|endblock\\s*\\(\\s*\\))"
    )
    private val ELSE_LIKE = Regex("(?i)^(?:else|elseif)\\b")

    private enum class Directive { OPEN, CLOSE, DEDENT, NEUTRAL }

    private enum class Role { NORMAL, QIQ_INTERIOR, QIQ_CLOSE, PRESERVE }

    /** A `[start, end)` half-open opaque span; [qiq] marks a `{{ … }}` directive. */
    private data class Span(val start: Int, val end: Int, val qiq: Boolean)

    /**
     * The target indent width (in spaces) for each line of [text], using
     * [indentSize] spaces per level. Index `i` is line `i` (0-based, in document
     * order).
     *
     * A value of `-1` means "leave this line's existing indentation untouched":
     * the interiors of `<?php … ?>` islands and `<!-- … -->` comments are preserved
     * verbatim (including tab vs. space), so callers must skip rewriting those lines
     * rather than materialise the width as spaces.
     */
    fun computeLineIndents(text: CharSequence, indentSize: Int): IntArray {
        val lineStarts = lineStartOffsets(text)
        val n = lineStarts.size
        val result = IntArray(n)

        val opaque = opaqueSpans(text)
        val masked = maskedForHtml(text, opaque)

        val net = IntArray(n)
        val dedent = BooleanArray(n)
        classifyQiq(text, lineStarts, net, dedent, opaque.filter { !it.qiq })
        classifyHtml(masked, lineStarts, net, dedent)

        // Assign each continuation line a role and the multi-line span it belongs
        // to; record where each multi-line span opens so interiors can offset from
        // the opening line's computed indent.
        val role = Array(n) { Role.NORMAL }
        val spanOf = IntArray(n) { -1 }
        val spanOpensAtLine = IntArray(n) { -1 }
        val multi = opaque.filter { lineOf(lineStarts, it.start) != lineOf(lineStarts, it.end - 1) }
        for ((idx, span) in multi.withIndex()) {
            val startLine = lineOf(lineStarts, span.start)
            val endLine = lineOf(lineStarts, span.end - 1)
            spanOpensAtLine[startLine] = idx
            for (line in startLine + 1..endLine) {
                spanOf[line] = idx
                role[line] = when {
                    !span.qiq -> Role.PRESERVE
                    line == endLine -> Role.QIQ_CLOSE
                    else -> Role.QIQ_INTERIOR
                }
            }
        }

        val openIndent = IntArray(multi.size)
        var depth = 0
        for (i in 0 until n) {
            val lineStart = lineStarts[i]
            val lineEnd = lineEnd(text, lineStarts, i)
            // A blank line normalises to no indent (0) so Reformat strips stray
            // whitespace — except inside a preserved span, where a whitespace-only
            // line may be significant (e.g. a heredoc body) and is kept verbatim.
            if (role[i] != Role.PRESERVE && isBlank(text, lineStart, lineEnd)) {
                result[i] = 0
                continue
            }
            when (role[i]) {
                Role.QIQ_INTERIOR -> result[i] = openIndent[spanOf[i]] + indentSize
                Role.QIQ_CLOSE -> result[i] = openIndent[spanOf[i]]
                Role.PRESERVE -> result[i] = LEAVE_AS_IS
                Role.NORMAL -> {
                    val level = (depth - if (dedent[i]) 1 else 0).coerceAtLeast(0)
                    result[i] = level * indentSize
                    if (spanOpensAtLine[i] >= 0) openIndent[spanOpensAtLine[i]] = result[i]
                    depth = (depth + net[i]).coerceAtLeast(0)
                }
            }
        }
        return result
    }

    private fun lineStartOffsets(text: CharSequence): IntArray {
        val starts = ArrayList<Int>()
        starts.add(0)
        for (i in text.indices) {
            if (text[i] == '\n') starts.add(i + 1)
        }
        return starts.toIntArray()
    }

    /** `{{ … }}` directives, `<?php … ?>`, and `<!-- … -->` comments, by start. */
    private fun opaqueSpans(text: CharSequence): List<Span> {
        // PHP islands and HTML comments first: a `{{ … }}` that falls inside one of
        // them is opaque text (a string/heredoc/comment), not a Qiq directive, so it
        // must not be registered as its own Qiq span — otherwise a multi-line
        // `{{ … }}` nested there would later claim QIQ_INTERIOR/QIQ_CLOSE roles and
        // reindent lines inside the island.
        val nonQiq = ArrayList<Span>()
        addDelimited(text, "<?php", "?>", nonQiq)
        addDelimited(text, "<?=", "?>", nonQiq)
        addDelimited(text, "<!--", "-->", nonQiq)

        val spans = ArrayList<Span>(nonQiq)
        for (directive in QiqBlockModel.scanDirectives(text)) {
            val start = directive.range.startOffset
            if (nonQiq.any { start >= it.start && start < it.end }) continue
            spans.add(Span(start, directive.range.endOffset, qiq = true))
        }
        return spans.sortedBy { it.start }
    }

    private fun addDelimited(text: CharSequence, open: String, close: String, out: MutableList<Span>) {
        var i = 0
        while (true) {
            val start = indexOf(text, open, i) ?: break
            val end = indexOf(text, close, start + open.length)
            val stop = if (end == null) text.length else end + close.length
            out.add(Span(start, stop, qiq = false))
            i = stop
        }
    }

    /** A copy of [text] with opaque spans blanked (newlines kept) for HTML scanning. */
    private fun maskedForHtml(text: CharSequence, opaque: List<Span>): CharSequence {
        val chars = CharArray(text.length) { text[it] }
        for (span in opaque) {
            for (j in span.start until minOf(span.end, chars.size)) {
                if (chars[j] != '\n') chars[j] = ' '
            }
        }
        return String(chars)
    }

    private fun classifyQiq(
        text: CharSequence,
        lineStarts: IntArray,
        net: IntArray,
        dedent: BooleanArray,
        opaqueNonQiq: List<Span>,
    ) {
        for (directive in QiqBlockModel.scanDirectives(text)) {
            // A `{{ … }}` sequence inside a `<?php … ?>` island or `<!-- … -->`
            // comment is PHP/comment text, not a real directive; it must not move
            // block depth. HTML scanning already masks these spans, so skip them
            // here for parity.
            val start = directive.range.startOffset
            if (opaqueNonQiq.any { start >= it.start && start < it.end }) continue
            val kind = directiveKind(directive.inner) ?: continue
            val line = lineOf(lineStarts, directive.range.startOffset)
            when (kind) {
                Directive.OPEN -> net[line]++
                Directive.CLOSE -> net[line]--
                Directive.DEDENT -> Unit // else/elseif: realigns its own line, no net change
                Directive.NEUTRAL -> continue
            }
            if ((kind == Directive.CLOSE || kind == Directive.DEDENT) &&
                isLineLead(text, lineStarts, line, directive.range.startOffset)
            ) {
                dedent[line] = true
            }
        }
    }

    private fun directiveKind(inner: String): Directive? {
        if (QiqBlockModel.openerTypeOf(inner) != null) return Directive.OPEN
        val normalized = inner.replaceFirst(THIS_RECEIVER, "").trimStart()
        if (CLOSER.containsMatchIn(normalized)) return Directive.CLOSE
        if (ELSE_LIKE.containsMatchIn(normalized)) return Directive.DEDENT
        return Directive.NEUTRAL
    }

    private fun classifyHtml(masked: CharSequence, lineStarts: IntArray, net: IntArray, dedent: BooleanArray) {
        for (match in TAG.findAll(masked)) {
            val isClose = match.groupValues[1] == "/"
            val name = match.groupValues[2].lowercase()
            val selfClosing = match.groupValues[4] == "/" || name in VOID_ELEMENTS
            val line = lineOf(lineStarts, match.range.first)
            when {
                isClose -> {
                    net[line]--
                    if (isLineLead(masked, lineStarts, line, match.range.first)) dedent[line] = true
                }
                selfClosing -> Unit
                else -> net[line]++
            }
        }
    }

    /** True if [offset] is the first non-whitespace position of its line. */
    private fun isLineLead(text: CharSequence, lineStarts: IntArray, line: Int, offset: Int): Boolean =
        firstNonWs(text, lineStarts[line], lineEnd(text, lineStarts, line)) == offset

    private fun lineEnd(text: CharSequence, lineStarts: IntArray, line: Int): Int =
        if (line + 1 < lineStarts.size) lineStarts[line + 1] else text.length

    private fun firstNonWs(text: CharSequence, start: Int, end: Int): Int {
        var i = start
        while (i < end && (text[i] == ' ' || text[i] == '\t')) i++
        return i
    }

    private fun isBlank(text: CharSequence, start: Int, end: Int): Boolean {
        for (i in start until end) {
            val c = text[i]
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') return false
        }
        return true
    }

    private fun lineOf(lineStarts: IntArray, offset: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun indexOf(text: CharSequence, needle: String, from: Int): Int? {
        val idx = text.indexOf(needle, from, ignoreCase = true)
        return if (idx < 0) null else idx
    }
}
