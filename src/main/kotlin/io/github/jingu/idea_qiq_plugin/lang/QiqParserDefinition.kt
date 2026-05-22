package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import io.github.jingu.idea_qiq_plugin.lexer.QiqLexerAdapter
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost
import io.github.jingu.idea_qiq_plugin.psi.QiqElementTypes
import io.github.jingu.idea_qiq_plugin.psi.QiqPhpHost

class QiqParserDefinition : ParserDefinition {

    companion object {
        // Qiq のルート File 要素（テンプレート言語側の PSI ルート）
        val FILE: IFileElementType = QiqElementTypes.FILE

        // テンプレートデータ（外側の実コンテンツ = HTML/PHP）を抽出するための要素
        // 第3引数: テンプレートデータのトークン (= TEMPLATE_DATA)
        // 第4引数: 外側言語以外の範囲 (= OUTER: Qiq タグ等)
        val TEMPLATE_DATA_ELEMENT: TemplateDataElementType = TemplateDataElementType(
            "QIQ_TEMPLATE_DATA",
            QiqTemplateLanguage,
            QiqTokenTypes.TEMPLATE_DATA,
            QiqTokenTypes.OUTER
        )
    }

    override fun createLexer(project: Project): Lexer = QiqLexerAdapter()

    override fun createParser(project: Project): PsiParser = PsiParser { root, builder ->
        val rootMarker = builder.mark()
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            val blockKind = tokenType.blockKind()
            when {
                tokenType.isPhpOpen() -> parsePhpBlock(builder)
                blockKind != null -> parseQiqBlock(builder, blockKind)
                else -> builder.advanceLexer()
            }
        }
        rootMarker.done(root)
        builder.treeBuilt
    }

    // ここは Qiq の FILE（テンプレート言語側のルート）を返す
    override fun getFileNodeType(): IFileElementType = FILE

    // 空白は TokenType.WHITE_SPACE のみ
    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    // コメントトークンがなければ空でOK
    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    // 文字列リテラルがあるならここに登録（不要なら EMPTY でOK）
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(QiqTokenTypes.STRING)

    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        QiqTokenTypes.CODE_BODY,
        QiqTokenTypes.CODE_CONTENT,
        QiqTokenTypes.RAW_CONTENT,
        QiqTokenTypes.ESCAPE_CONTENT,
        QiqTokenTypes.ESCAPE_H_CONTENT,
        QiqTokenTypes.ESCAPE_A_CONTENT,
        QiqTokenTypes.ESCAPE_J_CONTENT,
        QiqTokenTypes.ESCAPE_U_CONTENT,
        QiqTokenTypes.ESCAPE_C_CONTENT -> QiqCodeHost(node)
        QiqTokenTypes.PHP_CONTENT,
        QiqTokenTypes.PHP_BLOCK_CONTENT -> QiqPhpHost(node)
        else -> ASTWrapperPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = QiqFile(viewProvider)

    override fun spaceExistenceTypeBetweenTokens(
        left: ASTNode,
        right: ASTNode
    ) = ParserDefinition.SpaceRequirements.MAY
}

private enum class QiqBlockKind(
    val contentType: IElementType,
    private vararg val closingTokens: IElementType
) {
    CODE(
        QiqTokenTypes.CODE_CONTENT,
        QiqTokenTypes.RBRACE2,
        QiqTokenTypes.CODE_CLOSE,
        QiqTokenTypes.CODE_CLOSE_TOKEN
    ),
    RAW(
        QiqTokenTypes.RAW_CONTENT,
        QiqTokenTypes.RBRACE_EQ,
        QiqTokenTypes.CLOSE_EQ
    ),
    ESCAPE_ATTR(
        QiqTokenTypes.ESCAPE_A_CONTENT,
        QiqTokenTypes.RBRACEA,
        QiqTokenTypes.RBRACEC,
        QiqTokenTypes.CLOSE_A,
        QiqTokenTypes.CLOSE_C
    ),
    ESCAPE_CSS(
        QiqTokenTypes.ESCAPE_C_CONTENT,
        QiqTokenTypes.RBRACEC,
        QiqTokenTypes.RBRACEA,
        QiqTokenTypes.CLOSE_C,
        QiqTokenTypes.CLOSE_A
    ),
    ESCAPE_HTML(
        QiqTokenTypes.ESCAPE_H_CONTENT,
        QiqTokenTypes.RBRACEH,
        QiqTokenTypes.CLOSE_H
    ),
    ESCAPE_JSON(
        QiqTokenTypes.ESCAPE_J_CONTENT,
        QiqTokenTypes.RBRACEJ,
        QiqTokenTypes.CLOSE_J
    ),
    ESCAPE_URL(
        QiqTokenTypes.ESCAPE_U_CONTENT,
        QiqTokenTypes.RBRACEU,
        QiqTokenTypes.CLOSE_U
    );

    fun isClosing(tokenType: IElementType?): Boolean = tokenType != null && closingTokens.contains(tokenType)
}

private fun IElementType?.blockKind(): QiqBlockKind? = when (this) {
    QiqTokenTypes.CODE_OPEN,
    QiqTokenTypes.LBRACE2,
    QiqTokenTypes.CODE_OPEN_TOKEN -> QiqBlockKind.CODE
    QiqTokenTypes.RAW_OPEN,
    QiqTokenTypes.LBRACE_EQ,
    QiqTokenTypes.OPEN_EQ -> QiqBlockKind.RAW
    QiqTokenTypes.ESCAPE_A_OPEN,
    QiqTokenTypes.LBRACEA,
    QiqTokenTypes.OPEN_A -> QiqBlockKind.ESCAPE_ATTR
    QiqTokenTypes.ESCAPE_C_OPEN,
    QiqTokenTypes.LBRACEC,
    QiqTokenTypes.OPEN_C -> QiqBlockKind.ESCAPE_CSS
    QiqTokenTypes.ESCAPE_H_OPEN,
    QiqTokenTypes.LBRACEH,
    QiqTokenTypes.OPEN_H -> QiqBlockKind.ESCAPE_HTML
    QiqTokenTypes.ESCAPE_J_OPEN,
    QiqTokenTypes.LBRACEJ,
    QiqTokenTypes.OPEN_J -> QiqBlockKind.ESCAPE_JSON
    QiqTokenTypes.ESCAPE_U_OPEN,
    QiqTokenTypes.LBRACEU,
    QiqTokenTypes.OPEN_U -> QiqBlockKind.ESCAPE_URL
    else -> null
}

private fun IElementType?.isPhpOpen(): Boolean = when (this) {
    QiqTokenTypes.PHP_OPEN,
    QiqTokenTypes.OPEN_PHP -> true
    else -> false
}

private fun parseQiqBlock(builder: PsiBuilder, kind: QiqBlockKind) {
    builder.advanceLexer() // consume the opening delimiter
    val hostMarker = builder.mark()
    while (!builder.eof() && !kind.isClosing(builder.tokenType)) {
        builder.advanceLexer()
    }
    hostMarker.done(kind.contentType)
    if (!builder.eof() && kind.isClosing(builder.tokenType)) {
        builder.advanceLexer() // consume closing delimiter
    }
}

private fun parsePhpBlock(builder: PsiBuilder) {
    builder.advanceLexer() // consume <?php
    val hostMarker = builder.mark()
    while (!builder.eof() && !builder.tokenType.isPhpClose()) {
        builder.advanceLexer()
    }
    hostMarker.done(QiqTokenTypes.PHP_CONTENT)
    if (!builder.eof() && builder.tokenType.isPhpClose()) {
        builder.advanceLexer() // consume ?>
    }
}

private fun IElementType?.isPhpClose(): Boolean = when (this) {
    QiqTokenTypes.PHP_CLOSE,
    QiqTokenTypes.CLOSE_PHP -> true
    else -> false
}
