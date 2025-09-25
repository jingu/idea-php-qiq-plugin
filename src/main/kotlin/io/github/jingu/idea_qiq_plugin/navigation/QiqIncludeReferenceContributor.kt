package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * setLayout()やrender()の第一引数（StringLiteralExpression）に PsiReference を付与するcontributor。
 * 参照の解決は NASR（Nearest-Anchored Strict Resolution）で行う。
 */
class QiqIncludeReferenceContributor : PsiReferenceContributor() {
    private val knownTemplateFunctions = setOf("setLayout", "render", "extends")

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // 1) パターン：StringLiteral が ParameterList の子 かつ 親が FunctionReference
        val stringArgInFunc =
            PlatformPatterns.psiElement(StringLiteralExpression::class.java)
                .withParent(PlatformPatterns.psiElement(ParameterList::class.java)
                    .withParent(PlatformPatterns.psiElement(FunctionReference::class.java))
                )

        registrar.registerReferenceProvider(
            stringArgInFunc,
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    // PCEをここで発火させないため、軽い参照だけ取る
                    val lit = element as StringLiteralExpression
                    val fr = lit.parent?.parent as? FunctionReference ?: return PsiReference.EMPTY_ARRAY

                    // DumbMode中は重いことをしない（任意）
                    if (com.intellij.openapi.project.DumbService.isDumb(element.project)) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    // 関数名の軽量チェック（解決なし）
                    val name = fr.name ?: return PsiReference.EMPTY_ARRAY
                    if (name !in knownTemplateFunctions) return PsiReference.EMPTY_ARRAY

                    // 文字列リテラルの中身だけを可視範囲に
                    val range = TextRange(1, lit.textLength - 1).let { r ->
                        if (r.startOffset >= r.endOffset) TextRange.EMPTY_RANGE else r
                    }
                    return arrayOf(QiqLayoutPathReference(lit, range))
                }
            }
        )
    }
}

/**
 * 'layout/base' のようなパス文字列を、現在ファイルの最寄り祖先にある firstSegment ディレクトリを
 * アンカーとして厳密解決する PsiReference。
 *
 * 振る舞い:
 *  - firstSegment（例: "layout"）までは「現在ファイルから最寄り祖先で直下ディレクトリ一致」を探す
 *  - 以降のセグメントは「中間スキップなしの完全一致」
 *  - 最終セグメントはファイル名完全一致（拡張子は allowedExts の優先順）
 *  - 見つからなければ未解決（BFS 深掘りなし）
 */
class QiqLayoutPathReference(
    element: StringLiteralExpression,
    rangeInElement: TextRange
) : PsiReferenceBase<StringLiteralExpression>(element, rangeInElement), PsiPolyVariantReference {

    /** 許可する拡張子の優先順。設定で差し替え可能にしても良い。 */
    private val allowedExts: List<String> = listOf("qiq", "qiq.php", "php")

    override fun resolve(): PsiElement? {
        val results = multiResolve(false)
        return results.firstOrNull()?.element
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project

        // 1) 事前ガード：不安定な間はキャッシュを使わない（EMPTYの抱き込みを防ぐ）
        val dumb = com.intellij.openapi.project.DumbService.isDumb(project)
        // Top-level 物理 VirtualFile を先に引いておく（取れなければノーキャッシュで即終了）
        val ilm = com.intellij.lang.injection.InjectedLanguageManager.getInstance(project)
        val topLevel = ilm.getTopLevelFile(this.element)
        val vFile: VirtualFile = topLevel.originalFile.virtualFile
            ?: topLevel.virtualFile
            ?: PsiUtilCore.getVirtualFile(topLevel)
            ?: this.element.containingFile?.virtualFile
            ?: return ResolveResult.EMPTY_ARRAY

        // content root 外や一時ファイル（parent なし）もノーキャッシュで返す
        val fileIndex = ProjectFileIndex.getInstance(project)
        val baseDir = (if (vFile.isDirectory) vFile else vFile.parent)
        if (baseDir == null || !fileIndex.isInContent(baseDir)) {
            // ここで cache を使うと EMPTY が張り付く可能性があるため使わない
            return ResolveResult.EMPTY_ARRAY
        }

        // Dumb 中は「解決しても不安定になりやすい」ので、キャッシュせず直接実行か、空を返す
        if (dumb) {
            // 走らせても安全なロジックならノーキャッシュで実行（この参照の NASR はインデックス非依存なのでOK）
            return doResolve(project, vFile)
        }

        // 2) ここまで来たら安定。キャッシュを使う
        val cache = ResolveCache.getInstance(project)
        return cache.resolveWithCaching(
            this,
            RESOLVER,
            false,
            incompleteCode
        )
    }

    // 参照型を型実引数にした PolyVariantResolver を用意
    private object RESOLVER : ResolveCache.PolyVariantResolver<QiqLayoutPathReference> {
        override fun resolve(ref: QiqLayoutPathReference, incompleteCode: Boolean): Array<ResolveResult> {
            val project = ref.element.project

            // ここで top-level / vFile を毎回安全に算出（Injected 対応）
            val ilm = com.intellij.lang.injection.InjectedLanguageManager.getInstance(project)
            val topLevel = ilm.getTopLevelFile(ref.element)
            val vFile: VirtualFile =
                topLevel.originalFile.virtualFile
                    ?: topLevel.virtualFile
                    ?: PsiUtilCore.getVirtualFile(topLevel)
                    ?: ref.element.containingFile?.virtualFile
                    ?: return ResolveResult.EMPTY_ARRAY

            return ref.doResolve(project, vFile)
        }
    }

    private fun doResolve(project: Project, currentVFile: VirtualFile): Array<ResolveResult> {
        val raw = element.contents.trim()
        if (raw.isEmpty()) {
            return ResolveResult.EMPTY_ARRAY
        }

        val segments = raw.trim('/', '.').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return ResolveResult.EMPTY_ARRAY

        val first = segments.first()
        val remaining = segments.drop(1)

        val v = resolveStrictFromNearestAnchor(project, currentVFile, first, remaining, allowedExts)
            ?: return ResolveResult.EMPTY_ARRAY

        val psi = PsiManager.getInstance(project).findFile(v) ?: return ResolveResult.EMPTY_ARRAY
        return arrayOf(PsiElementResolveResult(psi))
    }

    override fun getVariants(): Array<Any> {
        // 厳密一致主義に合わせ、安易に深い候補を出さない。必要ならここで
        // ルート候補直下の子だけを出すなど、最小限の補完に留める。
        return emptyArray()
    }

    // ====== NASR（Nearest-Anchored Strict Resolution）実装 ======
    private fun resolveStrictFromNearestAnchor(
        project: Project,
        current: VirtualFile,
        first: String,
        remaining: List<String>,
        exts: List<String>
    ): VirtualFile? {
        val fileIndex = ProjectFileIndex.getInstance(project)

        // ✅ 起点ディレクトリの決定：current が LightVirtualFile で parent=null の場合に備える
        var dir: VirtualFile? = if (current.isDirectory) current else current.parent
        if (dir == null) {
            // current が LightVirtualFile（Injected）等の場合のフォールバック
            val psi = PsiManager.getInstance(project).findFile(current)
            val top = if (psi != null)
                com.intellij.lang.injection.InjectedLanguageManager.getInstance(project).getTopLevelFile(psi)
            else
                null
            dir = top?.containingDirectory?.virtualFile
                ?: top?.originalFile?.containingDirectory?.virtualFile
                        ?: top?.virtualFile?.parent
        }
        if (dir == null) return null

        while (dir != null && fileIndex.isInContent(dir)) {
            val anchor = dir.findChild(first)
            if (anchor != null && anchor.isDirectory) {
                resolveStrict(anchor, remaining, exts)?.let { return it }
            }
            dir = dir.parent
        }
        return null
    }

    /**
     * アンカー（firstSegment に一致するディレクトリ）直下から、
     * 残りのセグメントを「中間スキップなし」で厳密に辿る。
     */
    private fun resolveStrict(
        rootDir: VirtualFile,
        segments: List<String>,
        exts: List<String>
    ): VirtualFile? {
        var node = rootDir
        if (segments.isEmpty()) {
            // 仕様：終点はファイル名を要求するため、単独パスは解決不可とする
            return null
        }
        segments.forEachIndexed { i, seg ->
            val last = i == segments.lastIndex
            if (last) {
                // ファイル名完全一致（拡張子だけ柔軟）
                for (ext in exts) {
                    node.findChild("$seg.$ext")?.let { f ->
                        if (!f.isDirectory) return f
                    }
                }
                return null
            } else {
                val next = node.findChild(seg) ?: return null
                if (!next.isDirectory) return null
                node = next
            }
        }
        return null
    }
}