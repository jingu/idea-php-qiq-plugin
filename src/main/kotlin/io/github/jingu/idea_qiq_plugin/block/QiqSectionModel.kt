package io.github.jingu.idea_qiq_plugin.block

import com.intellij.openapi.util.TextRange

/**
 * A section name *definition* — a `setSection`/`appendSection`/`prependSection`
 * directive — found in raw template text. [head] is the directive name as written
 * (so a navigation target can label itself accurately), and [nameRange] is the span
 * of the name itself (inside the quotes), so a reference can navigate straight to it.
 */
data class QiqSectionDef(
    val name: String,
    val head: String,
    val nameRange: TextRange,
)

/**
 * A section name *usage* — a `getSection('name')` / `hasSection('name')` reader —
 * found in raw template text. [head] is the reader's name (`getSection` /
 * `hasSection`) so a navigation target can label itself accurately.
 */
data class QiqSectionUsage(
    val name: String,
    val head: String,
    val nameRange: TextRange,
)

/**
 * Extracts Qiq section *name* definitions and usages from raw template text.
 *
 * Sections are Qiq's named, dictionary-style captures: `setSection('x')` defines,
 * `getSection('x')` / `hasSection('x')` read. Blocks (`setBlock`/`getBlock`) are a
 * separate, inheritance-style mechanism whose `getBlock()` takes no name, so there
 * is no by-name block reader to navigate — block names are handled only
 * structurally by [QiqBlockModel] (folding / structure view / pairing).
 *
 * Text-based and pure (no PSI), mirroring [QiqBlockModel]: it reuses the same
 * directive scan so the directive recognition stays in one place.
 */
object QiqSectionModel {

    // A reader is a bare call or a `$this->` call only — never a call on another
    // receiver (`$obj->getSection(...)`), matching the PSI gate in QiqSectionCall.
    // The leading non-capturing group consumes an optional `$this->`; its bare
    // alternative is a zero-width lookbehind rejecting a preceding member-access
    // (`->`/`::`) or identifier char. Groups stay 1=head, 2=quote, 3=name.
    private val READER = Regex("(?i)(?:\\\$this\\s*->\\s*|(?<![-:>\\w\$]))(getSection|hasSection)\\s*\\(\\s*(['\"])(.*?)\\2")

    /**
     * Every section name definition in [text], in document order — `setSection`,
     * `appendSection`, and `prependSection` all define a section (they differ only in
     * how Qiq merges content). Directives with no quoted name, or an empty name, are
     * skipped.
     */
    fun definitions(text: CharSequence): List<QiqSectionDef> {
        val result = ArrayList<QiqSectionDef>()
        for (directive in QiqBlockModel.scanDirectives(text)) {
            if (QiqBlockModel.openerTypeOf(directive.inner) != QiqBlockType.SECTION) continue
            val nameRange = firstArgQuotedRange(text, directive.range) ?: continue
            if (nameRange.isEmpty) continue
            result.add(QiqSectionDef(text.substring(nameRange.startOffset, nameRange.endOffset), directive.head, nameRange))
        }
        return result
    }

    /**
     * Every `getSection`/`hasSection` name usage in [text], in document order.
     * Matched within each `{{ ... }}` directive so plain text is not scanned.
     * Only bare or `$this->` reads count (not `$obj->getSection(...)`); usages with
     * an empty name are skipped.
     */
    fun usages(text: CharSequence): List<QiqSectionUsage> {
        val result = ArrayList<QiqSectionUsage>()
        for (directive in QiqBlockModel.scanDirectives(text)) {
            val end = minOf(directive.range.endOffset, text.length)
            val sub = text.subSequence(directive.range.startOffset, end)
            for (match in READER.findAll(sub)) {
                val head = if (match.groupValues[1].equals("hasSection", ignoreCase = true)) "hasSection" else "getSection"
                val nameGroup = match.groups[3] ?: continue
                if (nameGroup.value.isEmpty()) continue
                val start = directive.range.startOffset + nameGroup.range.first
                result.add(QiqSectionUsage(nameGroup.value, head, TextRange(start, directive.range.startOffset + nameGroup.range.last + 1)))
            }
        }
        return result
    }

    /**
     * The content span (excluding quotes) of the call's *first argument* in the
     * directive [range] of [text], when that argument is a plain quoted string —
     * else null. Requiring the first non-whitespace token after `(` to be a quote
     * keeps a non-literal or non-first argument (`setSection($name, 'x')`,
     * `setSection($c ? 'a' : 'b')`) from being mistaken for a section name.
     */
    private fun firstArgQuotedRange(text: CharSequence, range: TextRange): TextRange? {
        val end = minOf(range.endOffset, text.length)
        var i = range.startOffset
        while (i < end && text[i] != '(') i++
        if (i >= end) return null
        i++ // past '('
        while (i < end && text[i].isWhitespace()) i++
        if (i >= end) return null
        val quote = text[i]
        if (quote != '\'' && quote != '"') return null // first argument is not a plain string literal
        val contentStart = i + 1
        var j = contentStart
        while (j < end && text[j] != quote) j++
        if (j >= end) return null // unterminated quote
        return TextRange(contentStart, j)
    }
}
