package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.openapi.util.TextRange

/**
 * Gives the Qiq (base) language a no-op formatting model.
 *
 * Qiq's PSI is flat and the file interleaves HTML, injected PHP, and Qiq blocks,
 * so the block-based formatting engine cannot compose a sensible indent. Rather
 * than fight it, this returns an empty model: its sole purpose is to claim the
 * formatter for the Qiq base language so the template framework does not run the
 * HTML formatter over the interleaved markup. The block-aware reindent is applied
 * textually afterwards by [QiqPostFormatProcessor].
 */
class QiqFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(context: FormattingContext): FormattingModel {
        val file = context.containingFile
        val root = QiqFormatBlock(TextRange(0, file.textLength))
        return FormattingModelProvider.createFormattingModelForPsiFile(file, root, context.codeStyleSettings)
    }
}
