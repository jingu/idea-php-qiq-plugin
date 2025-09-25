package io.github.jingu.idea_qiq_plugin.ui

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import io.github.jingu.idea_qiq_plugin.highlight.QiqHighlighterKeys
import io.github.jingu.idea_qiq_plugin.highlight.QiqSyntaxHighlighter

class QiqColorSettingsPage : ColorSettingsPage {
    private val DESCRIPTORS = arrayOf(
        AttributesDescriptor("Keyword", QiqHighlighterKeys.KEYWORD),
        AttributesDescriptor("String", QiqHighlighterKeys.STRING),
        AttributesDescriptor("Number", QiqHighlighterKeys.NUMBER),
        AttributesDescriptor("Operator", QiqHighlighterKeys.OP),
        AttributesDescriptor("Variable", QiqHighlighterKeys.VARIABLE),
        AttributesDescriptor("Braces", QiqHighlighterKeys.BRACES),
        AttributesDescriptor("Delimiter {{ }}, {{h}} etc.", QiqHighlighterKeys.DELIMITER),
        AttributesDescriptor("Bad character", QiqHighlighterKeys.BAD)
    )

    override fun getDisplayName() = "Qiq"

    override fun getIcon() = QiqIcons.FILE

    override fun getAttributeDescriptors() = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getHighlighter() = QiqSyntaxHighlighter()

    override fun getDemoText(): String = """
      <div>
        {{ if (\${'$'}user): }}
          HTML escaped: {{h \${'$'}user->name }}!
          URL escaped: {{u \${'$'}url }}
          CSS escaped: {{c \${'$'}cssClass }}
          JavaScript escaped: {{j \${'$'}jsVar }}
          Attribute escaped: {{a \${'$'}attr }}
          Raw output: {{= \${'$'}rawContent }}
        {{ else: }}
          Hello, guest!
        {{ endif }}
      </div>
    """.trimIndent()

  override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey>? = null
}
