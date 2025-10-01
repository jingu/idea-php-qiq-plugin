package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import io.github.jingu.idea_qiq_plugin.lang.QiqFileTypeOverrider
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage
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

    private fun isInQiqFile(element: PsiElement): Boolean {
        val project = element.project
        val ilm = InjectedLanguageManager.getInstance(project)
        val topLevel = ilm.getTopLevelFile(element) ?: element.containingFile ?: return false

        val viewProvider = topLevel.viewProvider
        if (viewProvider is TemplateLanguageFileViewProvider && viewProvider.baseLanguage == QiqTemplateLanguage) {
            return true
        }

        if (topLevel.language == QiqTemplateLanguage) {
            return true
        }

        val fileType = topLevel.virtualFile?.fileType
        if (fileType == QiqFileType) {
            return true
        }

        // 最後のフォールバック: 拡張子チェック
        val name = topLevel.virtualFile?.name ?: return false
        if (name.endsWith(".qiq") || name.endsWith(".qiq.php")) return true

        return topLevel.virtualFile?.getUserData(QiqFileTypeOverrider.QIQ_MARKER) == true
    }

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

    override fun getVariants(): Array<Any> = emptyArray()
}
