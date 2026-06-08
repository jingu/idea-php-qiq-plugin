package io.github.jingu.idea_qiq_plugin.editor

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes

/**
 * Matches Qiq delimiter pairs: `{{` and the escape variants `{{=`/`{{h`/`{{a`/
 * `{{c`/`{{j`/`{{u` against their closing `}}`.
 *
 * Wrapped in [PairedBraceMatcherAdapter] (like the bundled Blade support) so the
 * matcher stays scoped to the Qiq layer of the template file and does not pair a
 * Qiq delimiter with a brace from the injected PHP or surrounding HTML.
 */
class QiqBraceMatcher : PairedBraceMatcherAdapter(Matcher, QiqTemplateLanguage) {

    private object Matcher : PairedBraceMatcher {
        override fun getPairs(): Array<BracePair> = PAIRS

        // Never auto-insert the closing `}}` when an opening delimiter is typed:
        // closing-delimiter completion is owned by QiqTypedHandler (`{{ ` -> ` }}`),
        // and letting PairedBraceMatcher also insert one yields a doubled/partial
        // closer. This only suppresses auto-insertion; pair highlighting is unaffected.
        override fun isPairedBracesAllowedBeforeType(
            lbraceType: IElementType,
            contextType: IElementType?,
        ): Boolean = false

        override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int =
            openingBraceOffset
    }

    companion object {
        // Left tokens are what the lexer emits in YYINITIAL; right tokens are what
        // each Qiq sub-state emits for the closing `}}`. `{{a` and `{{c` share the
        // QIQ_ESC state, so both close with RBRACEC.
        private val PAIRS = arrayOf(
            BracePair(QiqTokenTypes.CODE_OPEN, QiqTokenTypes.RBRACE2, true),
            BracePair(QiqTokenTypes.RAW_OPEN, QiqTokenTypes.RBRACE_EQ, true),
            BracePair(QiqTokenTypes.ESCAPE_H_OPEN, QiqTokenTypes.RBRACEH, true),
            BracePair(QiqTokenTypes.ESCAPE_J_OPEN, QiqTokenTypes.RBRACEJ, true),
            BracePair(QiqTokenTypes.ESCAPE_U_OPEN, QiqTokenTypes.RBRACEU, true),
            BracePair(QiqTokenTypes.ESCAPE_A_OPEN, QiqTokenTypes.RBRACEC, true),
            BracePair(QiqTokenTypes.ESCAPE_C_OPEN, QiqTokenTypes.RBRACEC, true),
        )
    }
}
