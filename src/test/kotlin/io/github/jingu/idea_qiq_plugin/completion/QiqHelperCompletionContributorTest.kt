package io.github.jingu.idea_qiq_plugin.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.resources.ProjectExtension
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MemberReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the position gate that decides where helper-name completion fires.
 * Uses in-memory PHP PSI (no index needed) to build the reference shapes the
 * completion sees as `position.parent`.
 */
@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqHelperCompletionContributorTest {

    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    @Test
    fun firesOnBareNameBeforeParens(project: Project) {
        // `{{ helper }}` injects `<?php helper ?>` -> ConstantReference.
        assertTrue(gateFor(project, "<?php helper;", ConstantReference::class.java))
    }

    @Test
    fun firesOnBareCall(project: Project) {
        assertTrue(gateFor(project, "<?php helper();", FunctionReference::class.java))
    }

    @Test
    fun firesOnThisMemberReference(project: Project) {
        // `{{ $this->helper }}` -> MemberReference qualified by $this.
        assertTrue(gateFor(project, "<?php \$this->helper;", MemberReference::class.java))
    }

    @Test
    fun firesOnThisMethodCall(project: Project) {
        assertTrue(gateFor(project, "<?php \$this->helper();", MemberReference::class.java))
    }

    @Test
    fun doesNotFireOnOtherObjectMember(project: Project) {
        // `$article->title` is a real property access, not a helper.
        assertFalse(gateFor(project, "<?php \$article->title;", MemberReference::class.java))
    }

    @Test
    fun doesNotFireOnUnrelatedElement(project: Project) {
        assertFalse(QiqHelperCompletionContributor.isHelperCallNamePosition(null))
    }

    /** Parse [php], take the first [type] node, and evaluate the gate — all in a read action. */
    private fun <T : PsiElement> gateFor(project: Project, php: String, type: Class<T>): Boolean {
        var result = false
        ApplicationManager.getApplication().runReadAction {
            val file = PsiFileFactory.getInstance(project)
                .createFileFromText("snippet.php", PhpFileType.INSTANCE, php)
            val ref = PsiTreeUtil.collectElementsOfType(file, type).first()
            result = QiqHelperCompletionContributor.isHelperCallNamePosition(ref)
        }
        return result
    }
}
