package io.github.jingu.idea_qiq_plugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost
import io.github.jingu.idea_qiq_plugin.util.QiqUtil

/**
 * Annotator for applying Qiq-specific text attributes to reserved directive identifiers
 * (such as "extends") that would otherwise not be highlighted.
 */
class QiqReservedDirectiveAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is QiqCodeHost) return

        val match = QiqUtil.findReservedDirectiveSpan(element.text) ?: return
        val highlightRange = match.toTextRange(element.textRange.startOffset)

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(highlightRange)
            .textAttributes(QiqHighlighterKeys.FUNCTION)
            .create()
    }
}
