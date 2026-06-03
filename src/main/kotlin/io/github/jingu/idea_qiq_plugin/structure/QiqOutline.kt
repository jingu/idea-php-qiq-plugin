package io.github.jingu.idea_qiq_plugin.structure

import com.intellij.openapi.util.TextRange
import io.github.jingu.idea_qiq_plugin.block.QiqBlockModel
import io.github.jingu.idea_qiq_plugin.block.QiqBlockType

/** The kind of outline node, used to pick a structure-view icon. */
enum class QiqOutlineKind { DIRECTIVE, SECTION, BLOCK, CONTROL }

/**
 * One node in the Qiq structure outline.
 *
 * [offset] is where clicking the node navigates to (the `{{` of the directive).
 */
data class QiqOutlineNode(
    val label: String,
    val kind: QiqOutlineKind,
    val offset: Int,
    val children: List<QiqOutlineNode>,
)

/**
 * Builds the structure-view outline from raw template text.
 *
 * The outline nests `setSection`/`setBlock` and the control blocks `if`/`foreach`/
 * `for` using the shared [QiqBlockModel] pairing, and surfaces top-level
 * `setLayout(...)` / `extends(...)` directives. Pure and text-based so it can be
 * unit-tested without the platform.
 */
object QiqOutline {

    private val TOP_LEVEL_HEADS = setOf("setLayout", "extends")
    private val WHITESPACE_RUN = Regex("\\s+")

    fun build(text: CharSequence): List<QiqOutlineNode> {
        val directives = QiqBlockModel.scanDirectives(text)
        val innerByStart = directives.associate { it.range.startOffset to it.inner }

        // Containers: balanced block pairs, spanning opener-start .. closer-end.
        val containers = QiqBlockModel.computeBlockRanges(text).map { block ->
            Entry(
                range = block.fullRange,
                node = MutableNode(
                    label(block.type, normalize(innerByStart[block.open.startOffset] ?: "")),
                    kindOf(block.type),
                    block.open.startOffset,
                ),
                isContainer = true,
            )
        }

        // Leaves: standalone setLayout()/extends() directives.
        val leaves = directives
            .filter { it.head in TOP_LEVEL_HEADS }
            .map { Entry(it.range, MutableNode(normalize(it.inner), QiqOutlineKind.DIRECTIVE, it.range.startOffset), isContainer = false) }

        return assemble(containers + leaves)
    }

    /** Nest entries by range containment into a forest, preserving document order. */
    private fun assemble(entries: List<Entry>): List<QiqOutlineNode> {
        val sorted = entries.sortedWith(compareBy({ it.range.startOffset }, { -it.range.endOffset }))
        val roots = ArrayList<MutableNode>()
        val stack = ArrayDeque<Entry>()

        for (entry in sorted) {
            while (stack.isNotEmpty() && !stack.last().range.contains(entry.range)) {
                stack.removeLast()
            }
            if (stack.isEmpty()) roots.add(entry.node) else stack.last().node.children.add(entry.node)
            if (entry.isContainer) stack.addLast(entry)
        }

        return roots.map { it.toImmutable() }
    }

    private fun kindOf(type: QiqBlockType): QiqOutlineKind = when (type) {
        QiqBlockType.SECTION -> QiqOutlineKind.SECTION
        QiqBlockType.BLOCK -> QiqOutlineKind.BLOCK
        QiqBlockType.IF, QiqBlockType.FOREACH, QiqBlockType.FOR -> QiqOutlineKind.CONTROL
    }

    private fun label(type: QiqBlockType, inner: String): String = when (type) {
        QiqBlockType.SECTION -> "section " + (firstStringArg(inner) ?: inner)
        QiqBlockType.BLOCK -> "block " + (firstStringArg(inner) ?: inner)
        QiqBlockType.IF, QiqBlockType.FOREACH, QiqBlockType.FOR -> inner.trimEnd(';', ':', ' ')
    }

    /** Collapse internal newlines/tabs/runs of spaces so a multi-line directive renders on one line. */
    private fun normalize(inner: String): String = inner.replace(WHITESPACE_RUN, " ").trim()

    /** The first single- or double-quoted argument, e.g. `setSection('header')` -> `'header'`. */
    private fun firstStringArg(inner: String): String? {
        val open = inner.indexOfFirst { it == '\'' || it == '"' }
        if (open < 0) return null
        val quote = inner[open]
        val close = inner.indexOf(quote, open + 1)
        if (close < 0) return null
        return inner.substring(open, close + 1)
    }

    private class Entry(val range: TextRange, val node: MutableNode, val isContainer: Boolean)

    private class MutableNode(val label: String, val kind: QiqOutlineKind, val offset: Int) {
        val children = ArrayList<MutableNode>()
        fun toImmutable(): QiqOutlineNode = QiqOutlineNode(label, kind, offset, children.map { it.toImmutable() })
    }
}
