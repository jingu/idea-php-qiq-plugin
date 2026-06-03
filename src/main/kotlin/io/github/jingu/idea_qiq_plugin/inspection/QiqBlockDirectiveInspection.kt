package io.github.jingu.idea_qiq_plugin.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import io.github.jingu.idea_qiq_plugin.block.QiqBlockModel
import io.github.jingu.idea_qiq_plugin.block.QiqBlockProblem
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage
import io.github.jingu.idea_qiq_plugin.ui.QiqBundle

/**
 * Warns on structurally invalid Qiq blocks: an opener with no closer, a closer
 * with no opener, and a closer that does not match the nearest open block
 * (e.g. `{{ foreach }}` … `{{ endif }}`).
 *
 * Pairing is delegated to [QiqBlockModel.validate], the same model that drives
 * folding, block matching, and the structure view, so the warnings line up with
 * those features. Runs on the whole file at once rather than per element.
 */
class QiqBlockDirectiveInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (!file.language.isKindOf(QiqTemplateLanguage)) return null

        val problems = QiqBlockModel.validate(file.text)
        if (problems.isEmpty()) return null

        return problems.map { problem ->
            manager.createProblemDescriptor(
                file,
                problem.range,
                message(problem),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
            )
        }.toTypedArray()
    }

    private fun message(problem: QiqBlockProblem): String = when (problem.kind) {
        QiqBlockProblem.Kind.UNCLOSED_OPENER ->
            QiqBundle.message("inspection.block.unclosed", problem.type.openHead, problem.type.closeText)
        QiqBlockProblem.Kind.UNMATCHED_CLOSER ->
            QiqBundle.message("inspection.block.unmatched", problem.type.closeText)
        QiqBlockProblem.Kind.MISMATCHED_CLOSER ->
            QiqBundle.message("inspection.block.mismatched", problem.type.closeText, problem.expected!!.openHead)
    }
}
