package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.inspection.QiqBlockDirectiveInspection

/**
 * Integration coverage for [QiqBlockDirectiveInspection]: drives the real platform
 * highlighting pipeline (registration + PSI + the shared [QiqBlockModel] validator)
 * rather than the pure validator alone (QiqBlockModelTest). #30 / #32.
 */
class QiqBlockDirectiveInspectionFixtureTest : BasePlatformTestCase() {

    private fun qiqWarnings(text: String): List<HighlightInfo> {
        myFixture.configureByText("page.qiq", text)
        myFixture.enableInspections(QiqBlockDirectiveInspection())
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.description?.contains("Qiq") == true }
    }

    fun testBalancedBlockHasNoWarning() {
        val warnings = qiqWarnings("{{ if (\$x): }}\n<p>ok</p>\n{{ endif }}")
        assertEmpty(warnings)
    }

    fun testUnclosedOpenerIsFlagged() {
        val warnings = qiqWarnings("{{ if (\$x): }}\n<p>oops</p>")
        assertSize(1, warnings)
        assertTrue(warnings[0].description, warnings[0].description.contains("Unclosed Qiq block"))
    }

    fun testUnmatchedCloserIsFlagged() {
        val warnings = qiqWarnings("<p>oops</p>\n{{ endif }}")
        assertSize(1, warnings)
        assertTrue(warnings[0].description, warnings[0].description.contains("no matching opener"))
    }

    fun testMismatchedCloserIsFlagged() {
        val warnings = qiqWarnings("{{ foreach (\$xs as \$x): }}\n{{ endif }}")
        assertTrue(
            "expected a mismatch/unclosed warning, got: ${warnings.map { it.description }}",
            warnings.any { it.description.contains("does not match") },
        )
    }
}
