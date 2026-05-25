package io.github.jingu.idea_qiq_plugin.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.resources.ProjectExtension
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqHostManipulatorTest {

    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @Test
    fun updateTextRenamesIdentifierInEscapeHost(project: Project) {
        assertUpdateTextRoundTrips(
            project,
            initial = "{{h \$article->title }}",
            expectedHostText = " \$article->title ",
            newText = " \$article->headline ",
        )
    }

    @Test
    fun updateTextWorksForEachEscapeDirective(project: Project) {
        for (modifier in listOf("h", "a", "j", "u", "c")) {
            assertUpdateTextRoundTrips(
                project,
                initial = "{{$modifier \$obj->old }}",
                expectedHostText = " \$obj->old ",
                newText = " \$obj->updated ",
            )
        }
    }

    @Test
    fun updateTextWorksForRawEchoHost(project: Project) {
        assertUpdateTextRoundTrips(
            project,
            initial = "{{= \$user->name }}",
            expectedHostText = " \$user->name ",
            newText = " \$user->displayName ",
        )
    }

    @Test
    fun updateTextWorksForCodeHost(project: Project) {
        assertUpdateTextRoundTrips(
            project,
            initial = "{{ foreach (\$items as \$item): }}",
            expectedHostText = " foreach (\$items as \$item): ",
            newText = " foreach (\$rows as \$row): ",
        )
    }

    @Test
    fun updateTextWorksForInlinePhpHost(project: Project) {
        assertUpdateTextRoundTrips(
            project,
            initial = "<?php \$user = new User(); ?>",
            expectedHostText = " \$user = new User(); ",
            newText = " \$person = new User(); ",
        )
    }

    private fun assertUpdateTextRoundTrips(
        project: Project,
        initial: String,
        expectedHostText: String,
        newText: String,
    ) {
        val psiFile = readAction { createQiqFile(project, initial) }
        val host = readAction {
            PsiTreeUtil.collectElementsOfType(psiFile, PsiLanguageInjectionHost::class.java).single()
        }
        readAction {
            assertEquals(expectedHostText, host.text, "Precondition: host text differs from expectation")
        }

        val updated = WriteAction.computeAndWait<PsiLanguageInjectionHost, RuntimeException> {
            host.updateText(newText)
        }

        readAction {
            assertEquals(newText, updated.text, "updateText did not reflect the new content")
        }
    }

    private fun createQiqFile(project: Project, text: String): PsiFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText("sample.qiq", QiqFileType, text)

    private fun <T> readAction(block: () -> T): T =
        ApplicationManager.getApplication().runReadAction<T>(block)
}
