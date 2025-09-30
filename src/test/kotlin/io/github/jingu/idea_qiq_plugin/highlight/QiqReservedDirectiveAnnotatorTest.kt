package io.github.jingu.idea_qiq_plugin.highlight

import io.github.jingu.idea_qiq_plugin.util.QiqUtil
import io.github.jingu.idea_qiq_plugin.util.ReservedDirectiveSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QiqReservedDirectiveAnnotatorTest {

    @Test
    fun findsExtendsAtStart() {
        val span = QiqUtil.findReservedDirectiveSpan("extends('layout')")
        assertEquals(ReservedDirectiveSpan(0, 7), span)
    }

    @Test
    fun findsExtendsWithWhitespace() {
        val span = QiqUtil.findReservedDirectiveSpan("  extends  ('layout')")
        assertEquals(ReservedDirectiveSpan(2, 7), span)
    }

    @Test
    fun ignoresNonDirective() {
        val span = QiqUtil.findReservedDirectiveSpan("render('partial')")
        assertNull(span)
    }

    @Test
    fun requiresParenthesis() {
        val span = QiqUtil.findReservedDirectiveSpan("extends 'layout'")
        assertNull(span)
    }
}
