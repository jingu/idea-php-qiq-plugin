package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

class QiqTypedHandler : TypedHandlerDelegate() {
    private val validQiqTokens = setOf("{{", "{{=", "{{h", "{{j", "{{a", "{{u", "{{c")

    private fun isQiqFile(file: PsiFile): Boolean {
        // Qiq をテンプレート言語にしている場合でも、baseLanguage / templateData のいずれかで判定できるようにゆるく見る
        val lp = file.viewProvider
        return file.language.isKindOf(QiqTemplateLanguage)
                || lp.baseLanguage.isKindOf(QiqTemplateLanguage)
                || lp.languages.any { it.isKindOf(QiqTemplateLanguage) }
    }

    override fun beforeCharTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType,
    ): Result {
        if (c != '{' || !isQiqFile(file)) return Result.CONTINUE

        val doc = editor.document
        val offset = editor.caretModel.offset
        val text = doc.charsSequence

        // Completing the Qiq opener `{{`: the platform's brace-pair logic treats it
        // like a lone `{` and auto-inserts a single stray `}` (`{{|}`); its usual
        // guard is bypassed when nothing follows (end of line), so it fires even on
        // an empty line. Qiq closers are the two-char `}}`, completed on space by
        // charTyped below, so we insert the second `{` ourselves and STOP, keeping
        // the stray brace from ever appearing. Inside an injected PHP fragment we
        // defer, so PHP's own `{`/`}` pairing keeps working.
        val completesOpener =
            offset > 0 && text[offset - 1] == '{' && (offset < 2 || text[offset - 2] != '{')
        if (completesOpener &&
            InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset) == null
        ) {
            WriteCommandAction.runWriteCommandAction(project) {
                CommandProcessor.getInstance().runUndoTransparentAction {
                    doc.insertString(offset, "{")
                    editor.caretModel.moveToOffset(offset + 1)
                }
            }
            return Result.STOP
        }
        return Result.CONTINUE
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!isQiqFile(file)) return Result.CONTINUE

        val doc = editor.document
        val caret = editor.caretModel
        val offset = caret.offset
        val text = doc.charsSequence

        // 1) "{{", "{{=", "{{h" ... + [space] のとき "}}" 補完
        if (c == ' ') {
            val tokens = validQiqTokens.sortedByDescending { it.length }
            val matched = tokens.firstOrNull { tok ->
                val start = offset - 1 - tok.length
                start >= 0 && text.subSequence(start, offset - 1).toString() == tok
            }
            if (matched != null && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
                val hasClosing =
                    (offset + 2 <= text.length && text.subSequence(offset, offset + 2).toString() == "}}") ||
                            (offset + 3 <= text.length && text.subSequence(offset, offset + 3).toString() == " }}")
                if (!hasClosing) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        CommandProcessor.getInstance().runUndoTransparentAction {
                            doc.insertString(offset, " }}")
                        }
                    }
                    return Result.STOP
                }
            }
        }

        return Result.CONTINUE
    }
}
