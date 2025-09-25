package io.github.jingu.idea_qiq_plugin.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import io.github.jingu.idea_qiq_plugin.lexer.QiqLexerAdapter
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes

class QiqSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = QiqLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        if (tokenType == null) return emptyArray()
        return when (tokenType) {
            // ==== RAW ({{= ... }})
            QiqTokenTypes.LBRACE_EQ, QiqTokenTypes.RBRACE_EQ,
            QiqTokenTypes.RAW_OPEN, QiqTokenTypes.RAW_CLOSE,
            QiqTokenTypes.ESCAPE_RAW_OPEN, QiqTokenTypes.ESCAPE_RAW_CLOSE -> pack(QiqHighlighterKeys.DELIM_RAW)

            // ==== HTML-escaped ({{h ... }})
            QiqTokenTypes.LBRACEH, QiqTokenTypes.RBRACEH,
            QiqTokenTypes.ESCAPE_H_OPEN, QiqTokenTypes.ESCAPE_H_CLOSE -> pack(QiqHighlighterKeys.DELIM_HTML)

            // ==== JSON-escaped ({{j ... }})
            QiqTokenTypes.LBRACEJ, QiqTokenTypes.RBRACEJ,
            QiqTokenTypes.ESCAPE_J_OPEN, QiqTokenTypes.ESCAPE_J_CLOSE -> pack(QiqHighlighterKeys.DELIM_JSON)

            // ==== URL-escaped ({{u ... }})
            QiqTokenTypes.LBRACEU, QiqTokenTypes.RBRACEU,
            QiqTokenTypes.ESCAPE_U_OPEN, QiqTokenTypes.ESCAPE_U_CLOSE -> pack(QiqHighlighterKeys.DELIM_URL)

            // ==== General escaped ({{a ... }}, {{c ... }})
            QiqTokenTypes.LBRACEA, QiqTokenTypes.LBRACEC, QiqTokenTypes.RBRACEC,
            QiqTokenTypes.ESCAPE_A_OPEN, QiqTokenTypes.ESCAPE_A_CLOSE,
            QiqTokenTypes.ESCAPE_C_OPEN, QiqTokenTypes.ESCAPE_C_CLOSE -> pack(QiqHighlighterKeys.DELIM_ESCAPED)

            // ==== Plain code block ({{ ... }})
            QiqTokenTypes.LBRACE2, QiqTokenTypes.RBRACE2,
            QiqTokenTypes.CODE_OPEN, QiqTokenTypes.CODE_CLOSE,
            QiqTokenTypes.CODE_OPEN_TOKEN, QiqTokenTypes.CODE_CLOSE_TOKEN -> pack(QiqHighlighterKeys.DELIM_CODE)

            // ==== Qiq directives (if/for/endif/...)
            QiqTokenTypes.DIRECTIVE_OPEN, QiqTokenTypes.DIRECTIVE_CLOSE -> pack(QiqHighlighterKeys.DIRECTIVE)

            // (Optional) If you keep a unified ESCAPE fill color for inner content of escape blocks:
            // QiqTokenTypes.ESCAPE_H, QiqTokenTypes.ESCAPE_J, QiqTokenTypes.ESCAPE_U, QiqTokenTypes.ESCAPE_A, QiqTokenTypes.ESCAPE_C -> pack(QiqHighlighterKeys.ESCAPE)

            else -> emptyArray()
        }
    }
}
