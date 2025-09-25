package io.github.jingu.idea_qiq_plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.LiteralTextEscaper
import com.intellij.openapi.util.TextRange

class QiqPhpHost(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost = this

    override fun createLiteralTextEscaper(): LiteralTextEscaper<QiqPhpHost> =
        object : LiteralTextEscaper<QiqPhpHost>(this) {
            override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
                // No special escaping inside PHP blocks; map 1:1
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
