package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.block.QiqBlockType
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.util.QiqSectionCall
import io.github.jingu.idea_qiq_plugin.util.QiqSectionIndex

/**
 * Go to Declaration from a `getSection('x')` / `getBlock('x')` name to the
 * matching `setSection('x')` / `setBlock('x')` definitions, anywhere under the
 * detected template roots. A name can be set by more than one template, so the
 * reference is poly-variant.
 */
class QiqSectionReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, ctx: ProcessingContext): Array<PsiReference> {
                    if (!QiqInjectionSupport.isInQiqFile(element)) return PsiReference.EMPTY_ARRAY

                    val literal = element as? StringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    val type = QiqSectionCall.readerTypeForArg(literal) ?: return PsiReference.EMPTY_ARRAY

                    val name = literal.contents
                    if (name.isEmpty()) return PsiReference.EMPTY_ARRAY

                    val range = TextRange.create(1, element.textLength - 1).takeIf { it.startOffset < it.endOffset }
                        ?: return PsiReference.EMPTY_ARRAY

                    val ilm = InjectedLanguageManager.getInstance(element.project)
                    val contextVirtualFile = ilm.getTopLevelFile(element)?.virtualFile
                        ?: element.containingFile?.virtualFile

                    return arrayOf(QiqSectionReference(element, name, type, range, contextVirtualFile))
                }
            },
        )
    }
}

class QiqSectionReference(
    element: PsiElement,
    private val name: String,
    private val type: QiqBlockType,
    rangeInElement: TextRange,
    private val contextVirtualFile: VirtualFile?,
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val contextFile = contextVirtualFile ?: element.containingFile?.virtualFile ?: return emptyArray()
        val psiManager = com.intellij.psi.PsiManager.getInstance(element.project)
        val head = if (type == QiqBlockType.BLOCK) "setBlock" else "setSection"
        return QiqSectionIndex.definitionsByName(element.project, contextFile, name, type)
            .mapNotNull { location ->
                val psiFile = psiManager.findFile(location.file) ?: return@mapNotNull null
                // A synthetic target whose presentation carries the file path, so
                // same-named definitions in different templates are distinguishable.
                val target = QiqSectionTarget(psiFile, location.name, head, location.nameRange.startOffset)
                PsiElementResolveResult(target)
            }
            .toTypedArray()
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
