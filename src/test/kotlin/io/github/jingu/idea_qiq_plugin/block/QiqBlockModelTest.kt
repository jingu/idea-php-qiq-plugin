package io.github.jingu.idea_qiq_plugin.block

import com.intellij.openapi.util.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiqBlockModelTest {

    private fun ranges(text: String) = QiqBlockModel.computeBlockRanges(text)

    /** Convenience: the opener/closer delimiter substrings of a matched block. */
    private fun QiqBlockRange.openText(text: String) = text.substring(open.startOffset, open.endOffset)
    private fun QiqBlockRange.closeText(text: String) = text.substring(close.startOffset, close.endOffset)

    @Test
    fun pairsIfWithEndif() {
        val text = """
            {{ if (${'$'}x): }}
            <p>hi</p>
            {{ endif }}
        """.trimIndent()

        val blocks = ranges(text)
        assertEquals(1, blocks.size)
        assertEquals(QiqBlockType.IF, blocks[0].type)
        assertEquals("{{ if (${'$'}x): }}", blocks[0].openText(text))
        assertEquals("{{ endif }}", blocks[0].closeText(text))
    }

    @Test
    fun ignoresIfWithoutColon() {
        // No alternative-syntax colon => not a block opener.
        val text = "{{ if (${'$'}x) }}<p>hi</p>{{ endif }}"
        assertTrue(ranges(text).isEmpty())
    }

    @Test
    fun pairsForeachAndFor() {
        val text = """
            {{ foreach (${'$'}items as ${'$'}i): }}
            {{ for (${'$'}n = 0; ${'$'}n < 3; ${'$'}n++): }}
            x
            {{ endfor }}
            {{ endforeach }}
        """.trimIndent()

        val blocks = ranges(text)
        assertEquals(2, blocks.size)
        // sorted by opener offset: foreach first, then the inner for
        assertEquals(QiqBlockType.FOREACH, blocks[0].type)
        assertEquals(QiqBlockType.FOR, blocks[1].type)
    }

    @Test
    fun pairsSectionAndBlockCalls() {
        val text = """
            {{ setSection('header') }}
            <h1>title</h1>
            {{ endSection() }}
            {{ setBlock('body') }}
            content
            {{ endBlock() }}
        """.trimIndent()

        val blocks = ranges(text)
        assertEquals(2, blocks.size)
        assertEquals(QiqBlockType.SECTION, blocks[0].type)
        assertEquals(QiqBlockType.BLOCK, blocks[1].type)
    }

    @Test
    fun nestedSameTypeMatchesNearestPartner() {
        val text = """
            {{ if (${'$'}a): }}
            {{ if (${'$'}b): }}
            inner
            {{ endif }}
            {{ endif }}
        """.trimIndent()

        val blocks = ranges(text).sortedBy { it.open.startOffset }
        assertEquals(2, blocks.size)

        val outer = blocks[0]
        val inner = blocks[1]
        // The inner opener's closer is the FIRST endif; the outer's is the second.
        assertTrue(inner.close.startOffset < outer.close.startOffset)
        // Proper nesting: inner block is fully contained in the outer body.
        assertTrue(outer.bodyRange.contains(inner.fullRange))
    }

    @Test
    fun unclosedOpenerProducesNoRange() {
        val text = """
            {{ if (${'$'}x): }}
            <p>no closer here</p>
        """.trimIndent()
        assertTrue(ranges(text).isEmpty())
    }

    @Test
    fun unmatchedCloserProducesNoRange() {
        val text = "<p>stray</p>\n{{ endif }}"
        assertTrue(ranges(text).isEmpty())
    }

    @Test
    fun unclosedOpenerDoesNotSwallowLaterBlocks() {
        // A half-typed `{{ if (` (no `}}`) must not consume the following block:
        // the scanner skips it and still pairs the later setSection/endSection.
        val text = "{{ if (\n{{ setSection('a') }}\nx\n{{ endSection() }}"
        val blocks = ranges(text)
        assertEquals(1, blocks.size)
        assertEquals(QiqBlockType.SECTION, blocks[0].type)
    }

    @Test
    fun elseInsideIfIsNotABoundary() {
        val text = """
            {{ if (${'$'}x): }}
            a
            {{ else: }}
            b
            {{ endif }}
        """.trimIndent()

        val blocks = ranges(text)
        assertEquals(1, blocks.size)
        // The single if/endif spans across the else branch.
        val block = blocks[0]
        assertTrue(block.bodyRange.contains(text.indexOf("{{ else: }}")))
    }

    @Test
    fun ternaryColonDoesNotMakeNonBlockIfABlock() {
        // The colon belongs to the ternary, not to alternative syntax: not a block.
        val withoutAltColon = "{{ if (${'$'}a ? ${'$'}b : ${'$'}c) }}x{{ endif }}"
        assertTrue(ranges(withoutAltColon).isEmpty())

        // The same condition WITH the trailing alternative-syntax colon is a block.
        val withAltColon = "{{ if (${'$'}a ? ${'$'}b : ${'$'}c): }}x{{ endif }}"
        assertEquals(1, ranges(withAltColon).size)
    }

    @Test
    fun callStyleCloserWithoutParensIsNotACloser() {
        // `{{ endSection }}` without parentheses is invalid; the section stays unclosed.
        val text = "{{ setSection('a') }}x{{ endSection }}"
        assertTrue(ranges(text).isEmpty())
    }

    @Test
    fun directiveHeadsMatchCaseInsensitively() {
        // PHP keywords are case-insensitive; uppercase directives still pair.
        val text = "{{ IF (${'$'}x): }}body{{ ENDIF }}"
        val block = ranges(text).single()
        assertEquals(QiqBlockType.IF, block.type)
    }

    @Test
    fun openerWithoutParenthesesIsNotABlock() {
        // All openers are call/condition forms; without '(' they are not blocks.
        assertTrue(ranges("{{ if ${'$'}x: }}body{{ endif }}").isEmpty())
        assertTrue(ranges("{{ setSection 'a' }}body{{ endSection() }}").isEmpty())
    }

    @Test
    fun openerParenthesisMustFollowHead() {
        // A '(' from an inner call doesn't qualify: the opener's own '(' must follow
        // the head, so `{{ if $x && foo(): }}` (no outer parens) is not a block.
        assertTrue(ranges("{{ if ${'$'}x && foo(): }}body{{ endif }}").isEmpty())
    }

    @Test
    fun callStyleCloserMustBeEmptyArgCall() {
        // Only an empty-arg call closes a section/block; arguments are invalid.
        assertTrue(ranges("{{ setSection('a') }}x{{ endSection(${'$'}x) }}").isEmpty())
        // Empty-arg call with a trailing semicolon is accepted.
        assertEquals(1, ranges("{{ setSection('a') }}x{{ endSection(); }}").size)
    }

    @Test
    fun pairsSemicolonTerminatedClosers() {
        // `{{ endif; }}` is syntactically allowed; the trailing semicolon must
        // not prevent the closer from being recognised.
        val text = """
            {{ if (${'$'}x): }}body{{ endif; }}
            {{ foreach (${'$'}xs as ${'$'}x): }}b{{ endforeach; }}
            {{ for (${'$'}i = 0; ${'$'}i < 3; ${'$'}i++): }}c{{ endfor; }}
        """.trimIndent()

        val blocks = ranges(text)
        assertEquals(3, blocks.size)
        assertEquals(setOf(QiqBlockType.IF, QiqBlockType.FOREACH, QiqBlockType.FOR), blocks.map { it.type }.toSet())
    }

    @Test
    fun outputExpressionsAreNotBlocks() {
        val text = "{{= ${'$'}title }} {{h ${'$'}body }} {{ noop() }}"
        assertTrue(ranges(text).isEmpty())
    }

    @Test
    fun blockAtDelimiterResolvesFromOpenerAndCloser() {
        val text = """
            {{ if (${'$'}x): }}
            body
            {{ endif }}
        """.trimIndent()

        val onOpener = QiqBlockModel.blockAtDelimiter(text, text.indexOf("if"))
        val onCloser = QiqBlockModel.blockAtDelimiter(text, text.indexOf("endif"))
        val inBody = QiqBlockModel.blockAtDelimiter(text, text.indexOf("body"))

        assertEquals(QiqBlockType.IF, onOpener?.type)
        // Caret on either delimiter resolves to the same block.
        assertEquals(onOpener, onCloser)
        assertEquals(null, inBody)
    }

    @Test
    fun blockAtDelimiterPicksInnerNestedBlock() {
        val text = """
            {{ if (${'$'}a): }}
            {{ if (${'$'}b): }}
            inner
            {{ endif }}
            {{ endif }}
        """.trimIndent()

        // The first endif closes the inner block.
        val firstEndif = text.indexOf("{{ endif }}")
        val block = QiqBlockModel.blockAtDelimiter(text, firstEndif + 3)

        // Its opener is the SECOND `if (` (the inner one), not the outer.
        assertEquals(text.indexOf("if (${'$'}b)") - 3, block?.open?.startOffset)
    }

    @Test
    fun validateAcceptsWellFormedNesting() {
        val text = """
            {{ setSection('a') }}
            {{ if (${'$'}x): }}
            {{ foreach (${'$'}xs as ${'$'}y): }}
            x
            {{ endforeach }}
            {{ endif }}
            {{ endSection() }}
        """.trimIndent()

        assertTrue(QiqBlockModel.validate(text).isEmpty())
    }

    @Test
    fun validateFlagsUnclosedOpener() {
        val text = "{{ if (${'$'}x): }}\n<p>no end</p>"
        val problem = QiqBlockModel.validate(text).single()
        assertEquals(QiqBlockProblem.Kind.UNCLOSED_OPENER, problem.kind)
        assertEquals(QiqBlockType.IF, problem.type)
        assertEquals(text.indexOf("{{ if"), problem.range.startOffset)
    }

    @Test
    fun validateFlagsUnmatchedCloser() {
        val text = "<p>x</p>\n{{ endif }}"
        val problem = QiqBlockModel.validate(text).single()
        assertEquals(QiqBlockProblem.Kind.UNMATCHED_CLOSER, problem.kind)
        assertEquals(QiqBlockType.IF, problem.type)
    }

    @Test
    fun validateFlagsMismatchedCloser() {
        // foreach opened, endif seen: endif matches no open block (mismatch),
        // and the foreach is left unclosed.
        val text = "{{ foreach (${'$'}xs as ${'$'}y): }}\nx\n{{ endif }}"
        val problems = QiqBlockModel.validate(text)
        val mismatch = problems.single { it.kind == QiqBlockProblem.Kind.MISMATCHED_CLOSER }
        assertEquals(QiqBlockType.IF, mismatch.type)
        assertEquals(QiqBlockType.FOREACH, mismatch.expected)
        assertTrue(problems.any { it.kind == QiqBlockProblem.Kind.UNCLOSED_OPENER && it.type == QiqBlockType.FOREACH })
    }

    @Test
    fun validateFlagsInnerUnclosedOnWrongNesting() {
        // The inner foreach is never closed; the endif still closes the if.
        val text = "{{ if (${'$'}x): }}\n{{ foreach (${'$'}xs as ${'$'}y): }}\n{{ endif }}"
        val problems = QiqBlockModel.validate(text)
        assertEquals(1, problems.size)
        assertEquals(QiqBlockProblem.Kind.UNCLOSED_OPENER, problems[0].kind)
        assertEquals(QiqBlockType.FOREACH, problems[0].type)
    }

    @Test
    fun bodyRangeExcludesDelimiters() {
        val text = "{{ if (${'$'}x): }}BODY{{ endif }}"
        val block = ranges(text).single()
        assertEquals(TextRange(text.indexOf("BODY"), text.indexOf("BODY") + "BODY".length), block.bodyRange)
    }
}
