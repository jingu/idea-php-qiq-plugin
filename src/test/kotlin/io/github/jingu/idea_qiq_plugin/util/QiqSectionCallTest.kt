package io.github.jingu.idea_qiq_plugin.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.resources.ProjectExtension
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the call recognition that gates section completion, navigation and
 * inspection. Uses in-memory PHP PSI (no index) to build the call shapes the
 * features see around the section-name string literal.
 */
@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqSectionCallTest {

    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @Test
    fun readerAcceptsBareAndThisCalls(project: Project) {
        assertTrue(reader(project, "<?php getSection('x');"))
        assertTrue(reader(project, "<?php hasSection('x');"))
        assertTrue(reader(project, "<?php \$this->getSection('x');"))
        assertTrue(reader(project, "<?php \$this->hasSection('x');"))
    }

    @Test
    fun readerRejectsOtherReceiversStaticAndWriters(project: Project) {
        assertFalse(reader(project, "<?php \$obj->getSection('x');"))
        assertFalse(reader(project, "<?php Foo::getSection('x');")) // static call
        assertFalse(reader(project, "<?php setSection('x');")) // writer, not a reader
    }

    @Test
    fun readerRejectsNonFirstArgument(project: Project) {
        // The second string is not the name position.
        assertFalse(reader(project, "<?php getSection('x', 'y');", index = 1))
    }

    @Test
    fun writerAcceptsSetSectionCalls(project: Project) {
        assertTrue(writer(project, "<?php setSection('x');"))
        assertTrue(writer(project, "<?php \$this->setSection('x');"))
        assertFalse(writer(project, "<?php getSection('x');")) // reader, not a writer
        assertFalse(writer(project, "<?php \$obj->setSection('x');"))
    }

    private fun reader(project: Project, php: String, index: Int = 0): Boolean =
        onLiteral(project, php, index) { QiqSectionCall.isReaderArg(it) }

    private fun writer(project: Project, php: String, index: Int = 0): Boolean =
        onLiteral(project, php, index) { QiqSectionCall.isWriterArg(it) }

    private fun onLiteral(
        project: Project,
        php: String,
        index: Int,
        gate: (StringLiteralExpression) -> Boolean,
    ): Boolean {
        var result = false
        ApplicationManager.getApplication().runReadAction {
            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("snippet.php", PhpFileType.INSTANCE, php)
            val literal = PsiTreeUtil.collectElementsOfType(file, StringLiteralExpression::class.java).toList()[index]
            result = gate(literal)
        }
        return result
    }
}
