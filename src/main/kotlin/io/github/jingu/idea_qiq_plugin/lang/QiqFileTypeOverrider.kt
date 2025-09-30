package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class QiqFileTypeOverrider : FileTypeOverrider {

    companion object {
        val QIQ_MARKER: Key<Boolean> = Key.create("qiq.overridden.as.qiq")

        private val REENTRANT_GUARD: Key<Boolean> = Key.create("qiq.overrider.guard")

        // よく使うトークンで早期判定（軽いチェック）
        private fun quickLooksLikeQiq(text: CharSequence): Boolean {
            if (!text.contains("{{")) return false
            if (text.contains("{{=") || text.contains("{{h") || text.contains("{{a")) return true
            // 少し厳しめに：テンプレ制御
            return Regex("""\{\{\s*(if|foreach|setSection|extends|block|render|setLayout)\b""")
                .containsMatchIn(text)
        }
    }

    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        // 再入防止
        if (file.getUserData(REENTRANT_GUARD) == true) return null
        file.putUserData(REENTRANT_GUARD, true)
        try {
            if (file.isDirectory) return null

            // 既に Qiq として扱ったファイルは継続して Qiq を返す（再判定で PHP に戻さない）
            if (file.getUserData(QIQ_MARKER) == true) {
                return QiqFileType
            }

            // 1) Qiq専用拡張子は即スキップ（拡張子のみでQiqに確定させる）
            //    注意: file.extension は最後のドット以降のみ（".qiq.php" の extension は "php"）
            val nameLower = file.nameSequence.toString().lowercase()
            if (nameLower.endsWith(".qiq") || nameLower.endsWith(".qiq.php")) {
                return null
            }

            // 2) 対象候補のみ内容判定（誤検知＆無駄I/O削減）
            val ext = file.extension?.lowercase()
            val candidate = ext == null || ext in setOf("php", "phtml", "html", "htm", "tpl")
            if (!candidate) return null

            // 3) サイズ上限（巨大ファイルは避ける）
            if (file.length > 5_000_000) return null

            // 4) 中身を読む（※ file.fileType は絶対触らない：無限再帰の原因になる）
            val text = try {
                VfsUtilCore.loadText(file)
            } catch (_: Throwable) {
                return null
            }

            // 5) 軽いチェック
            if (!quickLooksLikeQiq(text)) return null

            // 6) 厳しめチェック（Qiq固有トークン＋閉じ "}}" の共起）
            val sure = (text.contains("{{"))
                    || (text.contains("{{=")
                    || text.contains("{{h")
                    || text.contains("{{a")
                    || text.contains("{{u")
                    || text.contains("{{c")
                    || text.contains("{{j"))
                    && text.contains("}}")

            if (sure) {
                file.putUserData(QIQ_MARKER, true)
                return QiqFileType
            }

            return null
        } finally {
            file.putUserData(REENTRANT_GUARD, null)
        }
    }
}
