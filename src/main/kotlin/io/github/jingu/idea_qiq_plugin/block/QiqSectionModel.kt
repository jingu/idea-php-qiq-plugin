package io.github.jingu.idea_qiq_plugin.block

import com.intellij.openapi.util.TextRange

/**
 * A section/block name *definition* — a `setSection('name')` / `setBlock('name')`
 * directive — found in raw template text.
 *
 * [nameRange] is the span of the name itself (the text inside the quotes), so a
 * reference can navigate straight to the name rather than the whole directive.
 */
data class QiqSectionDef(
    val name: String,
    val type: QiqBlockType,
    val nameRange: TextRange,
)

/**
 * Extracts Qiq section/block *name* definitions from raw template text.
 *
 * Text-based and pure (no PSI), mirroring [QiqBlockModel]: it reuses the same
 * directive scan, so the `setSection`/`setBlock` recognition stays in one place.
 * Only the call-style openers carry a name; `getSection`/`getBlock` (the readers)
 * are handled by the contributors, not here.
 */
object QiqSectionModel {

    /**
     * Every `setSection`/`setBlock` name definition in [text], in document order.
     * Directives with no quoted name, or an empty name, are skipped — an empty
     * name is not a usable symbol.
     */
    fun definitions(text: CharSequence): List<QiqSectionDef> {
        val result = ArrayList<QiqSectionDef>()
        for (directive in QiqBlockModel.scanDirectives(text)) {
            val type = QiqBlockModel.openerTypeOf(directive.inner) ?: continue
            if (type != QiqBlockType.SECTION && type != QiqBlockType.BLOCK) continue
            val nameRange = firstQuotedRange(text, directive.range) ?: continue
            if (nameRange.isEmpty) continue
            result.add(QiqSectionDef(text.substring(nameRange.startOffset, nameRange.endOffset), type, nameRange))
        }
        return result
    }

    /** The defined names of [type] in [text]. */
    fun definedNames(text: CharSequence, type: QiqBlockType): Set<String> =
        definitions(text).asSequence().filter { it.type == type }.map { it.name }.toSet()

    /**
     * The content span (excluding quotes) of the first single- or double-quoted
     * string inside the directive [range] of [text], or null if there is none or
     * the quote is unterminated.
     */
    private fun firstQuotedRange(text: CharSequence, range: TextRange): TextRange? {
        var i = range.startOffset
        val end = minOf(range.endOffset, text.length)
        while (i < end) {
            val c = text[i]
            if (c == '\'' || c == '"') {
                val contentStart = i + 1
                var j = contentStart
                while (j < end && text[j] != c) j++
                if (j >= end) return null // unterminated quote
                return TextRange(contentStart, j)
            }
            i++
        }
        return null
    }
}
