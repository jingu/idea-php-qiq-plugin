package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import io.github.jingu.idea_qiq_plugin.util.QiqTemplateResolver

/**
 * Enables Find Usages on a Qiq template file. The platform offers Find Usages
 * only for elements a registered handler claims; a plain file has none, so this
 * factory supplies the default handler for Qiq templates (`.qiq` / `.qiq.php`, and
 * the `.php` partials Qiq renders that live under a template root —
 * [QiqTemplateResolver.isTemplateTarget]). The handler itself does nothing custom:
 * its inherited reference search runs [QiqTemplateReferenceSearcher], which reports
 * the referencing call sites.
 */
class QiqTemplateFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean {
        val file = (element as? PsiFileSystemItem)?.virtualFile ?: return false
        return QiqTemplateResolver.isTemplateTarget(element.project, file)
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? =
        if (canFindUsages(element)) object : FindUsagesHandler(element) {} else null
}
