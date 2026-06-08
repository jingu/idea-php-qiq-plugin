package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * A navigation target for a section/block name occurrence (a `setSection` /
 * `setBlock` definition, or a `getSection` / `getBlock` usage — [head] picks the
 * label).
 *
 * A synthetic element (the occurrence lives in injected PHP text, not a stable
 * PSI symbol) whose [getPresentation] renders the directive plus `path:line`, so
 * the popup distinguishes same-named occurrences in different templates. The
 * `path:line` is folded into the presentable text rather than the location
 * string, because the declaration chooser wraps the location in parentheses;
 * folding it in keeps the chooser entries free of `(...)`.
 * Navigation falls back to [getContainingFile] + [getTextOffset] via
 * [FakePsiElement]/PsiElementBase. [getIcon] is fixed to null so the chooser does
 * not briefly flash the file-type icon before settling.
 */
class QiqSectionTarget(
    private val file: PsiFile,
    private val sectionName: String,
    private val head: String,
    private val nameOffset: Int,
) : FakePsiElement() {

    override fun getParent(): PsiElement = file

    override fun getContainingFile(): PsiFile = file

    override fun getTextOffset(): Int = nameOffset

    override fun getName(): String = sectionName

    override fun getIcon(open: Boolean): Icon? = null

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = "$head('$sectionName')  $pathAndLine"

        override fun getLocationString(): String? = null

        override fun getIcon(unused: Boolean): Icon? = null
    }

    /** Project-relative `path:line` (or the file name as a fallback). */
    private val pathAndLine: String
        get() {
            val virtualFile = file.virtualFile ?: return file.name
            val base = file.project.basePath
            val path = if (base != null && virtualFile.path.startsWith(base)) {
                virtualFile.path.removePrefix(base).removePrefix("/")
            } else {
                virtualFile.path
            }
            val line = file.viewProvider.document?.getLineNumber(nameOffset)?.plus(1)
            return if (line != null) "$path:$line" else path
        }
}
