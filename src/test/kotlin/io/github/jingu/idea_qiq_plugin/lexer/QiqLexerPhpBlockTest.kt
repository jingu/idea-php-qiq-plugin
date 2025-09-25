package io.github.jingu.idea_qiq_plugin.lexer

import kotlin.test.Test
import kotlin.test.assertEquals

class QiqLexerPhpBlockTest {

    @Test
    fun phpIslandProducesDedicatedTokens() {
        val lexer = QiqLexerAdapter()
        val text = "<div><?php echo 42; ?>text</div>"
        lexer.start(text)

        val tokens = mutableListOf<Pair<String?, String>>()
        while (lexer.tokenType != null) {
            tokens.add(lexer.tokenType?.toString() to text.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }

        val expected: List<Pair<String?, String>> = listOf(
            "TEMPLATE_DATA" to "<div>",
            "PHP_OPEN" to "<?php",
            "PHP_CONTENT" to " echo 42; ",
            "PHP_CLOSE" to "?>",
            "TEMPLATE_DATA" to "text",
            "TEMPLATE_DATA" to "</div>"
        )

        assertEquals(expected, tokens)
    }

    @Test
    fun qiqCodeInsideHtmlAttribute() {
        val lexer = QiqLexerAdapter()
        val text = "<script src=\"{{= asset('/js/common.js') }}\"></script>"
        lexer.start(text)

        val tokens = mutableListOf<String>()
        while (lexer.tokenType != null) {
            tokens.add(lexer.tokenType!!.toString())
            lexer.advance()
        }

        val expected = listOf(
            "TEMPLATE_DATA",
            "RAW_OPEN",
            "CODE_BODY",
            "RBRACE_EQ",
            "TEMPLATE_DATA",
            "TEMPLATE_DATA"
        )

        assertEquals(expected, tokens)
    }
}
