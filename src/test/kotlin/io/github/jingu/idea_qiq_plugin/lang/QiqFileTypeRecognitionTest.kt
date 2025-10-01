package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.testFramework.LightVirtualFile
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QiqFileTypeRecognitionTest {

    @Test
    fun `php template with qiq comment is recognized`() {
        val fileContent = "{{ // New Qiq template }}\n"
        val virtualFile = LightVirtualFile("new_template.php", fileContent)

        val overrider = QiqFileTypeOverrider()
        val overridden = overrider.getOverriddenFileType(virtualFile)

        assertSame(QiqFileType, overridden, "PHP template should be recognized as Qiq")
        assertTrue(virtualFile.getUserData(QiqFileTypeOverrider.QIQ_MARKER) == true)
    }

    @Test
    fun `file type falls back to marker for php`() {
        val virtualFile = LightVirtualFile("existing_template.php", "")
        virtualFile.putUserData(QiqFileTypeOverrider.QIQ_MARKER, true)

        val result = QiqFileType.isMyFileType(virtualFile)

        assertTrue(result, ".php file with Qiq marker must be treated as Qiq")
    }
}
