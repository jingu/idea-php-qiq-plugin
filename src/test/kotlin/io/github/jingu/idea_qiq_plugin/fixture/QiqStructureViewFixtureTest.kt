package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration coverage for QiqStructureViewFactory (#29 / #32): builds the real
 * structure view through the registered factory and checks the rendered tree,
 * complementing the pure QiqOutline tests.
 */
class QiqStructureViewFixtureTest : BasePlatformTestCase() {

    private fun renderedStructure(text: String): String {
        myFixture.configureByText("page.qiq", text)
        var rendered = ""
        myFixture.testStructureView { component ->
            PlatformTestUtil.expandAll(component.tree)
            rendered = PlatformTestUtil.print(component.tree, false)
        }
        return rendered
    }

    fun testSectionAndControlBlockOutline() {
        val text = """
            {{ setSection('content') }}
            {{ if (${'$'}x): }}
            <p>hi</p>
            {{ endif }}
            {{ endSection() }}
        """.trimIndent()
        val rendered = renderedStructure(text)
        assertTrue(rendered, rendered.contains("section 'content'"))
        assertTrue(rendered, rendered.contains("if (${'$'}x)"))
    }

    fun testLayoutDirectiveAppearsAtTopLevel() {
        val rendered = renderedStructure("{{ setLayout('layout/base') }}\n<p>body</p>")
        assertTrue(rendered, rendered.contains("setLayout('layout/base')"))
    }
}
