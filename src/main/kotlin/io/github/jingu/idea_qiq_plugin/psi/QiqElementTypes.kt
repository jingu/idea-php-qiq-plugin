package io.github.jingu.idea_qiq_plugin.psi

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenType

object QiqElementTypes {
    @JvmField val FILE: IFileElementType = IFileElementType(QiqTemplateLanguage)
    // 必要であれば PSI ノード用の IElementType をここに追加
    @JvmStatic fun element(debugName: String): IElementType = QiqTokenType(debugName)
}
