package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.util.QiqTemplateResolver
import io.github.jingu.idea_qiq_plugin.util.QiqUtil

class QiqReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, ctx: ProcessingContext): Array<PsiReference> {
                    if (!isInQiqFile(element)) return PsiReference.EMPTY_ARRAY

                    val stringLiteral = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    val parameterList = PsiTreeUtil.getParentOfType(stringLiteral, ParameterList::class.java) ?: return PsiReference.EMPTY_ARRAY
                    val function = parameterList.parent as? FunctionReference ?: return PsiReference.EMPTY_ARRAY

                    val parameters = function.parameterList?.parameters ?: return PsiReference.EMPTY_ARRAY
                    val argIndex = parameters.indexOf(stringLiteral)
                    if (argIndex == -1 || argIndex > 3) return PsiReference.EMPTY_ARRAY

                    val path = stringLiteral.contents
                    if (!QiqUtil.isIncludePath(path)) return PsiReference.EMPTY_ARRAY

                    val range = TextRange.create(1, element.textLength - 1).takeIf { it.startOffset < it.endOffset }
                        ?: return PsiReference.EMPTY_ARRAY

                    val ilm = InjectedLanguageManager.getInstance(element.project)
                    val contextVirtualFile = ilm.getTopLevelFile(element)?.virtualFile
                        ?: element.containingFile?.virtualFile

                    return arrayOf(QiqIncludeReference(element, path, range, contextVirtualFile))
                }
            }
        )
    }

    private fun isInQiqFile(element: PsiElement): Boolean =
        QiqInjectionSupport.isInQiqFile(element)

}

class QiqIncludeReference(
    element: PsiElement,
    private val path: String,
    rangeInElement: com.intellij.openapi.util.TextRange? = null,
    private val contextVirtualFile: com.intellij.openapi.vfs.VirtualFile? = null
) :
    PsiReferenceBase<PsiElement>(element, rangeInElement ?: com.intellij.openapi.util.TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val contextFile = contextVirtualFile ?: element.containingFile?.virtualFile
        val result = QiqUtil.findTemplateByPath(element.project, path, contextFile)

        return result
    }

    /**
     * True when [element] is one of the template files this path resolves to. A
     * path can resolve to several candidates (different roots/extensions), so
     * membership in the full resolved set — not just the first match [resolve]
     * returns — is what makes Find Usages and Rename see every referencing call.
     */
    override fun isReferenceTo(element: PsiElement): Boolean {
        val targetFile = (element as? PsiFileSystemItem)?.virtualFile ?: return false
        val contextFile = contextVirtualFile ?: this.element.containingFile?.virtualFile
        return QiqTemplateResolver.resolve(this.element.project, path, contextFile).any { it == targetFile }
    }

    /**
     * Rewrite only the path's final segment when the referenced template file is
     * renamed, keeping the directory prefix and the original extension style
     * (see [QiqTemplateResolver.renamedPath]). The default would replace the whole
     * path range with the bare new file name, dropping the directory.
     */
    override fun handleElementRename(newElementName: String): PsiElement {
        val extensions = QiqTemplateResolver.candidateExtensions(element.project)
        return super.handleElementRename(QiqTemplateResolver.renamedPath(path, newElementName, extensions))
    }

    /**
     * Some rename paths rebind a reference to the new file via [bindToElement]
     * rather than [handleElementRename], and [PsiReferenceBase]'s default throws —
     * so this is implemented alongside [handleElementRename] to cover whichever the
     * platform invokes. Recompute the path's final segment from the new file's
     * name, preserving the directory and extension style, exactly as
     * [handleElementRename] does for the name path.
     */
    override fun bindToElement(element: PsiElement): PsiElement {
        val newFile = (element as? PsiFileSystemItem)?.virtualFile ?: return this.element
        val extensions = QiqTemplateResolver.candidateExtensions(this.element.project)
        return super.handleElementRename(QiqTemplateResolver.renamedPath(path, newFile.name, extensions))
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
