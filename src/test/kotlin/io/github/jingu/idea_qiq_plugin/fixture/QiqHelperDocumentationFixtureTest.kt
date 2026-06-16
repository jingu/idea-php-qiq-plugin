package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import io.github.jingu.idea_qiq_plugin.navigation.QiqHelperDocumentationProvider
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService

/**
 * Integration coverage for QiqHelperDocumentationProvider (#32): Quick Doc on a
 * helper call points at the resolved helper target (the class's __invoke) rather
 * than the unresolved injected global call.
 */
class QiqHelperDocumentationFixtureTest : BasePlatformTestCase() {

    fun testDocResolvesToHelperInvoke() {
        myFixture.addFileToProject(
            "GreetHelper.php",
            "<?php\nclass GreetHelper {\n    /** Greet someone. */\n    public function __invoke(string \$name): string { return \$name; }\n}\n",
        )
        myFixture.addFileToProject("bootstrap.php", "<?php\n\$helpers->set('greet', fn() => new GreetHelper());\n")
        QiqSettingsService.getInstance(project).setHelperBootstrapFiles(listOf("bootstrap.php"))
        val file = myFixture.configureByText("page.qiq", "{{ greet('x') }}")

        val host = PsiTreeUtil.findChildOfType(file, QiqCodeHost::class.java) ?: error("no code host")
        var injected: PsiFile? = null
        InjectedLanguageManager.getInstance(project).enumerate(host) { f, _ -> injected = f }
        val injectedFile = injected ?: error("no injected PHP")
        val call = PsiTreeUtil.findChildrenOfType(injectedFile, FunctionReference::class.java)
            .first { it.name == "greet" }
        val nameLeaf = call.nameNode?.psi ?: error("no name node")

        val target = QiqHelperDocumentationProvider()
            .getCustomDocumentationElement(myFixture.editor, injectedFile, nameLeaf, 0)

        assertNotNull("expected the helper doc to resolve to a target", target)
        assertEquals("__invoke", (target as? Function)?.name)
        assertEquals("GreetHelper", (target as? com.jetbrains.php.lang.psi.elements.Method)?.containingClass?.name)
    }
}
