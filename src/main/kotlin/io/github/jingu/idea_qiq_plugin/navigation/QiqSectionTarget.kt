package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import io.github.jingu.idea_qiq_plugin.block.QiqBlockType
import javax.swing.Icon

/**
 * A navigation target for a `setSection`/`setBlock` name definition.
 *
 * A synthetic element (the definition lives in injected PHP text, not a stable
 * PSI symbol) whose [getPresentation] renders the directive *and the file path*,
 * so the "Choose Declaration" popup distinguishes definitions of the same name in
 * different templates. Navigation falls back to [getContainingFile] +
 * [getTextOffset] via [FakePsiElement]/PsiElementBase.
 */
class QiqSectionTarget(
    private val file: PsiFile,
    private val sectionName: String,
    private val type: QiqBlockType,
    private val nameOffset: Int,
) : FakePsiElement() {

    override fun getParent(): PsiElement = file

    override fun getContainingFile(): PsiFile = file

    override fun getTextOffset(): Int = nameOffset

    override fun getName(): String = sectionName

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String {
            val head = if (type == QiqBlockType.BLOCK) "setBlock" else "setSection"
            return "$head('$sectionName')"
        }

        override fun getLocationString(): String? {
            val virtualFile = file.virtualFile ?: return file.name
            val base = file.project.basePath
            return if (base != null && virtualFile.path.startsWith(base)) {
                virtualFile.path.removePrefix(base).removePrefix("/")
            } else {
                virtualFile.path
            }
        }

        override fun getIcon(unused: Boolean): Icon? = null
    }
}
