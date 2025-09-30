package io.github.jingu.idea_qiq_plugin.highlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QiqReservedDirectiveAnnotatorTest {

    @Test
    fun findsExtendsAtStart() {
        val span = QiqReservedDirectiveAnnotator.findReservedDirectiveSpan("extends('layout')")
        assertEquals(QiqReservedDirectiveAnnotator.Span(0, 7), span)
    }

    @Test
    fun findsExtendsWithWhitespace() {
        val span = QiqReservedDirectiveAnnotator.findReservedDirectiveSpan("  extends  ('layout')")
        assertEquals(QiqReservedDirectiveAnnotator.Span(2, 7), span)
    }

    @Test
    fun ignoresNonDirective() {
        val span = QiqReservedDirectiveAnnotator.findReservedDirectiveSpan("render('partial')")
        assertNull(span)
    }

    @Test
    fun requiresParenthesis() {
        val span = QiqReservedDirectiveAnnotator.findReservedDirectiveSpan("extends 'layout'")
        assertNull(span)
    }
}
