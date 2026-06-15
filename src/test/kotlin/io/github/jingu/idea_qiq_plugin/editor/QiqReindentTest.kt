package io.github.jingu.idea_qiq_plugin.editor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the pure line-indent computation that backs the Qiq formatter.
 * Indent size is 4 throughout; values are leading-space widths per line.
 */
class QiqReindentTest {

    private fun indents(text: String) = QiqReindent.computeLineIndents(text, 4)

    @Test
    fun indentsBlockBodyAndAlignsCloser() {
        val text = "{{ if (${'$'}x): }}\nbody\n{{ endif }}"
        assertContentEquals(intArrayOf(0, 4, 0), indents(text))
    }

    @Test
    fun nestsQiqBlocksAndHtmlTogether() {
        val text = "{{ if (${'$'}a): }}\n<div>\n<p>x</p>\n</div>\n{{ endif }}"
        // if(0) -> div(1) -> p (2, net 0) -> /div dedent(1) -> endif dedent(0)
        assertContentEquals(intArrayOf(0, 4, 8, 4, 0), indents(text))
    }

    @Test
    fun elseRealignsWithoutChangingDepth() {
        val text = "{{ if (${'$'}x): }}\na\n{{ else: }}\nb\n{{ endif }}"
        assertContentEquals(intArrayOf(0, 4, 0, 4, 0), indents(text))
    }

    @Test
    fun voidAndSelfClosingHtmlDoNotPushDepth() {
        val text = "{{ if (${'$'}x): }}\n<br>\n<img src=\"a\"/>\n<span>y</span>\n{{ endif }}"
        // All body lines stay at one level; none of br/img/self-closing nest.
        assertContentEquals(intArrayOf(0, 4, 4, 4, 0), indents(text))
    }

    @Test
    fun preservesPhpIslandLinesAndKeepsItAtColumnZero() {
        // The island interior is marked LEAVE_AS_IS (-1) so its indentation is kept
        // verbatim; only the `<?php` opener line and the Qiq block are reindented.
        val text = "<?php\n${'$'}x = 1;\n?>\n{{ if (${'$'}a): }}\nx\n{{ endif }}"
        assertContentEquals(intArrayOf(0, -1, -1, 0, 4, 0), indents(text))
    }

    @Test
    fun indentsMultilineDirectiveInteriorPastOpeningLine() {
        // The render([...]) array spans three lines; its interior is indented one
        // level past the opening line and the closing `]) }}` aligns with it.
        //   {{= render([   <- opening at the block body depth (4)
        //       'x' => 1,  <- interior 4 + 4
        //   ]) }}          <- close aligns with opening (4)
        val text = "{{ if (${'$'}a): }}\n{{= render([\n'x' => 1,\n]) }}\n{{ endif }}"
        assertContentEquals(intArrayOf(0, 4, 8, 4, 0), indents(text))
    }

    @Test
    fun keepsPhpIslandInteriorVerbatim() {
        // A <?php ?> island inside a block: the opener indents, but the PHP body is
        // left exactly as written (no reflow).
        val text = "{{ if (${'$'}a): }}\n<?php\n  ${'$'}x = 1;\n?>\n{{ endif }}"
        // opener(0) -> <?php opening line at depth 1 (4); body & ?> kept verbatim (-1).
        assertContentEquals(intArrayOf(0, 4, -1, -1, 0), indents(text))
    }

    @Test
    fun ignoresMultilineDirectiveInsidePhpIsland() {
        // A multi-line `{{= … }}` that lives inside a <?php ?> island (e.g. a heredoc)
        // is opaque PHP text, not a Qiq directive: it must not claim interior/close
        // roles and reindent lines within the island. All island lines stay verbatim.
        val text = "<?php\necho \"{{= foo([\n1,\n]) }}\";\n?>"
        assertContentEquals(intArrayOf(0, -1, -1, -1, -1), indents(text))
    }

    @Test
    fun ignoresDirectiveLikeTextInsidePhpIsland() {
        // A `{{ endif }}` sequence inside a single-line <?php ?> island is PHP
        // string text, not a real directive: it must not close the surrounding
        // `{{ if }}` block and so must not dedent the lines that follow it.
        val text = "{{ if (${'$'}a): }}\n<?php echo '{{ endif }}'; ?>\n<div>x</div>\n{{ endif }}"
        // if(0) -> php island stays in the body (4) -> <div> body (4) -> endif (0).
        assertContentEquals(intArrayOf(0, 4, 4, 0), indents(text))
    }

    @Test
    fun masksHtmlInsideQiqOutputTags() {
        // The {{h ... }} inside the tag must not break the <main> open count.
        val text = "{{ if (${'$'}a): }}\n<main class=\"{{h ${'$'}x }}\">\ntext\n</main>\n{{ endif }}"
        assertContentEquals(intArrayOf(0, 4, 8, 4, 0), indents(text))
    }

    @Test
    fun isIdempotent() {
        val text = "{{ if (${'$'}a): }}\n<div>\n<p>x</p>\n</div>\n{{ endif }}"
        val once = applyIndents(text, indents(text))
        val twice = applyIndents(once, indents(once))
        assertTrue(once == twice, "reindent should be a fixed point")
        assertContentEquals(indents(text), indents(once))
    }

    /**
     * Rewrite each line's leading whitespace to the computed width, mirroring
     * [QiqPostFormatProcessor]: a LEAVE_AS_IS (-1) line is kept exactly as written.
     */
    private fun applyIndents(text: String, indents: IntArray): String {
        val lines = text.split("\n")
        return lines.mapIndexed { i, line ->
            val content = line.trimStart(' ', '\t')
            when {
                indents[i] == QiqReindent.LEAVE_AS_IS -> line
                content.isEmpty() -> ""
                else -> " ".repeat(indents[i]) + content
            }
        }.joinToString("\n")
    }
}
