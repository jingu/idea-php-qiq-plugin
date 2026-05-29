package io.github.jingu.idea_qiq_plugin.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperRegistry
import io.github.jingu.idea_qiq_plugin.ui.QiqBundle
import javax.swing.ListSelectionModel

/**
 * Project-level settings page for Qiq Templates, surfaced under
 * Settings > Languages & Frameworks > Qiq Templates.
 *
 * Exposes:
 *  - Strict Types switch (injects `declare(strict_types=1)` into Qiq
 *    template PHP fragments so scalar literal misuses surface as type
 *    warnings).
 *  - Helper bootstrap files list. Each entry points to a PHP file that
 *    registers Qiq helpers via `HelperLocator::set('name', closure)`.
 *    Files are added via a native file chooser (multi-select) and stored
 *    relative to the project base dir when possible so settings stay
 *    portable across checkouts. The scanner inspects these files to build
 *    the helperName → PhpClass map used for Go to Declaration on helper
 *    calls in templates.
 */
class QiqProjectConfigurable(private val project: Project) : BoundSearchableConfigurable(
    QiqBundle.message("settings.qiq.display.name"),
    "settings.qiq",
    "settings.qiq",
) {

    private val settings get() = QiqSettingsService.getInstance(project)
    private val bootstrapModel = CollectionListModel<String>()

    override fun createPanel(): DialogPanel {
        val list = JBList(bootstrapModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            visibleRowCount = 4
            emptyText.text = QiqBundle.message("settings.qiq.helper.bootstrap.empty")
        }
        val listPanel = ToolbarDecorator.createDecorator(list)
            .setAddAction { chooseBootstrapFiles() }
            .disableUpDownActions()
            .createPanel()

        return panel {
            row {
                // Bind through the public service accessors rather than the
                // backing `state` property, which is private.
                checkBox(QiqBundle.message("settings.qiq.strict.types.checkbox"))
                    .bindSelected(
                        { settings.isStrictTypesEnabled() },
                        { settings.setStrictTypesEnabled(it) },
                    )
                    .comment(QiqBundle.message("settings.qiq.strict.types.comment"))
            }

            group(QiqBundle.message("settings.qiq.helper.bootstrap.group")) {
                row {
                    cell(listPanel)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .comment(QiqBundle.message("settings.qiq.helper.bootstrap.comment"))
                        .onReset { bootstrapModel.replaceAll(settings.getHelperBootstrapFiles()) }
                        .onIsModified { bootstrapModel.items != settings.getHelperBootstrapFiles() }
                        .onApply {
                            settings.setHelperBootstrapFiles(bootstrapModel.items.toList())
                            // Drop cached scan results so the next resolve
                            // sees the new file list immediately.
                            QiqHelperRegistry.getInstance(project).invalidateCache()
                        }
                }.resizableRow()
            }
        }
    }

    private fun chooseBootstrapFiles() {
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
            .withTitle(QiqBundle.message("settings.qiq.helper.bootstrap.chooser.title"))
            .withFileFilter { it.extension.equals("php", ignoreCase = true) }

        val toSelect = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
        FileChooser.chooseFiles(descriptor, project, toSelect) { files ->
            files.forEach { vf ->
                val stored = toStoredPath(vf)
                if (bootstrapModel.items.none { it == stored }) {
                    bootstrapModel.add(stored)
                }
            }
        }
    }

    /**
     * Store paths relative to the project base dir when the chosen file
     * lives under it; fall back to the absolute path otherwise. This keeps
     * settings portable when the repository is checked out elsewhere.
     */
    private fun toStoredPath(vf: VirtualFile): String {
        val base = project.basePath
        val path = vf.path
        if (base != null && path.startsWith("$base/")) {
            return path.removePrefix("$base/")
        }
        return path
    }
}
