package io.github.jingu.idea_qiq_plugin.newfile

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import io.github.jingu.idea_qiq_plugin.lang.QiqFileTypeOverrider
import io.github.jingu.idea_qiq_plugin.ui.QiqBundle
import io.github.jingu.idea_qiq_plugin.ui.QiqIcons

class QiqCreateFileAction : CreateFileFromTemplateAction(
    QiqBundle.message("action.qiq.create.template.text"),
    QiqBundle.message("action.qiq.create.template.description"),
    QiqIcons.FILE
), DumbAware {

    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder
            .setTitle(QiqBundle.message("action.qiq.create.template.dialog.title"))
            .addKind(
                QiqBundle.message("action.qiq.create.template.kind.qiq"),
                QiqIcons.FILE,
                TemplateNames.QIQ_FILE
            )
            .addKind(
                QiqBundle.message("action.qiq.create.template.kind.qiq_php"),
                QiqIcons.FILE,
                TemplateNames.QIQ_PHP_FILE
            )
            .addKind(
                QiqBundle.message("action.qiq.create.template.kind.php"),
                QiqIcons.FILE,
                TemplateNames.QIQ_PHP_TEMPLATE
            )
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String {
        return QiqBundle.message("action.qiq.create.template.action.name", newName)
    }

    override fun createFile(name: String, templateName: String, dir: PsiDirectory): PsiFile? {
        val psiFile = super.createFile(name, templateName, dir)
        psiFile?.virtualFile?.let { virtualFile ->
            virtualFile.putUserData(QiqFileTypeOverrider.QIQ_MARKER, true)
            ApplicationManager.getApplication().invokeLater(
                {
                    if (virtualFile.isValid) {
                        com.intellij.util.FileContentUtilCore.reparseFiles(listOf(virtualFile))
                    }
                },
                ModalityState.nonModal(),
                { !virtualFile.isValid }
            )
        }
        return psiFile
    }

    private object TemplateNames {
        const val QIQ_FILE = "Qiq File"
        const val QIQ_PHP_FILE = "Qiq PHP File"
        const val QIQ_PHP_TEMPLATE = "Qiq PHP Template"
    }
}
