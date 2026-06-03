package io.github.jingu.idea_qiq_plugin.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModel.ElementInfoProvider
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.icons.AllIcons
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiFile
import io.github.jingu.idea_qiq_plugin.ui.QiqIcons
import javax.swing.Icon

/** Structure tool-window outline for Qiq templates (sections, blocks, control blocks). */
class QiqStructureViewFactory : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder =
        object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                QiqStructureViewModel(psiFile, editor)

            override fun isRootNodeShown(): Boolean = true
        }
}

private class QiqStructureViewModel(psiFile: PsiFile, editor: Editor?) :
    TextEditorBasedStructureViewModel(editor, psiFile), ElementInfoProvider {

    private val root = QiqStructureViewElement(psiFile, null)

    override fun getRoot(): StructureViewTreeElement = root

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean =
        (element?.value as? QiqOutlineNode)?.children?.isEmpty() ?: false
}

/**
 * A structure node. [node] is null for the root (the file itself); otherwise it wraps
 * a [QiqOutlineNode] and navigates to that directive's offset on click.
 */
private class QiqStructureViewElement(
    private val file: PsiFile,
    private val node: QiqOutlineNode?,
) : StructureViewTreeElement {

    override fun getValue(): Any = node ?: file

    override fun getPresentation(): ItemPresentation =
        if (node == null) PresentationData(file.name, null, QiqIcons.FILE, null)
        else PresentationData(node.label, null, iconFor(node.kind), null)

    override fun getChildren(): Array<TreeElement> {
        val children = node?.children ?: QiqOutline.build(file.text)
        return children.map { QiqStructureViewElement(file, it) }.toTypedArray()
    }

    override fun navigate(requestFocus: Boolean) {
        val offset = node?.offset ?: return
        val virtualFile = file.virtualFile ?: return
        OpenFileDescriptor(file.project, virtualFile, offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = node != null && file.virtualFile != null

    override fun canNavigateToSource(): Boolean = canNavigate()

    private companion object {
        // No dedicated branch/loop icons exist in AllIcons, so control blocks
        // share one icon; the four categories stay visually distinct.
        fun iconFor(kind: QiqOutlineKind): Icon = when (kind) {
            QiqOutlineKind.DIRECTIVE -> AllIcons.Nodes.Include
            QiqOutlineKind.SECTION -> AllIcons.Nodes.Field
            QiqOutlineKind.BLOCK -> AllIcons.Nodes.Property
            QiqOutlineKind.CONTROL -> AllIcons.Nodes.Lambda
        }
    }
}
