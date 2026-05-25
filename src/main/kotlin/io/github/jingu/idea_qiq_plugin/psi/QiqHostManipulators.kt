package io.github.jingu.idea_qiq_plugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes

/**
 * Lets refactorings (Rename, Move, etc.) propagate edits made inside the
 * injected PHP back to the Qiq host text. Without this, IntelliJ would find
 * the references in the injected fragment but silently drop the rewrite
 * because PsiLanguageInjectionHost.updateText returns the original host
 * unchanged.
 *
 * Strategy: wrap the new text in a parseable Qiq snippet derived from the
 * host's element type (e.g. ESCAPE_H_CONTENT → `{{h NEW }}`), re-parse it,
 * locate the matching host in the synthetic tree, then graft its AST
 * children into the original host node. The host instance itself is
 * preserved so callers that already hold a reference keep working.
 */
class QiqCodeHostManipulator : AbstractElementManipulator<QiqCodeHost>() {
    override fun handleContentChange(
        element: QiqCodeHost,
        range: TextRange,
        newContent: String,
    ): QiqCodeHost {
        val newHostText = range.replace(element.text, newContent)
        val snippet = wrapForElementType(element.node.elementType, newHostText)
        val replacement = reparseHost(element, snippet, element.node.elementType)
        element.node.replaceAllChildrenToChildrenOf(replacement.node)
        return element
    }

    private fun reparseHost(
        anchor: QiqCodeHost,
        snippet: String,
        expectedType: IElementType,
    ): QiqCodeHost {
        val tempFile = PsiFileFactory.getInstance(anchor.project)
            .createFileFromText("_qiq_rename.qiq", QiqFileType, snippet)
        return PsiTreeUtil.collectElementsOfType(tempFile, QiqCodeHost::class.java)
            .firstOrNull { it.node.elementType == expectedType }
            ?: throw IncorrectOperationException(
                "Failed to re-parse Qiq host (type=$expectedType, snippet=$snippet)",
            )
    }

    private fun wrapForElementType(type: IElementType, text: String): String = when (type) {
        QiqTokenTypes.ESCAPE_H_CONTENT -> "{{h$text}}"
        QiqTokenTypes.ESCAPE_A_CONTENT -> "{{a$text}}"
        QiqTokenTypes.ESCAPE_J_CONTENT -> "{{j$text}}"
        QiqTokenTypes.ESCAPE_U_CONTENT -> "{{u$text}}"
        QiqTokenTypes.ESCAPE_C_CONTENT -> "{{c$text}}"
        QiqTokenTypes.ESCAPE_CONTENT -> "{{h$text}}"
        QiqTokenTypes.RAW_CONTENT -> "{{=$text}}"
        else -> "{{$text}}"
    }
}

class QiqPhpHostManipulator : AbstractElementManipulator<QiqPhpHost>() {
    override fun handleContentChange(
        element: QiqPhpHost,
        range: TextRange,
        newContent: String,
    ): QiqPhpHost {
        val newHostText = range.replace(element.text, newContent)
        val snippet = "<?php$newHostText?>"
        val tempFile = PsiFileFactory.getInstance(element.project)
            .createFileFromText("_qiq_php_rename.qiq", QiqFileType, snippet)
        val replacement = PsiTreeUtil.findChildOfType(tempFile, QiqPhpHost::class.java)
            ?: throw IncorrectOperationException(
                "Failed to re-parse Qiq PHP host (snippet=$snippet)",
            )
        element.node.replaceAllChildrenToChildrenOf(replacement.node)
        return element
    }
}
