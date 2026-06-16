package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import io.github.jingu.idea_qiq_plugin.helper.QiqHelperTargets
import io.github.jingu.idea_qiq_plugin.navigation.QiqHelperParameterInfoHandler
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * Integration coverage for QiqHelperParameterInfoHandler (#32): the resolved helper
 * __invoke signature is rendered for a helper call. Exercises the bootstrap scan +
 * PhpIndex resolution feeding the parameter-info UI.
 */
class QiqHelperParameterInfoFixtureTest : BasePlatformTestCase() {

    fun testRendersResolvedInvokeSignature() {
        myFixture.addFileToProject(
            "GreetHelper.php",
            "<?php\nclass GreetHelper {\n    public function __invoke(string \$name): string { return \$name; }\n}\n",
        )
        myFixture.addFileToProject("bootstrap.php", "<?php\n\$helpers->set('greet', fn() => new GreetHelper());\n")
        QiqSettingsService.getInstance(project).setHelperBootstrapFiles(listOf("bootstrap.php"))
        val file = myFixture.configureByText("page.qiq", "{{ greet('x') }}")

        val invoke = QiqHelperTargets.functions(project, "greet").firstOrNull()
            ?: error("helper 'greet' did not resolve to a target")
        val context = MockParameterInfoUIContext(file)
        QiqHelperParameterInfoHandler().updateUI(invoke, context)

        assertTrue("signature should mention the parameter, was: ${context.text}", context.text.contains("name"))
    }
}
