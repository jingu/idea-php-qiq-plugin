package io.github.jingu.idea_qiq_plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Represents the inside of a native PHP block (<?php ... ?>) in a Qiq template.
 * Acts as a PsiLanguageInjectionHost so that PhpLanguage can be injected.
 */
class QiqPhpCodeElement(node: ASTNode) :
    ASTWrapperPsiElement(node), PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost {
        val range = textRange
        val document = containingFile.viewProvider.document ?: return this
        document.replaceString(range.startOffset, range.endOffset, text)
        return this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        object : LiteralTextEscaper<QiqPhpCodeElement>(this) {
            override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
                outChars.append(rangeInsideHost.substring(myHost.text))
                return true
            }

            override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int =
                rangeInsideHost.startOffset + offsetInDecoded

            override fun isOneLine(): Boolean = false
        }
}
