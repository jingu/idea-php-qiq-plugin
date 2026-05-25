package io.github.jingu.idea_qiq_plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes

class QiqCodeHost(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {

    enum class Kind { CODE, PRINT }

    /**
     * Classify this host so the injector can decide between statement and echo contexts.
     * Prefer the element type assigned by the parser, but keep a text-based fallback for
     * defensive compatibility with legacy PSI produced before the dedicated host node existed.
     */
    val kind: Kind
        get() = when (node.elementType) {
            QiqTokenTypes.RAW_CONTENT,
            QiqTokenTypes.ESCAPE_CONTENT,
            QiqTokenTypes.ESCAPE_H_CONTENT,
            QiqTokenTypes.ESCAPE_A_CONTENT,
            QiqTokenTypes.ESCAPE_J_CONTENT,
            QiqTokenTypes.ESCAPE_U_CONTENT,
            QiqTokenTypes.ESCAPE_C_CONTENT -> Kind.PRINT
            QiqTokenTypes.CODE_CONTENT,
            QiqTokenTypes.CODE_BODY -> Kind.CODE
            else -> {
                val t = text.trimStart()
                if (t.isEmpty()) Kind.CODE else when (t[0]) {
                    '=', 'h', 'j', 'a', 'c', 'u' -> Kind.PRINT
                    else -> Kind.CODE
                }
            }
        }

    fun isPrintLike(): Boolean = kind == Kind.PRINT

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost =
        ElementManipulators.handleContentChange(this, text)

    override fun createLiteralTextEscaper(): LiteralTextEscaper<QiqCodeHost> =
        object : LiteralTextEscaper<QiqCodeHost>(this) {
            override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
                // No special escaping inside Qiq CODE_BODY; map 1:1
                val text = myHost.text
                outChars.append(text, rangeInsideHost.startOffset, rangeInsideHost.endOffset)
                return true
            }

            override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
                val result = rangeInsideHost.startOffset + offsetInDecoded
                return if (result <= rangeInsideHost.endOffset) result else -1
            }

            override fun isOneLine(): Boolean = false
        }
}
