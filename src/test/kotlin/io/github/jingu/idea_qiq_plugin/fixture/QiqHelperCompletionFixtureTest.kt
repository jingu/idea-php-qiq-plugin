package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperRegistry
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * Integration coverage for QiqHelperCompletionContributor (#32): a helper
 * registered in a nominated bootstrap file is offered at a `{{ name(...) }}` call
 * position. Exercises the real bootstrap scan + settings + PHP injection.
 */
class QiqHelperCompletionFixtureTest : BasePlatformTestCase() {

    private fun setUpHelper() {
        myFixture.addFileToProject(
            "GreetHelper.php",
            "<?php\nclass GreetHelper {\n    public function __invoke(\$name) { return \$name; }\n}\n",
        )
        myFixture.addFileToProject(
            "bootstrap.php",
            "<?php\n\$helpers->set('greet', fn() => new GreetHelper());\n" +
                "\$helpers->set('greeting', fn() => new GreetHelper());\n",
        )
        QiqSettingsService.getInstance(project).setHelperBootstrapFiles(listOf("bootstrap.php"))
    }

    fun testRegistryDiscoversHelper() {
        setUpHelper()
        val names = QiqHelperRegistry.getInstance(project).allHelperNames()
        assertContainsElements(names, "greet", "greeting")
    }

    fun testRegisteredHelperOffered() {
        setUpHelper()
        // Two helpers share the `gree` prefix so the popup stays open (a single
        // match would auto-insert and leave no lookup to inspect); the `()` makes
        // it a call-name position.
        myFixture.configureByText("page.qiq", "{{ gree<caret>() }}")
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(items, "greet", "greeting")
    }
}
