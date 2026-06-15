package io.github.jingu.idea_qiq_plugin.liveTemplate

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import io.github.jingu.idea_qiq_plugin.lang.QiqTemplateLanguage

/**
 * Live-template context for Qiq templates: enables the bundled block snippets
 * (`qif`, `qforeach`, `qsection`, ...) inside a `.qiq` / `.qiq.php` file.
 *
 * Registered with `contextId="QIQ"` in plugin.xml; the templates in
 * `resources/liveTemplates/Qiq.xml` reference that id in their `<context>`.
 */
class QiqLiveTemplateContextType : TemplateContextType("Qiq") {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file
        if (file.fileType == QiqFileType) return true
        if (file.viewProvider.baseLanguage == QiqTemplateLanguage) return true
        val name = file.viewProvider.virtualFile.name
        return name.endsWith(".qiq", ignoreCase = true) || name.endsWith(".qiq.php", ignoreCase = true)
    }
}
