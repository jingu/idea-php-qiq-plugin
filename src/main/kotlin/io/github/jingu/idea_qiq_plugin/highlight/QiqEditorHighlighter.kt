package io.github.jingu.idea_qiq_plugin.highlight

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes

class QiqEditorHighlighter(
    colors: EditorColorsScheme,
    project: Project?,
    virtualFile: VirtualFile?
) : LayeredLexerEditorHighlighter(QiqSyntaxHighlighter(), colors) {

    init {
        // TEMPLATE_DATA 上に HTML のハイライターをレイヤー登録
        val htmlHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(HTMLLanguage.INSTANCE, project, virtualFile)
        registerLayer(QiqTokenTypes.TEMPLATE_DATA, LayerDescriptor(htmlHighlighter, ""))
    }
}
