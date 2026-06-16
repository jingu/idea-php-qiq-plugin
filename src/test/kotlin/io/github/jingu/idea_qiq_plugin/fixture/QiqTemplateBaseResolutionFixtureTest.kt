package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * Integration coverage for QiqSettingsService.resolveTemplateBase (#32): the base
 * for a file under a `qiq/{template,helper}` tree is the `template/` directory —
 * the root a Qiq `/...` root-absolute path is resolved against — not `qiq/`.
 */
class QiqTemplateBaseResolutionFixtureTest : BasePlatformTestCase() {

    fun testBaseStopsAtTemplateDirectory() {
        val root = "qiq_${getTestName(true)}"
        myFixture.addFileToProject(
            "$root/template/layout/base.qiq.php",
            "<html>{{= \$this->getSection('content') }}{{ if (\$x): }}<i>x</i>{{ endif }}</html>",
        )
        myFixture.addFileToProject(
            "$root/template/partial/menu.qiq.php",
            "<ul>{{ foreach (\$items as \$i): }}<li>{{= \$i }}</li>{{ endforeach }}</ul>",
        )
        // A non-template sibling (plain PHP) marks the edge of the template tree.
        myFixture.addFileToProject("$root/helper/Foo.php", "<?php\nclass Foo {}\n")
        val home = myFixture.addFileToProject(
            "$root/template/page/home.qiq.php",
            "{{ setSection('content') }}<p>hi</p>{{ endSection() }}{{ if (\$y): }}<b>y</b>{{ endif }}",
        )

        val base = QiqSettingsService.getInstance(project).resolveTemplateBase(home.virtualFile)
        assertNotNull("expected a template base directory", base)
        assertEquals("template", base!!.name)
    }
}
