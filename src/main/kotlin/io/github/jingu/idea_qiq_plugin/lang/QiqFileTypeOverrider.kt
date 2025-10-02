package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId

class QiqFileTypeOverrider : FileTypeOverrider {

    companion object {
        val QIQ_MARKER: Key<Boolean> = Key.create("qiq.overridden.as.qiq")

        private val REENTRANT_GUARD: Key<Boolean> = Key.create("qiq.overrider.guard")

        private val CANDIDATE_EXTENSIONS = setOf("php", "phtml", "html", "htm", "tpl")
        private const val SIZE_LIMIT_BYTES: Long = 5_000_000
        private val CONTROL_PATTERN = Regex("""\{\{\s*(if|foreach|setSection|extends|block|render|setLayout)\b""")

        // よく使うトークンで早期判定（軽いチェック）
        private fun quickLooksLikeQiq(text: CharSequence): Boolean {
            if (!text.contains("{{")) return false
            if (text.contains("{{=") || text.contains("{{h") || text.contains("{{a")) return true
            if (text.contains("{{ //") || text.contains("{{//")) return true
            // 少し厳しめに：テンプレ制御
            return CONTROL_PATTERN.containsMatchIn(text)
        }
    }

    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (file !is VirtualFileWithId) return null
        if (file.isDirectory) return null
        if (!file.isValid) return null
        if (!file.isInLocalFileSystem) return null

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
            val candidate = ext == null || ext in CANDIDATE_EXTENSIONS
            if (!candidate) return null

            // 3) サイズ上限（巨大ファイルは避ける）
            if (file.length > SIZE_LIMIT_BYTES) return null

        // 再入防止
        if (file.getUserData(REENTRANT_GUARD) == true) return null
        file.putUserData(REENTRANT_GUARD, true)
        try {
            // 4) 中身を読む（※ file.fileType は絶対触らない：無限再帰の原因になる）
            val text = runCatching { VfsUtilCore.loadText(file) }.getOrElse { return null }

            // 5) 軽いチェック
            if (!quickLooksLikeQiq(text)) return null

            // 6) 厳しめチェック（閉じ "}}" の存在のみ確認：quick 判定で十分な指標を満たしている）
            if (text.contains("}}")) {
                file.putUserData(QIQ_MARKER, true)
                return QiqFileType
            }

            return null
        } finally {
            file.putUserData(REENTRANT_GUARD, null)
        }
    }
}
