package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.OuterLanguageElementType

class QiqTokenType(debugName: String) : IElementType(debugName, QiqTemplateLanguage)

object QiqTokenTypes {
    @JvmField val DUMMY_ID = IElementType("DUMMY_ID", QiqTemplateLanguage)

    // 外側(HTML等) のテンプレートデータ
    @JvmField val TEMPLATE_DATA: IElementType = QiqTokenType("TEMPLATE_DATA")

    // 代表的なトークン（必要に応じて増やす）
    @JvmField val LBRACE2 = QiqTokenType("LBRACE2")    // {{
    @JvmField val RBRACE2 = QiqTokenType("RBRACE2")    // }}
    @JvmField val LBRACEH = QiqTokenType("LBRACEH")    // {{h
    @JvmField val LBRACEA = QiqTokenType("LBRACEA")    // {{a
    @JvmField val LBRACEU = QiqTokenType("LBRACEU")    // {{u
    @JvmField val LBRACEC = QiqTokenType("LBRACEC")    // {{c
    @JvmField val LBRACEJ = QiqTokenType("LBRACEJ")    // {{j
    @JvmField val LBRACE_EQ = QiqTokenType("LBRACE_EQ")  // {{=

    @JvmField val RBRACEH = QiqTokenType("RBRACEH")    // {{h
    @JvmField val RBRACEA = QiqTokenType("RBRACEA")    // {{a
    @JvmField val RBRACEU = QiqTokenType("RBRACEU")    // {{u
    @JvmField val RBRACEC = QiqTokenType("RBRACEC")    // {{c
    @JvmField val RBRACEJ = QiqTokenType("RBRACEJ")    // {{j
    @JvmField val RBRACE_EQ = QiqTokenType("RBRACE_EQ")  // {{=

    // ===== ESCAPE/RAW friendly aliases for highlighter (open/close pairs) =====
    // RAW output: {{= ... }}
    @JvmField val ESCAPE_RAW_OPEN = QiqTokenType("ESCAPE_RAW_OPEN")   // alias for {{=
    @JvmField val ESCAPE_RAW_CLOSE = QiqTokenType("ESCAPE_RAW_CLOSE") // alias for }} (RAW)

    // Escaped output variants: {{h / {{a / {{j / {{u / {{c }}
    @JvmField val ESCAPE_H_OPEN = QiqTokenType("ESCAPE_H_OPEN")
    @JvmField val ESCAPE_H_CLOSE = QiqTokenType("ESCAPE_H_CLOSE")

    @JvmField val ESCAPE_A_OPEN = QiqTokenType("ESCAPE_A_OPEN")
    @JvmField val ESCAPE_A_CLOSE = QiqTokenType("ESCAPE_A_CLOSE")

    @JvmField val ESCAPE_J_OPEN = QiqTokenType("ESCAPE_J_OPEN")
    @JvmField val ESCAPE_J_CLOSE = QiqTokenType("ESCAPE_J_CLOSE")

    @JvmField val ESCAPE_U_OPEN = QiqTokenType("ESCAPE_U_OPEN")
    @JvmField val ESCAPE_U_CLOSE = QiqTokenType("ESCAPE_U_CLOSE")

    @JvmField val ESCAPE_C_OPEN = QiqTokenType("ESCAPE_C_OPEN")
    @JvmField val ESCAPE_C_CLOSE = QiqTokenType("ESCAPE_C_CLOSE")

    // Plain code block: {{ ... }}  (non-output)
    @JvmField val CODE_OPEN_TOKEN = QiqTokenType("CODE_OPEN_TOKEN")   // alias for {{
    @JvmField val CODE_CLOSE_TOKEN = QiqTokenType("CODE_CLOSE_TOKEN") // alias for }} (plain)

    @JvmField val DIRECTIVE_OPEN = QiqTokenType("DIRECTIVE_OPEN") // {{ if( ... ) }}
    @JvmField val DIRECTIVE_CLOSE = QiqTokenType("DIRECTIVE_CLOSE")// {{ endif }}
    @JvmField val PHP_OPEN = QiqTokenType("PHP_OPEN")  // <?php
    @JvmField val PHP_CLOSE = QiqTokenType("PHP_CLOSE")// ?>
    @JvmField val IDENT = QiqTokenType("IDENT")
    @JvmField val NUMBER = QiqTokenType("NUMBER")
    @JvmField val STRING = QiqTokenType("STRING")
    @JvmField val OP = QiqTokenType("OP")
    @JvmField val COMMA = QiqTokenType("COMMA")
    @JvmField val COLON = QiqTokenType("COLON")
    @JvmField val PAREN_L = QiqTokenType("PAREN_L")
    @JvmField val PAREN_R = QiqTokenType("PAREN_R")
    @JvmField val WHITE_SPACE = QiqTokenType("WHITE_SPACE")
    @JvmField val BAD_CHAR = QiqTokenType("BAD_CHAR")
    @JvmField val OPEN_EQ = QiqTokenType("OPEN_EQ") // {{=
    @JvmField val OPEN_H = QiqTokenType("OPEN_H")   // {{h
    @JvmField val OPEN_A = QiqTokenType("OPEN_A")   // {{a
    @JvmField val OPEN_U = QiqTokenType("OPEN_U")   // {{u
    @JvmField val OPEN_C = QiqTokenType("OPEN_C")   // {{c
    @JvmField val OPEN_J = QiqTokenType("OPEN_J")   // {{j
    @JvmField val OPEN_CTRL = QiqTokenType("OPEN_CTRL") // {{ if, {{ for, etc.
    @JvmField val OPEN_PHP = QiqTokenType("OPEN_PHP") //
    @JvmField val QIQ_PHP_USE = QiqTokenType("QIQ_PHP_USE") // {{ php use ...
    @JvmField val PHP_USE = QiqTokenType("PHP_USE") // <?php use ...
    @JvmField val CLOSE_EQ = QiqTokenType("CLOSE_EQ") // }}
    @JvmField val CLOSE_H = QiqTokenType("CLOSE_H")   //
    @JvmField val CLOSE_A = QiqTokenType("CLOSE_A")   //
    @JvmField val CLOSE_U = QiqTokenType("CLOSE_U")   //
    @JvmField val CLOSE_C = QiqTokenType("CLOSE_C")   //
    @JvmField val CLOSE_J = QiqTokenType("CLOSE_J")   //
    @JvmField val CLOSE_CTRL = QiqTokenType("CLOSE_CTRL") // {{
    @JvmField val CLOSE_PHP = QiqTokenType("CLOSE_PHP") // ?>
    @JvmField val CODE = QiqTokenType("CODE") // Qiq code inside {{ ... }}
    @JvmField val PHP_CODE = QiqTokenType("PHP_CODE") // PHP code
    @JvmField val QIQ_CODE = QiqTokenType("QIQ_CODE") // Composite token for folded Qiq code
    @JvmField val QIQ_PHP_CODE = QiqTokenType("QIQ_PHP_CODE") // Composite token for folded PHP code

    // ===== Content tokens for injection hosts (used by QiqParserDefinition.createElement) =====
    // Plain code: {{ ... }}
    @JvmField val CODE_CONTENT = QiqTokenType("CODE_CONTENT")
    // Raw output: {{= ... }}
    @JvmField val RAW_CONTENT = QiqTokenType("RAW_CONTENT")
    // Generic escaped content (fallback if specific variant is not used by the lexer)
    @JvmField val ESCAPE_CONTENT = QiqTokenType("ESCAPE_CONTENT")
    // Escaped output variants: {{h ...}}, {{a ...}}, {{j ...}}, {{u ...}}, {{c ...}}
    @JvmField val ESCAPE_H_CONTENT = QiqTokenType("ESCAPE_H_CONTENT")
    @JvmField val ESCAPE_A_CONTENT = QiqTokenType("ESCAPE_A_CONTENT")
    @JvmField val ESCAPE_J_CONTENT = QiqTokenType("ESCAPE_J_CONTENT")
    @JvmField val ESCAPE_U_CONTENT = QiqTokenType("ESCAPE_U_CONTENT")
    @JvmField val ESCAPE_C_CONTENT = QiqTokenType("ESCAPE_C_CONTENT")
    // PHP block content: <?php ... ?>
    @JvmField val PHP_CONTENT = QiqTokenType("PHP_CONTENT")
    @JvmField val PHP_BLOCK_CONTENT = QiqTokenType("PHP_BLOCK_CONTENT") // For injection host

    @JvmField val TEMPLATE_TEXT = QiqTokenType("TEMPLATE_TEXT") // Text outside of Qiq blocks
    @JvmField val OUTER: IElementType = OuterLanguageElementType("QIQ_OUTER", QiqTemplateLanguage)

    // ===== Aliases expected by generated lexer =====
    @JvmField val CODE_OPEN = QiqTokenType("CODE_OPEN")          // alias used by lexer for {{
    @JvmField val CODE_CLOSE = QiqTokenType("CODE_CLOSE")        // alias used by lexer for }} (plain)
    @JvmField val RAW_OPEN = QiqTokenType("RAW_OPEN")            // alias used by lexer for {{=
    @JvmField val RAW_CLOSE = QiqTokenType("RAW_CLOSE")          // alias used by lexer for }} (RAW)

    @JvmField val CODE_BODY = IElementType("CODE_BODY", QiqTemplateLanguage)
}
