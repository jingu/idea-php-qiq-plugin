package io.github.jingu.idea_qiq_plugin.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object QiqHighlighterKeys {
    @JvmField val KEYWORD   = TextAttributesKey.createTextAttributesKey(
        "QIQ_KEYWORD",
        DefaultLanguageHighlighterColors.KEYWORD
    )
    @JvmField val FUNCTION  = TextAttributesKey.createTextAttributesKey(
        "QIQ_FUNCTION",
        DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
    )
    @JvmField val DIRECTIVE = TextAttributesKey.createTextAttributesKey(
        "QIQ_DIRECTIVE",
        DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL
    )
    @JvmField val STRING = TextAttributesKey.createTextAttributesKey("QIQ_STRING", DefaultLanguageHighlighterColors.STRING)
    @JvmField val NUMBER = TextAttributesKey.createTextAttributesKey("QIQ_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    @JvmField val OP = TextAttributesKey.createTextAttributesKey("QIQ_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    @JvmField val VARIABLE = TextAttributesKey.createTextAttributesKey("QIQ_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
    @JvmField val BRACES = TextAttributesKey.createTextAttributesKey("QIQ_BRACES", DefaultLanguageHighlighterColors.BRACES)
    @JvmField val DELIMITER = TextAttributesKey.createTextAttributesKey("QIQ_DELIMITER", DefaultLanguageHighlighterColors.BRACES)
    @JvmField val BAD = TextAttributesKey.createTextAttributesKey("QIQ_BAD_CHARACTER", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
    @JvmField val DELIM_CODE = TextAttributesKey.createTextAttributesKey(
        "QIQ_DELIM_CODE",
        DefaultLanguageHighlighterColors.PARENTHESES // gray系
    )
    @JvmField val DELIM_RAW = TextAttributesKey.createTextAttributesKey(
        "QIQ_DELIM_RAW",
        DefaultLanguageHighlighterColors.KEYWORD // red
    )
    @JvmField val DELIM_ESCAPED = TextAttributesKey.createTextAttributesKey(
        "QIQ_DELIM_ESCAPED",
        DefaultLanguageHighlighterColors.STRING // blue/green系
    )
    @JvmField val DELIM_HTML = TextAttributesKey.createTextAttributesKey(
        "QIQ_DELIM_HTML",
        DefaultLanguageHighlighterColors.STRING // blue/green系
    )
    @JvmField val DELIM_JSON = TextAttributesKey.createTextAttributesKey(
        "QIQ_DELIM_JSON",
        DefaultLanguageHighlighterColors.STRING // blue/green系
    )
    @JvmField val DELIM_URL = TextAttributesKey.createTextAttributesKey(
        "QIQ_DELIM_URL",
        DefaultLanguageHighlighterColors.STRING // blue/green系
    )
}
