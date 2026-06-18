package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpLanguage
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost

/**
 * Smoke test proving the fixture harness boots with the Qiq language and the
 * bundled PHP plugin, and that PHP is injected into a `{{ ... }}` host. The
 * feature-specific HeavyPlatformTestCase suites (#32) build on this setup.
 */
class QiqFixtureSmokeTest : BasePlatformTestCase() {

    fun testQiqFileTypeRecognized() {
        val file = myFixture.configureByText("page.qiq", "<div>{{= \$x }}</div>")
        assertEquals(QiqFileType, file.fileType)
    }

    fun testPhpInjectedIntoCodeHost() {
        val file = myFixture.configureByText("page.qiq", "{{= 1 + 2 }}")
        val host = PsiTreeUtil.findChildOfType(file, QiqCodeHost::class.java)
        assertNotNull("expected a Qiq code host", host)

        val injected = mutableListOf<PsiFile>()
        InjectedLanguageManager.getInstance(project)
            .enumerate(host!!) { injectedPsi, _ -> injected.add(injectedPsi) }
        assertTrue("expected PHP injected into the {{= }} host", injected.isNotEmpty())
        assertEquals(PhpLanguage.INSTANCE, injected.first().language)
    }
}
