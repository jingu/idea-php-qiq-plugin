package io.github.jingu.idea_qiq_plugin.completion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the pure directive-head gate that decides where keyword completion fires.
 * [hostText] is the `{{ }}` block content; [tokenStartInHost] is the start offset
 * of the directive token being completed within it (the typed prefix, or
 * completion's synthetic dummy identifier when the head is still empty) — not the
 * live caret.
 */
class QiqDirectiveCompletionContributorTest {

    private fun isHead(hostText: String, tokenStartInHost: Int) =
        QiqDirectiveCompletionContributor.isDirectiveHead(hostText, tokenStartInHost)

    @Test
    fun firesAtEmptyHead() {
        // `{{ | }}` — nothing typed; completion's dummy identifier sits at the head.
        assertTrue(isHead("", 0))
        assertTrue(isHead("  ", 2))
    }

    @Test
    fun firesWhileTypingAKeyword() {
        // `{{ if| }}` — the typed token starts at offset 1 (after the space), so
        // only the leading whitespace precedes the token start.
        assertTrue(isHead(" if", 1))
        assertTrue(isHead("   foreach", 3))
    }

    @Test
    fun doesNotFireMidExpression() {
        // `{{ $x && if| }}` — non-whitespace precedes the token start: not a head.
        assertFalse(isHead("\$x && if", 6))
    }

    @Test
    fun doesNotFireAfterReceiver() {
        // `{{ $this->set| }}` — the `$this->` qualifier precedes the token start.
        assertFalse(isHead("\$this->set", 7))
    }

    @Test
    fun toleratesOutOfRangeToken() {
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
