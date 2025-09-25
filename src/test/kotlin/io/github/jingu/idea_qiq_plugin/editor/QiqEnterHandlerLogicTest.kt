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

        val openOffset = text.indexOf("}}") + 2
        val hasCloser = handler.hasMatchingCloser(text, openOffset, "setSection", "endSection()")

        assertTrue(hasCloser)
    }

    @Test
    fun reportsMissingCloser() {
        val text = """
            {{ setSection('header') }}
            <p>content</p>
        """.trimIndent()

        val openOffset = text.indexOf("}}") + 2
        val hasCloser = handler.hasMatchingCloser(text, openOffset, "setSection", "endSection()")

        assertFalse(hasCloser)
    }
}
