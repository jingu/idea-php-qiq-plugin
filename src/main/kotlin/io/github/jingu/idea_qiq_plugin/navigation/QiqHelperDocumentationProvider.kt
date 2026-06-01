package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FunctionReference
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperTargets
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport

/**
 * Points Quick Documentation (Ctrl/Cmd+Q, hover, and the doc PhpStorm appends
 * to a warning tooltip) at the real declaration of a Qiq helper call.
 *
 * A helper call such as `{{ renderAd(...) }}` is an unresolved global call in
 * the injected PHP — Qiq dispatches helpers at runtime — so PhpStorm has no
 * symbol to document and renders an empty "Source: .../null". By supplying the
 * resolved helper target as the documentation element (a 1.x helper class's
 * `__invoke`, or a 2.x/3.x `Qiq\Helpers` subclass method — the same target
 * [QiqHelperGotoDeclarationHandler] navigates to), the platform documents that
 * method instead: its signature, PHPDoc and real source file.
 */
class QiqHelperDocumentationProvider : AbstractDocumentationProvider() {

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        val element = contextElement ?: return null
        val call = PsiTreeUtil.getParentOfType(element, FunctionReference::class.java, false) ?: return null

        // Only when the caret is on the call name, mirroring the helper goto.
        val nameNode = call.nameNode ?: return null
        if (!nameNode.textRange.contains(element.textRange)) return null

        if (!QiqInjectionSupport.isInQiqFile(call)) return null

        val name = call.name?.takeIf { it.isNotEmpty() } ?: return null
        // Document the first resolved target (`__invoke` for a 1.x class, else
        // a 2.x/3.x method); for an overloaded name PhpStorm documents one.
        return QiqHelperTargets.functions(call.project, name).firstOrNull()
    }
}
