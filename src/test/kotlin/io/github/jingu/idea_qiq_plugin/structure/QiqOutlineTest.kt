package io.github.jingu.idea_qiq_plugin.structure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiqOutlineTest {

    private fun build(text: String) = QiqOutline.build(text)

    @Test
    fun nestsBlocksAndLabelsSectionsByName() {
        val text = """
            {{ setLayout('layout/base') }}
            {{ setSection('content') }}
            {{ if (${'$'}x): }}
            <p>hi</p>
            {{ endif }}
            {{ endSection() }}
        """.trimIndent()

        val roots = build(text)
        // Top level: setLayout directive, then the section.
        assertEquals(2, roots.size)
        assertEquals("setLayout('layout/base')", roots[0].label)
        assertEquals("section 'content'", roots[1].label)

        // The section contains the if-block.
        val ifNode = roots[1].children.single()
        assertEquals("if (${'$'}x)", ifNode.label)
        assertTrue(ifNode.children.isEmpty())

        // Kinds drive the structure-view icons.
        assertEquals(QiqOutlineKind.DIRECTIVE, roots[0].kind)
        assertEquals(QiqOutlineKind.SECTION, roots[1].kind)
        assertEquals(QiqOutlineKind.CONTROL, ifNode.kind)
    }

    @Test
    fun navigationOffsetPointsAtOpeningDelimiter() {
        val text = "{{ setSection('a') }}x{{ endSection() }}"
        val section = build(text).single()
        assertEquals(text.indexOf("{{ setSection"), section.offset)
    }

    @Test
    fun nestedSameKeywordBlocksNestCorrectly() {
        val text = """
            {{ if (${'$'}a): }}
            {{ if (${'$'}b): }}
            inner
            {{ endif }}
            {{ endif }}
        """.trimIndent()

        val outer = build(text).single()
        assertEquals("if (${'$'}a)", outer.label)
        val inner = outer.children.single()
        assertEquals("if (${'$'}b)", inner.label)
    }

    @Test
    fun stripsTrailingSemicolonFromControlLabel() {
        val text = "{{ if (${'$'}x): }}c{{ endif; }}"
        val node = build(text).single()
        assertEquals("if (${'$'}x)", node.label)
    }

    @Test
    fun collapsesMultilineDirectiveLabelToOneLine() {
        // A directive whose arguments span several lines/tabs must render on one line.
        val text = "{{ foreach (${'$'}x->gen(\n\t${'$'}a,\n\t${'$'}b) as ${'$'}i): }}body{{ endforeach }}"
        val node = build(text).single()
        assertEquals("foreach (${'$'}x->gen( ${'$'}a, ${'$'}b) as ${'$'}i)", node.label)
    }

    @Test
    fun extendsIsTopLevel() {
        val text = "{{ extends('layout/main') }}\n<p>body</p>"
        val roots = build(text)
        assertEquals(1, roots.size)
        assertEquals("extends('layout/main')", roots[0].label)
    }

    @Test
    fun topLevelDirectiveHeadMatchesCaseInsensitively() {
        // PHP method calls are case-insensitive: `setlayout` must surface like `setLayout`.
        val text = "{{ setlayout('layout/base') }}\n<p>body</p>"
        val roots = build(text)
        assertEquals(1, roots.size)
        assertEquals(QiqOutlineKind.DIRECTIVE, roots[0].kind)
        assertEquals("setlayout('layout/base')", roots[0].label)
    }

    @Test
    fun unbalancedBlockIsNotShownAsContainer() {
        // No closer => not a balanced block, so it is not an outline container.
        val text = "{{ setLayout('base') }}\n{{ if (${'$'}x): }}\n<p>no end</p>"
        val roots = build(text)
        assertEquals(1, roots.size)
        assertEquals("setLayout('base')", roots[0].label)
    }
}
