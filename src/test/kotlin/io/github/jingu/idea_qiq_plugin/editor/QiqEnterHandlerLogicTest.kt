package io.github.jingu.idea_qiq_plugin.editor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QiqEnterHandlerLogicTest {

    private val handler = QiqEnterHandler()

    @Test
    fun detectsExistingCloserForSection() {
        val text = """
            {{ setSection('header') }}
            <p>content</p>
            {{ endSection() }}
        """.trimIndent()

        assertTrue(handler.isOpenerClosed(text, text.indexOf("{{ setSection")))
    }

    @Test
    fun reportsMissingCloser() {
        val text = """
            {{ setSection('header') }}
            <p>content</p>
        """.trimIndent()

        assertFalse(handler.isOpenerClosed(text, text.indexOf("{{ setSection")))
    }

    @Test
    fun semicolonTerminatedCloserCountsAsClosed() {
        // Regression: {{ endif; }} must be recognised as the closer so Enter does
        // not insert a duplicate {{ endif }}.
        val text = "{{ if (\$x): }}\nbody\n{{ endif; }}"

        assertTrue(handler.isOpenerClosed(text, text.indexOf("{{ if")))
    }
}
