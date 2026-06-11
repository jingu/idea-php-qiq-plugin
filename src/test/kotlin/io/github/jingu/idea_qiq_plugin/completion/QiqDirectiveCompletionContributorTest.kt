package io.github.jingu.idea_qiq_plugin.completion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the pure directive-head gate that decides where keyword completion fires.
 * [hostText] is the `{{ }}` block content; [caretInHost] the caret offset within it.
 */
class QiqDirectiveCompletionContributorTest {

    private fun isHead(hostText: String, caretInHost: Int) =
        QiqDirectiveCompletionContributor.isDirectiveHead(hostText, caretInHost)

    @Test
    fun firesAtEmptyHead() {
        // `{{ | }}` — caret right after the opening brace, nothing typed.
        assertTrue(isHead("", 0))
        assertTrue(isHead("  ", 2))
    }

    @Test
    fun firesWhileTypingAKeyword() {
        // `{{ if| }}` — the dummy identifier starts at offset 1 (after the space),
        // so only the leading whitespace precedes the caret position.
        assertTrue(isHead(" if", 1))
        assertTrue(isHead("   foreach", 3))
    }

    @Test
    fun doesNotFireMidExpression() {
        // `{{ $x && if| }}` — non-whitespace precedes the caret: not a head.
        assertFalse(isHead("\$x && if", 6))
    }

    @Test
    fun doesNotFireAfterReceiver() {
        // `{{ $this->set| }}` — the `$this->` qualifier precedes the caret.
        assertFalse(isHead("\$this->set", 7))
    }

    @Test
    fun toleratesOutOfRangeCaret() {
        assertFalse(isHead("if", -1))
        assertFalse(isHead("if", 3))
    }

    @Test
    fun offersControlAndApiKeywords() {
        val keywords = QiqDirectiveCompletionContributor.keywords
        // Control directives and the section/layout API are all present.
        assertTrue(keywords.containsAll(listOf("if", "foreach", "for", "else", "endif")))
        assertTrue(keywords.containsAll(listOf("setSection", "getSection", "setLayout", "extends")))
        // append/prepend section openers complete too (#50 vocabulary).
        assertTrue(keywords.containsAll(listOf("appendSection", "prependSection")))
    }
}
