package io.github.jingu.idea_qiq_plugin.highlight

import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes
import kotlin.test.Test
import kotlin.test.assertContentEquals

class QiqSyntaxHighlighterTest {

    private val highlighter = QiqSyntaxHighlighter()

    @Test
    fun rawDelimitersUseConfiguredKey() {
        val tokens = listOf(
            QiqTokenTypes.RAW_OPEN,
            QiqTokenTypes.LBRACE_EQ,
            QiqTokenTypes.RAW_CLOSE,
            QiqTokenTypes.RBRACE_EQ
        )

        tokens.forEach { type ->
            val keys = highlighter.getTokenHighlights(type)
            assertContentEquals(arrayOf(QiqHighlighterKeys.DELIM_RAW), keys)
        }
    }
}
