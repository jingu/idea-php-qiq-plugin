package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.inspection.QiqSectionNameInspection

/**
 * Integration coverage for [QiqSectionNameInspection] (#31 / #32): a getSection()
 * for a name some template defines is clean; one no template defines is flagged.
 * Exercises the real cross-template section index over the qiq/{template,helper}
 * layout.
 */
class QiqSectionNameInspectionFixtureTest : BasePlatformTestCase() {

    private fun undefinedWarnings(layoutBody: String): List<HighlightInfo> {
        // Unique tree per method (the light project's root cache is reused).
        val root = "qiq_${getTestName(true)}"
        myFixture.addFileToProject(
            "$root/template/page/home.qiq.php",
            "{{ setSection('content') }}<p>hi</p>{{ endSection() }}{{ if (\$x): }}<i>x</i>{{ endif }}",
        )
        myFixture.addFileToProject("$root/helper/Foo.php", "<?php\nclass Foo {}\n")
        val layout = myFixture.addFileToProject("$root/template/layout/base.qiq.php", layoutBody)
        myFixture.configureFromExistingVirtualFile(layout.virtualFile)
        myFixture.enableInspections(QiqSectionNameInspection())
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.description?.contains("is not defined") == true }
    }

    fun testDefinedSectionNotFlagged() {
        assertEmpty(
            undefinedWarnings("<html>{{= \$this->getSection('content') }}{{ if (\$y): }}<b>y</b>{{ endif }}</html>"),
        )
    }

    fun testUndefinedSectionFlagged() {
        val warnings =
            undefinedWarnings("<html>{{= \$this->getSection('missing') }}{{ if (\$y): }}<b>y</b>{{ endif }}</html>")
        assertSize(1, warnings)
        assertTrue(warnings[0].description, warnings[0].description.contains("missing"))
    }
}
