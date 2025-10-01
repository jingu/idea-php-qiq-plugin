package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import io.github.jingu.idea_qiq_plugin.ui.QiqIcons
import javax.swing.Icon

object QiqFileType : LanguageFileType(QiqTemplateLanguage), FileTypeIdentifiableByVirtualFile {
    override fun getName() = "Qiq"
    override fun getDescription() = "Qiq template with PHP and HTML support"
    override fun getDefaultExtension() = "qiq"
    override fun getIcon(): Icon = QiqIcons.FILE

    override fun isMyFileType(file: VirtualFile): Boolean {
        if (file.getUserData(QiqFileTypeOverrider.QIQ_MARKER) == true) {
            return true
        }

        val fileName = file.name
        return fileName.endsWith(".qiq") || fileName.endsWith(".qiq.php")
    }
}
