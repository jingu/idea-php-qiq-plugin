package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider

class QiqFileViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    eventSystemEnabled: Boolean,
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, eventSystemEnabled), TemplateLanguageFileViewProvider {

    // Use HTML as the template data language to keep HTML IDE features
    private val templateDataLanguage: Language = HTMLLanguage.INSTANCE

    override fun getTemplateDataLanguage(): Language = templateDataLanguage

    override fun getLanguages(): Set<Language> = setOf(getBaseLanguage(), templateDataLanguage)

    override fun getBaseLanguage(): Language = QiqTemplateLanguage

    override fun createFile(lang: Language): PsiFile? {
        return when (lang) {
            QiqTemplateLanguage -> QiqParserDefinition().createFile(this) // QiqFile
            HTMLLanguage.INSTANCE -> {
                // HTML 側はテンプレートデータ用の軽量 Psi を作る
                val file = LanguageParserDefinitions.INSTANCE.forLanguage(lang)?.createFile(this) ?: return null
                if (file is PsiFileImpl) {
                    file.contentElementType = QiqParserDefinition.TEMPLATE_DATA_ELEMENT
                }
                file
            }
            else -> null
        }
    }

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return QiqFileViewProvider(manager = getManager(), file = fileCopy, eventSystemEnabled = false)
    }
}
