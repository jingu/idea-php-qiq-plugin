package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.github.jingu.idea_qiq_plugin.block.QiqBlockModel
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

class QiqEnterHandler : EnterHandlerDelegateAdapter() {

    private fun isQiqFile(file: PsiFile): Boolean {
        val lp = file.viewProvider
        return file.language.isKindOf(QiqTemplateLanguage)
                || lp.baseLanguage.isKindOf(QiqTemplateLanguage)
                || lp.languages.any { it.isKindOf(QiqTemplateLanguage) }
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): Result {
        if (!isQiqFile(file)) return Result.Continue

        val doc = editor.document
        val caret = editor.caretModel.currentCaret
        val curOffset = caret.offset
        val text = doc.charsSequence

        // いまの行（Enterで新しくできた行）
        val curLine = doc.getLineNumber(curOffset)
        if (curLine == 0) return Result.Continue

        // 直前行のテキストを取得
        val prevLine = curLine - 1
        val prevStart = doc.getLineStartOffset(prevLine)
        val prevEnd = doc.getLineEndOffset(prevLine)
        val prevText = text.subSequence(prevStart, prevEnd).toString()

        // 直前行の末尾（空白除去）が "}}" で終わらなければ対象外
        val prevTrimmed = prevText.trimEnd()
        if (!prevTrimmed.endsWith("}}")) return Result.Continue

        // "}}" の位置（prevStart..prevEnd の中での絶対オフセット）
        val closeAfter = prevStart + prevTrimmed.length
        // 対応する "{{" を左へスキャン
        val openPos = findLastOpenDoubleBrace(text, closeAfter - 2) ?: return Result.Continue

        // 中身を取り出し、ブロック opener か判定（ブロックセット・コロンルールは
        // QiqBlockModel に一元化）
        val inside = text.subSequence(openPos + 2, closeAfter - 2).toString().trim()
        val blockType = QiqBlockModel.openerTypeOf(inside) ?: return Result.Continue
        val closer = blockType.closeText

        // この opener が文書全体で既に閉じられているなら挿入しない
        if (isOpenerClosed(text, openPos)) {
            return Result.Continue
        }

        // 既に次の非空行が endX の場合は重複挿入しない
        val nextNonEmpty = findNextNonEmptyLineText(doc, doc.getLineStartOffset(curLine))
        if (nextNonEmpty?.trimStart()?.startsWith("{{ $closer") == true) {
            return Result.Continue
        }

        // インデントは直前行の先頭空白を継承
        val indent = prevText.takeWhile { it == ' ' || it == '\t' }
        val endLine = "$indent{{ $closer }}"

        // いまのキャレット位置（空行の先頭）に「改行＋endLine」を差し込む
        // ＝ 現在行は空行のまま、次行に endLine を作る → キャレットは空行に残る
        WriteCommandAction.runWriteCommandAction(file.project) {
            val insertion = "\n$endLine"
            doc.insertString(curOffset, insertion)
            // キャレットはそのまま（空行の先頭）に置く
            caret.moveToOffset(curOffset)
        }

        return Result.Stop // これ以上の post-process は不要
    }

    // "{{" を左へ走査して見つける
    private fun findLastOpenDoubleBrace(text: CharSequence, fromExclusive: Int): Int? {
        var i = fromExclusive
        while (i >= 1) {
            if (text[i - 1] == '{' && text[i] == '{') return i - 1
            i--
        }
        return null
    }

    /**
     * Whether the opener whose `{{` starts at [openPos] is already balanced by a
     * closer somewhere in [text]. Delegates the pairing to [QiqBlockModel] so the
     * Enter handler and the block-range features agree on what counts as closed.
     */
    internal fun isOpenerClosed(text: CharSequence, openPos: Int): Boolean =
        QiqBlockModel.computeBlockRanges(text).any { it.open.startOffset == openPos }

    // fromOffset 以降で最初の非空行テキストを返す
    private fun findNextNonEmptyLineText(
        doc: com.intellij.openapi.editor.Document,
        fromOffset: Int
    ): String? {
        if (fromOffset >= doc.textLength) return null
        var line = doc.getLineNumber(fromOffset)
        val last = doc.lineCount - 1
        while (line <= last) {
            val s = doc.charsSequence.subSequence(
                doc.getLineStartOffset(line),
                doc.getLineEndOffset(line)
            ).toString()
            if (s.isNotBlank()) return s
            line++
        }
        return null
    }
}
