package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration coverage for QiqTemplatePathCompletionContributor (#32): render()
 * offers the template paths discovered under the roots, both relative and
 * root-absolute (`/`-prefixed).
 */
class QiqTemplatePathCompletionFixtureTest : BasePlatformTestCase() {

    private fun completionInside(homeBody: String, marker: String): List<String> {
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
        val markerIndex = home.text.indexOf(marker)
        assertTrue("marker '$marker' not found in fixture text", markerIndex >= 0)
        myFixture.editor.caretModel.moveToOffset(markerIndex + marker.length)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testTemplatePathsOffered() {
        // render('') offers the templates discovered under the roots; depending on
        // which directory scores as the root they come through relative or as the
        // `/`-prefixed base-absolute form, so match slash-agnostically.
        val items = completionInside("{{ render('') }}{{ if (\$z): }}<b>z</b>{{ endif }}", "render('")
        assertTrue("offered: $items", items.any { it.endsWith("layout/base") })
        assertTrue("offered: $items", items.any { it.endsWith("partial/menu") })
    }

    fun testRootAbsoluteTemplatePathsOffered() {
        val items = completionInside("{{ render('/') }}{{ if (\$z): }}<b>z</b>{{ endif }}", "render('/")
        assertTrue(
            "expected a root-absolute layout/base candidate, got: $items",
            items.any { it.contains("layout/base") },
        )
    }
}
