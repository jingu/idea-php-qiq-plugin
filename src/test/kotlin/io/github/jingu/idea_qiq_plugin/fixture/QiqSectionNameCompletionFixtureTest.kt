package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration coverage for QiqSectionNameCompletionContributor (#31 / #32):
 * getSection('<caret>') offers the section names defined by setSection across the
 * template roots.
 */
class QiqSectionNameCompletionFixtureTest : BasePlatformTestCase() {

    fun testCompletesDefinedSectionNames() {
        val root = "qiq_${getTestName(true)}"
        myFixture.addFileToProject(
            "$root/template/page/home.qiq.php",
            "{{ setSection('content') }}<p>hi</p>{{ endSection() }}" +
                "{{ setSection('sidebar') }}<aside>s</aside>{{ endSection() }}",
        )
        myFixture.addFileToProject("$root/helper/Foo.php", "<?php\nclass Foo {}\n")
        val marker = "getSection('"
        val layout = myFixture.addFileToProject(
            "$root/template/layout/base.qiq.php",
            "<html>{{= \$this->$marker') }}{{ if (\$y): }}<i>y</i>{{ endif }}</html>",
        )
        myFixture.configureFromExistingVirtualFile(layout.virtualFile)
        // addFileToProject keeps `<caret>` literal, so place the caret in the quotes.
        myFixture.editor.caretModel.moveToOffset(layout.text.indexOf(marker) + marker.length)
        myFixture.completeBasic()

        val items = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(items, "content", "sidebar")
    }
}
