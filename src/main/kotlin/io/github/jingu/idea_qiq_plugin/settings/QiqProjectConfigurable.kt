package io.github.jingu.idea_qiq_plugin.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.github.jingu.idea_qiq_plugin.ui.QiqBundle

/**
 * Project-level settings page for Qiq Templates, surfaced under
 * Settings > Languages & Frameworks > Qiq Templates.
 *
 * Currently exposes a single switch: Strict Types. Enabling it makes
 * QiqPhpInjector prepend `<?php declare(strict_types=1); ?>` to each
 * Qiq template's injected PHP, so scalar literal misuses such as
 * `{{h true }}` or `{{h 123 }}` surface as PhpStorm type warnings.
 */
class QiqProjectConfigurable(private val project: Project) : BoundSearchableConfigurable(
    QiqBundle.message("settings.qiq.display.name"),
    "settings.qiq",
    "settings.qiq",
) {

    private val settings get() = QiqSettingsService.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(QiqBundle.message("settings.qiq.strict.types.checkbox"))
                .bindSelected(settings.state::enableStrictTypes)
                .comment(QiqBundle.message("settings.qiq.strict.types.comment"))
        }
    }
}
