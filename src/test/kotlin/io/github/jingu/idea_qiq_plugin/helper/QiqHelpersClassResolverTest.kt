package io.github.jingu.idea_qiq_plugin.helper

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.resources.ProjectExtension
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.elements.PhpClass
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

/**
 * Unit-tests the per-class filter that powers Qiq 2.x/3.x helper
 * discovery: which methods of a `Qiq\Helpers` subclass count as helpers.
 *
 * The PhpIndex subclass walk (`processAllSubclasses`) is a thin platform
 * call and is not exercised here; the discriminating logic
 * (`QiqHelpersClassResolver.helperMethodsOf`) is pure PSI and runs against
 * an in-memory file, so no indexed fixture is required.
 */
@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqHelpersClassResolverTest {

    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @Test
    fun keepsOnlyPublicNonStaticNonMagicMethodsOfUserSubclass(project: Project) {
        val names = helperNames(
            project,
            """
            <?php
            namespace App\Template\Helper;

            use Qiq\Helper\Html\HtmlHelpers;

            class CustomHelpers extends HtmlHelpers
            {
                public function rot13(string ${'$'}str): string { return str_rot13(${'$'}str); }
                public function shout(string ${'$'}s): string { return strtoupper(${'$'}s); }
                protected function secret(): string { return ''; }
                private function hidden(): string { return ''; }
                public static function factory(): self { return new self(); }
                public function __call(string ${'$'}name, array ${'$'}args) {}
            }
            """.trimIndent(),
        )
        assertEquals(setOf("rot13", "shout"), names)
    }

    @Test
    fun excludesLibraryClassesUnderQiqNamespace(project: Project) {
        // The built-in HtmlHelpers lives under \Qiq\ and is covered by the
        // runtime stub — it must contribute no auto-discovered helpers even
        // though its methods are public.
        val names = helperNames(
            project,
            """
            <?php
            namespace Qiq\Helper\Html;

            class HtmlHelpers
            {
                public function anchor(string ${'$'}href): string { return ''; }
                public function textField(string ${'$'}name): string { return ''; }
            }
            """.trimIndent(),
        )
        assertEquals(emptySet(), names)
    }

    @Test
    fun userClassWithNoEligibleMethodsContributesNothing(project: Project) {
        val names = helperNames(
            project,
            """
            <?php
            namespace App;

            class EmptyHelpers
            {
                protected function p(): void {}
                public static function s(): void {}
            }
            """.trimIndent(),
        )
        assertEquals(emptySet(), names)
    }

    private fun helperNames(project: Project, php: String): Set<String> {
        var names: Set<String> = emptySet()
        ApplicationManager.getApplication().runReadAction {
            val file: PsiFile = PsiFileFactory.getInstance(project)
                .createFileFromText("Helpers.php", PhpFileType.INSTANCE, php)
            val phpClass = PsiTreeUtil.collectElementsOfType(file, PhpClass::class.java).single()
            names = QiqHelpersClassResolver.helperMethodsOf(phpClass).map { it.name }.toSet()
        }
        return names
    }
}
