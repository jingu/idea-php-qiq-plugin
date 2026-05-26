package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.rename.PsiElementRenameHandler
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

/**
 * Makes Shift+F6 work when the caret sits on a PHP identifier inside a Qiq
 * injection host such as `{{h $article->title }}` or `<?php ... ?>`.
 *
 * The platform's default PsiElementRenameHandler reads
 * CommonDataKeys.PSI_ELEMENT from the data context, which is set to the
 * outer Qiq host (an unnamed wrapper) — that satisfies neither
 * PsiNamedElement nor the renameability checks, so the action stays
 * disabled for property references. Local variable rename happens to work
 * via the default handler because variables expose themselves as
 * PsiNamedElement reachable through other data keys, but property /
 * method accesses do not.
 *
 * This handler descends into the injected fragment via
 * InjectedLanguageManager, then walks parents and asks PHP's own
 * references to resolve themselves. The first reference whose resolution
 * is a PsiNameIdentifierOwner becomes the rename target. PHP's type
 * inference already has the injected context wired by our
 * MultiHostInjector, so resolution covers variables, properties, methods
 * and class references uniformly.
 *
 * Scope: the handler only fires when the top-level (host) file's language
 * is Qiq, so it does not compete with PHP's own handlers in plain `.php`
 * files. The platform pre-narrows PSI_FILE in the data context to the
 * most specific file at the caret (sometimes the injected PHP file,
 * sometimes the outer Qiq file); the host check normalizes both cases.
 */
class QiqInjectedRenameHandler : PsiElementRenameHandler() {

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean =
        findInjectedTarget(dataContext) != null

    override fun isRenaming(dataContext: DataContext): Boolean =
        isAvailableOnDataContext(dataContext)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
        val target = findInjectedTarget(dataContext) ?: return
        invoke(project, arrayOf(target), dataContext)
    }

    private fun findInjectedTarget(ctx: DataContext): PsiElement? {
        val editor = CommonDataKeys.EDITOR.getData(ctx) ?: return null
        val file = CommonDataKeys.PSI_FILE.getData(ctx) ?: return null
        val offset = editor.caretModel.offset

        val ilm = InjectedLanguageManager.getInstance(file.project)
        val hostFile = ilm.getTopLevelFile(file)
        if (hostFile.language !is QiqTemplateLanguage) return null

        val injectedElement = if (file !== hostFile) {
            file.findElementAt(offset)
        } else {
            ilm.findInjectedElementAt(file, offset)
        } ?: return null

        return resolveRenameTarget(injectedElement)
    }

    private fun resolveRenameTarget(start: PsiElement): PsiNamedElement? {
        var current: PsiElement? = start
        var depth = 0
        while (current != null && current !is PsiFile && depth <= MAX_WALK_DEPTH) {
            resolveAcrossReferences(current)?.let { return it }
            if (current is PsiNameIdentifierOwner &&
                current.nameIdentifier != null &&
                (current as PsiNamedElement).name != null
            ) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun resolveAcrossReferences(element: PsiElement): PsiNamedElement? {
        if (element is PsiReference) {
            (element as PsiReference).resolve()?.let { resolved ->
                if (resolved is PsiNamedElement && resolved != element && resolved.name != null) {
                    return resolved
                }
            }
        }
        for (ref in element.references) {
            val resolved = ref.resolve()
            if (resolved is PsiNamedElement && resolved != element && resolved.name != null) {
                return resolved
            }
        }
        return null
    }

    private companion object {
        // Safety cap so a degenerate PSI cannot trap us in a tight loop.
        const val MAX_WALK_DEPTH = 12
    }
}
