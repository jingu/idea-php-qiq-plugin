package io.github.jingu.idea_qiq_plugin.completion

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure in-quote prefix helper that decides what the section
 * name completion matches against. The PSI position gate is covered by
 * QiqSectionCallTest; the VFS-backed candidate listing is exercised manually /
 * by the planned HeavyPlatformTestCase suite (#32).
 */
class QiqSectionNameCompletionContributorTest {

    private fun prefix(text: String, caretInLiteral: Int) =
        QiqSectionNameCompletionContributor.inQuotePrefix(text, caretInLiteral)

    @Test
    fun returnsInQuoteTextBeforeCaret() {
        assertEquals("ab", prefix("'abc'", 3)) // caret after `ab`
        assertEquals("", prefix("'abc'", 1)) // caret right after the opening quote
    }

    @Test
    fun excludesTheClosingQuote() {
        assertEquals("abc", prefix("'abc'", 4)) // caret right before the closing quote
        assertNull(prefix("'abc'", 5)) // caret on/after the closing quote
    }

    @Test
    fun completesAnUnterminatedLiteral() {
        // Still being typed: no closing quote, so the whole tail is the prefix.
        assertEquals("abc", prefix("'abc", 4))
    }

    @Test
    fun handlesDoubleQuotes() {
        assertEquals("ab", prefix("\"abc\"", 3))
    }

    @Test
    fun rejectsCaretAtOrBeforeOpeningQuote() {
        assertNull(prefix("'abc'", 0))
    }

    @Test
    fun rejectsNonStringText() {
        assertNull(prefix("abc", 2)) // no opening quote
        assertNull(prefix("", 0))
    }
}
