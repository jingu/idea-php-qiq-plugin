package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage

object QiqTemplateLanguage : Language("Qiq"), TemplateLanguage {
    override fun isCaseSensitive(): Boolean = true
    @Suppress("unused")
    private fun readResolve(): Any = QiqTemplateLanguage
}
