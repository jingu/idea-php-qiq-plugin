package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.util.QiqSectionCall
import io.github.jingu.idea_qiq_plugin.util.QiqSectionIndex

/**
 * Cmd/Ctrl+click navigation on a Qiq section name, in both directions:
 * - `getSection('x')` / `hasSection('x')` -> the matching `setSection('x')`
 *   definitions;
 * - `setSection('x')` -> the `getSection`/`hasSection` usages.
 *
 * Implemented as a [GotoDeclarationHandler] rather than a
 * `PsiReferenceContributor`: a name that collides with a PHP function (e.g.
 * `'header'`) gets a hard PHP function reference on the same string literal, and
 * `PsiMultiReference` then suppresses our soft section reference. Handler targets
 * are aggregated independently of references, so they survive that collision —
 * the same reason [QiqHelperGotoDeclarationHandler] is used for helper calls.
 */
class QiqSectionGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val src = sourceElement ?: return null
        // GotoDeclaration may hand us the host (Qiq) leaf rather than the injected
        // PHP element; in that case descend into the injection at the caret to find
        // the string literal.
        val literal = PsiTreeUtil.getParentOfType(src, StringLiteralExpression::class.java, false)
            ?: injectedStringLiteralAt(src, offset)
            ?: return null
        if (!QiqInjectionSupport.isInQiqFile(literal)) return null

        // A reader (getSection/hasSection) navigates to definitions; the writer
        // (setSection) navigates to usages — the reverse direction.
        val toUsages = when {
            QiqSectionCall.isReaderArg(literal) -> false
            QiqSectionCall.isWriterArg(literal) -> true
            else -> return null
        }

        val name = literal.contents
        if (name.isEmpty()) return null

        val project = literal.project
        val contextFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(literal)?.virtualFile
            ?: literal.containingFile?.virtualFile
            ?: return null

        val locations = if (toUsages) {
            QiqSectionIndex.usagesByName(project, contextFile, name)
        } else {
            QiqSectionIndex.definitionsByName(project, contextFile, name)
        }
        if (log.isDebugEnabled) {
            log.debug("Qiq section goto: name='$name' toUsages=$toUsages context=${contextFile.path} -> ${locations.size} target(s)")
        }
        if (locations.isEmpty()) return null

        val psiManager = PsiManager.getInstance(project)
        val targets = locations.mapNotNull { location ->
            psiManager.findFile(location.file)?.let {
                // location.head is the actual directive name (setSection / getSection / hasSection).
                QiqSectionTarget(it, location.name, location.head, location.nameRange.startOffset) as PsiElement
            }
        }
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    /** The injected string literal at host [offset], when [src] is the host leaf. */
    private fun injectedStringLiteralAt(src: PsiElement, offset: Int): StringLiteralExpression? {
        val hostFile = src.containingFile ?: return null
        val injected = InjectedLanguageManager.getInstance(src.project).findInjectedElementAt(hostFile, offset)
            ?: return null
        return PsiTreeUtil.getParentOfType(injected, StringLiteralExpression::class.java, false)
    }

    private companion object {
        private val log = Logger.getInstance(QiqSectionGotoDeclarationHandler::class.java)
    }
}
