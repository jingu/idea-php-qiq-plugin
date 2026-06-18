package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.inspection.QiqHelperArgumentsInspection
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * Integration coverage for [QiqHelperArgumentsInspection] (#32): checks helper call
 * arity and argument types against the resolved __invoke signature. Exercises the
 * bootstrap scan + PhpIndex class resolution + injected-PHP inspection.
 */
class QiqHelperArgumentsInspectionFixtureTest : BasePlatformTestCase() {

    private fun helperWarnings(callBody: String): List<HighlightInfo> {
        myFixture.addFileToProject(
            "GreetHelper.php",
            "<?php\nclass GreetHelper {\n    public function __invoke(string \$name): string { return \$name; }\n}\n",
        )
        myFixture.addFileToProject(
            "bootstrap.php",
            "<?php\n\$helpers->set('greet', fn() => new GreetHelper());\n",
        )
        QiqSettingsService.getInstance(project).setHelperBootstrapFiles(listOf("bootstrap.php"))
        myFixture.configureByText("page.qiq", callBody)
        myFixture.enableInspections(QiqHelperArgumentsInspection())
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.description?.contains("helper") == true }
    }

    fun testCorrectCallNotFlagged() {
        assertEmpty(helperWarnings("{{ greet('world') }}"))
    }

    fun testTooFewArgumentsFlagged() {
        val warnings = helperWarnings("{{ greet() }}")
        assertTrue(warnings.toString(), warnings.any { it.description.contains("Too few") })
    }

    fun testArgumentTypeMismatchFlagged() {
        val warnings = helperWarnings("{{ greet([1, 2]) }}")
        assertTrue(warnings.toString(), warnings.any { it.description.contains("expected") })
    }
}
