package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.openapi.util.TextRange

/**
 * A single no-op block spanning the whole file. The Qiq formatting model
 * deliberately does nothing structural — its only job is to give the Qiq (base)
 * language a formatter so the template framework does not run the HTML formatter
 * over the interleaved markup. The actual block-aware reindent is applied
 * textually by [QiqPostFormatProcessor].
 */
class QiqFormatBlock(private val range: TextRange) : Block {

    override fun getTextRange(): TextRange = range
    override fun getSubBlocks(): List<Block> = emptyList()
    override fun getWrap(): Wrap? = null
    override fun getAlignment(): Alignment? = null
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null
    override fun getIndent(): Indent = Indent.getNoneIndent()
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(Indent.getNoneIndent(), null)
    override fun isIncomplete(): Boolean = false
    override fun isLeaf(): Boolean = true
}
