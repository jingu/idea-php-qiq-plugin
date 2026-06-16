package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.github.jingu.idea_qiq_plugin.block.QiqBlockModel
import io.github.jingu.idea_qiq_plugin.block.QiqBlockType
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

class QiqEnterHandler : EnterHandlerDelegateAdapter() {

    private companion object {
        // 行末に現れる HTML 開きタグ（attrs に < や > は含まない）。
        private val TRAILING_OPEN_TAG = Regex("<([a-zA-Z][a-zA-Z0-9-]*)([^<>]*)>\\s*$")

        // `{{ ... }}` の中身先頭にある `$this->` レシーバ（クローザー照合の正規化用）。
        private val THIS_RECEIVER = Regex("^\\\$this\\s*->\\s*")
    }

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
        val prevTrimmed = prevText.trimEnd()

        val curLineStart = doc.getLineStartOffset(curLine)
        val curLineEnd = doc.getLineEndOffset(curLine)

        // キャレット手前が空白以外（行途中の Enter 等）なら触らずデフォルトに任せる。
        if (!text.subSequence(curLineStart, curOffset).isBlank()) return Result.Continue

        // クローザー / 本文はオープナー行のインデント基準で揃える。
        val baseIndent = prevText.takeWhile { it == ' ' || it == '\t' }
        val bodyIndent = baseIndent + singleIndentUnit(file)

        // --- ケース A: Qiq ブロックオープナー（直前行が "}}" で終わる） ---
        if (prevTrimmed.endsWith("}}")) {
            return handleQiqOpener(
                file, doc, caret, text, prevStart, prevTrimmed,
                curLine, curOffset, curLineStart, curLineEnd, baseIndent, bodyIndent
            )
        }

        // --- ケース B: HTML 開きタグ。Qiq がベース言語のため、HTML 標準の「タグ間
        // Enter」と協調しても閉じタグ行のインデントが崩れる。どちらが先に走っても
        // 正しくなるよう、キャレット行とその直下の閉じタグ行の両方を補正する。 ---
        val tag = trailingOpenTagName(prevTrimmed) ?: return Result.Continue
        val closerTag = "</$tag>"
        val rest = text.subSequence(curOffset, curLineEnd).toString()
        val restTrimmed = rest.trimStart()

        // B1: まだ 2 行に割れただけで、キャレット行が `</div>` を抱えている場合。
        // クローザーを 1 行下へ送り、間にキャレットを 1 段深く置く。
        if (restTrimmed.startsWith(closerTag, ignoreCase = true)) {
            WriteCommandAction.runWriteCommandAction(file.project) {
                doc.replaceString(curLineStart, curLineEnd, "$bodyIndent\n$baseIndent$restTrimmed")
                caret.moveToOffset(curLineStart + bodyIndent.length)
            }
            return Result.Stop
        }

        // ここから先はキャレット行が空であることが前提（行途中 Enter は対象外）。
        if (rest.isNotBlank()) return Result.Continue

        // B2: 直下に対応する閉じタグ行がある（HTML 標準ハンドラが空行を挿入済みだが
        // 閉じタグのインデントを壊している）。両行を一括で整える。
        val closerLine = nextNonBlankLine(doc, curLine)
        if (closerLine != null) {
            val cs = doc.getLineStartOffset(closerLine)
            val ce = doc.getLineEndOffset(closerLine)
            val closerText = text.subSequence(cs, ce).toString().trim()
            if (closerText.startsWith(closerTag, ignoreCase = true)) {
                WriteCommandAction.runWriteCommandAction(file.project) {
                    // 下の行から書き換えてキャレット行のオフセットを保つ。
                    doc.replaceString(cs, ce, "$baseIndent$closerText")
                    doc.replaceString(curLineStart, curLineEnd, bodyIndent)
                    caret.moveToOffset(curLineStart + bodyIndent.length)
                }
                return Result.Stop
            }
        }

        // B3: 単独の開きタグ。キャレット行を 1 段深くするだけ。
        WriteCommandAction.runWriteCommandAction(file.project) {
            doc.replaceString(curLineStart, curLineEnd, bodyIndent)
            caret.moveToOffset(curLineStart + bodyIndent.length)
        }
        return Result.Stop
    }

    // fromLine の次以降で最初の非空行の行番号を返す。
    private fun nextNonBlankLine(doc: com.intellij.openapi.editor.Document, fromLine: Int): Int? {
        var line = fromLine + 1
        val last = doc.lineCount - 1
        while (line <= last) {
            val s = doc.charsSequence.subSequence(
                doc.getLineStartOffset(line),
                doc.getLineEndOffset(line)
            )
            if (s.isNotBlank()) return line
            line++
        }
        return null
    }

    /**
     * Qiq ブロックオープナー直後の Enter を処理する。キャレット行を一段深いインデントに
     * し、オープナーが未クローズなら直下に対応するクローザーを自動挿入する。
     */
    private fun handleQiqOpener(
        file: PsiFile,
        doc: com.intellij.openapi.editor.Document,
        caret: com.intellij.openapi.editor.Caret,
        text: CharSequence,
        prevStart: Int,
        prevTrimmed: String,
        curLine: Int,
        curOffset: Int,
        curLineStart: Int,
        curLineEnd: Int,
        baseIndent: String,
        bodyIndent: String,
    ): Result {
        // "}}" の位置 → 対応する "{{" を左へスキャン。
        val closeAfter = prevStart + prevTrimmed.length
        val openPos = findLastOpenDoubleBrace(text, closeAfter - 2) ?: return Result.Continue

        // 中身がブロック opener か判定（ブロックセット・コロンルールは QiqBlockModel に一元化）。
        val inside = text.subSequence(openPos + 2, closeAfter - 2).toString().trim()
        val blockType = QiqBlockModel.openerTypeOf(inside) ?: return Result.Continue
        val closer = blockType.closeText

        // キャレット行の残りが空でなければ（行途中の Enter 等）デフォルトに任せる。
        if (!text.subSequence(curOffset, curLineEnd).isBlank()) return Result.Continue

        // クローザー挿入は、この opener が未クローズで直下に既存クローザーが無い場合だけ。
        // 直下クローザーの照合は大文字小文字と `$this->` を許容（QiqBlockModel と同じ正規化）。
        val nextNonEmpty = findNextNonEmptyLineText(doc, doc.getLineStartOffset(curLine))
        val closerAlreadyBelow = nextNonEmpty != null && isCloserLineFor(nextNonEmpty, blockType)
        val insertCloser = !isOpenerClosed(text, openPos) && !closerAlreadyBelow

        WriteCommandAction.runWriteCommandAction(file.project) {
            doc.replaceString(curLineStart, curLineEnd, bodyIndent)
            val caretPos = curLineStart + bodyIndent.length
            if (insertCloser) {
                doc.insertString(caretPos, "\n$baseIndent{{ $closer }}")
            }
            caret.moveToOffset(caretPos)
        }
        return Result.Stop
    }

    /**
     * 行末が HTML 開きタグなら、そのタグ名（小文字）を返す。void 要素・自己終了タグ・
     * 閉じタグは対象外。
     */
    private fun trailingOpenTagName(line: String): String? {
        val m = TRAILING_OPEN_TAG.find(line) ?: return null
        if (m.groupValues[2].trimEnd().endsWith("/")) return null // 自己終了タグ
        val name = m.groupValues[1].lowercase()
        return if (name in HTML_VOID_ELEMENTS) null else name
    }

    /**
     * [lineText] が [blockType] の閉じディレクティブ（`{{ endif }}` 等）かどうか。
     * QiqBlockModel と同様、キーワードは大文字小文字を無視し、`{{ $this->endSection() }}`
     * のような明示レシーバも許容する。
     */
    private fun isCloserLineFor(lineText: String, blockType: QiqBlockType): Boolean {
        val trimmed = lineText.trimStart()
        if (!trimmed.startsWith("{{")) return false
        val close = trimmed.indexOf("}}")
        if (close < 0) return false
        val head = trimmed.substring(2, close).trim()
            .replaceFirst(THIS_RECEIVER, "")
            .takeWhile { it.isLetterOrDigit() || it == '_' }
        return head.equals(blockType.closeHead, ignoreCase = true)
    }

    // 設定に従った 1 段分のインデント文字列（タブ or スペース）。
    private fun singleIndentUnit(file: PsiFile): String {
        val options = CodeStyle.getIndentOptions(file)
        return if (options.USE_TAB_CHARACTER) "\t" else " ".repeat(options.INDENT_SIZE.coerceAtLeast(1))
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
