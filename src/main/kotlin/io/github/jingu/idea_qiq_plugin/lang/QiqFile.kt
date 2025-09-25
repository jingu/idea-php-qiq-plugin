package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class QiqFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, QiqTemplateLanguage) {
    override fun getFileType(): FileType = QiqFileType
    override fun toString(): String = "Qiq File"
}
