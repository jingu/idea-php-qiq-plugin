package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider

/**
 * Shared detection for "is this element part of a Qiq template (possibly via
 * injected PHP)". Used by helper navigation and inspection suppression so the
 * detection rules stay in one place.
 */
object QiqInjectionSupport {

    fun isInQiqFile(element: PsiElement): Boolean {
        val project = element.project
        val ilm = InjectedLanguageManager.getInstance(project)
        val topLevel = ilm.getTopLevelFile(element) ?: element.containingFile ?: return false

        val viewProvider = topLevel.viewProvider
        if (viewProvider is TemplateLanguageFileViewProvider && viewProvider.baseLanguage == QiqTemplateLanguage) {
            return true
        }
        if (topLevel.language == QiqTemplateLanguage) return true

        val vf = topLevel.virtualFile ?: return false
        if (vf.fileType == QiqFileType) return true

        val name = vf.name
        if (name.endsWith(".qiq") || name.endsWith(".qiq.php")) return true

        return vf.getUserData(QiqFileTypeOverrider.QIQ_MARKER) == true
    }

    /**
     * Whether [file] is a Qiq template file, judged from the file alone (no PSI) —
     * for VFS walks that classify candidate files. Recognizes the Qiq file type,
     * the `.qiq` / `.qiq.php` names, and a file re-typed via the overrider marker.
     */
    fun isQiqTemplateFile(file: VirtualFile): Boolean {
        if (file.fileType == QiqFileType) return true
        val name = file.name
        if (name.endsWith(".qiq", ignoreCase = true) || name.endsWith(".qiq.php", ignoreCase = true)) return true
        return file.getUserData(QiqFileTypeOverrider.QIQ_MARKER) == true
    }
}
