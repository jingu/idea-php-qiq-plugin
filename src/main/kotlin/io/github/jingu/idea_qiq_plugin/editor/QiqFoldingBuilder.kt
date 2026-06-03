package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import io.github.jingu.idea_qiq_plugin.block.QiqBlockModel

/**
 * Folds Qiq block directives (`{{ if (...): }}` … `{{ endif }}`, `foreach`, `for`,
 * `setSection`, `setBlock`) using the shared [QiqBlockModel].
 *
 * Only the block body is folded, so the opener and closer delimiters stay visible
 * and a collapsed block reads as `{{ if (...): }}…{{ endif }}`. Single-line blocks
 * are skipped — there is nothing useful to collapse.
 */
class QiqFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ): Array<FoldingDescriptor> {
        val node = root.node ?: return emptyArray()
        val descriptors = ArrayList<FoldingDescriptor>()

        for (block in QiqBlockModel.computeBlockRanges(document.charsSequence)) {
            val body = block.bodyRange
            if (body.isEmpty) continue
            // Nothing to collapse if the opener and closer share a line.
            if (document.getLineNumber(body.startOffset) == document.getLineNumber(body.endOffset)) continue
            descriptors.add(FoldingDescriptor(node, body))
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = "…"

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
