package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import io.github.jingu.idea_qiq_plugin.util.QiqTemplateResolver

/**
 * Makes renaming a Qiq template *file* rewrite the path strings that reference it.
 *
 * The platform picks a single rename processor per element, and for a plain `.php`
 * partial PhpStorm's own `PhpFileRenameProcessor` wins — it does not search the
 * injected Qiq path references, so the rename leaves `render('partial/header')`
 * untouched and shows no usages in the preview. Registered `order="first"`, this
 * processor claims Qiq template files ([QiqTemplateResolver.isTemplateTarget]) and
 * collects references with a plain [ReferencesSearch], which runs
 * [QiqTemplateReferenceSearcher] (alongside any PHP reference search), so every
 * referencing call is found and rewritten by `QiqIncludeReference` itself
 * (`handleElementRename` / `bindToElement`, whichever the platform's rename path
 * invokes) when the file is renamed.
 *
 * Scoped to files under a detected template root, so ordinary PHP source files are
 * still handled by PhpStorm's processor.
 */
class QiqTemplateRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        val file = (element as? PsiFileSystemItem)?.virtualFile ?: return false
        return QiqTemplateResolver.isTemplateTarget(element.project, file)
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference> = ReferencesSearch.search(element, searchScope, false).findAll()
}
