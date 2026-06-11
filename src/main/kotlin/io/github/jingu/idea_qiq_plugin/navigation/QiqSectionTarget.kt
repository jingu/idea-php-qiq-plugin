package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * A navigation target for a Qiq section name occurrence (a `setSection`
 * definition, or a `getSection` / `hasSection` usage — [head] is the directive
 * name used as the label). Blocks are out of the name workflow (`getBlock()` is
 * argument-less), so there are no block targets.
 *
 * A synthetic element (the occurrence lives in injected PHP text, not a stable
 * PSI symbol) whose [getPresentation] renders the directive as the main text and
 * the `path:line` as the (grey, parenthesised) location string, so the chooser
 * distinguishes same-named occurrences in different templates. Navigation falls
 * back to [getContainingFile] + [getTextOffset] via [FakePsiElement]/
 * PsiElementBase. The icon is the containing template file's icon, returned
 * consistently from both [getIcon] and the presentation so the chooser shows it
 * steadily rather than flashing it on and then clearing it.
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

    override fun getIcon(open: Boolean): Icon? = fileIcon

    // FakePsiElement.getIcon(int) is final and delegates to this presentation's
    // getIcon, so returning the file icon here is what the chooser shows.
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = "$head('$sectionName')"

        override fun getLocationString(): String = pathAndLine

        override fun getIcon(unused: Boolean): Icon? = fileIcon
    }

    /** The containing template file's icon, shown consistently to avoid a flash. */
    private val fileIcon: Icon?
        get() = file.getIcon(0)

    /** Project-relative `path:line` (or the file name as a fallback). */
    private val pathAndLine: String
        get() {
            val virtualFile = file.virtualFile ?: return file.name
            // Compare against `base/` (not `base`) so a sibling like `/proj2` is
            // not treated as project-relative to base `/proj`.
            val base = file.project.basePath?.removeSuffix("/")
            val path = if (base != null && virtualFile.path.startsWith("$base/")) {
                virtualFile.path.removePrefix("$base/")
            } else {
                virtualFile.path
            }
            val line = file.viewProvider.document?.getLineNumber(nameOffset)?.plus(1)
            return if (line != null) "$path:$line" else path
        }
}
