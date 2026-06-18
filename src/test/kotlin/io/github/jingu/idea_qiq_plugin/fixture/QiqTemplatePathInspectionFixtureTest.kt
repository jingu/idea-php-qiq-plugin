package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.inspection.QiqTemplatePathInspection

/**
 * Integration coverage for [QiqTemplatePathInspection] over a representative
 * on-disk `qiq/{template,helper}` layout (#32): a render() to an existing template
 * is clean, one to a missing template is flagged.
 */
class QiqTemplatePathInspectionFixtureTest : BasePlatformTestCase() {

    private fun missingWarnings(homeBody: String): List<HighlightInfo> {
        // The light project (and its QiqSettingsService root cache, keyed by path)
        // is reused across test methods, so give each test a unique tree to avoid
        // resolving a path cached against a now-deleted temp file.
        val root = "qiq_${getTestName(true)}"
        myFixture.addFileToProject(
            "$root/template/layout/base.qiq.php",
            "<html>{{= \$this->getSection('content') }}{{ if (\$x): }}<i>x</i>{{ endif }}</html>",
        )
        myFixture.addFileToProject(
            "$root/template/partial/menu.qiq.php",
            "<ul>{{ foreach (\$items as \$i): }}<li>{{= \$i }}</li>{{ endforeach }}</ul>",
        )
        myFixture.addFileToProject("$root/helper/Foo.php", "<?php\nclass Foo {}\n")
        val home = myFixture.addFileToProject("$root/template/page/home.qiq.php", homeBody)
        myFixture.configureFromExistingVirtualFile(home.virtualFile)
        myFixture.enableInspections(QiqTemplatePathInspection())
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.description?.contains("template not found") == true }
    }

    fun testExistingTemplateNotFlagged() {
        assertEmpty(missingWarnings("{{ render('partial/menu') }}"))
    }

    fun testMissingTemplateFlagged() {
        val warnings = missingWarnings("{{ render('partial/nope') }}")
        assertSize(1, warnings)
        assertTrue(warnings[0].description, warnings[0].description.contains("partial/nope"))
    }
}
