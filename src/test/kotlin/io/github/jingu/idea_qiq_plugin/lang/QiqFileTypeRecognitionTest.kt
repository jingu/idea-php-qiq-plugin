package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqFileTypeRecognitionTest {

    @Test
    fun `php template with qiq comment is recognized`() {
        withTempVirtualFile("new_template.php", "{{ // New Qiq template }}\n") { file ->
            val overrider = QiqFileTypeOverrider()
            val overridden = ReadAction.compute<FileType?, RuntimeException> {
                overrider.getOverriddenFileType(file)
            }

            assertSame(QiqFileType, overridden, "PHP template should be recognized as Qiq")
            assertTrue(file.getUserData(QiqFileTypeOverrider.QIQ_MARKER) == true)
        }
    }

    @Test
    fun `file type falls back to marker for php`() {
        val virtualFile = LightVirtualFile("existing_template.php", "")
        virtualFile.putUserData(QiqFileTypeOverrider.QIQ_MARKER, true)

        val result = QiqFileType.isMyFileType(virtualFile)

        assertTrue(result, ".php file with Qiq marker must be treated as Qiq")
    }

    @Test
    fun `missing closing delimiter is ignored`() {
        withTempVirtualFile("broken_template.php", "{{ extends('layout')\n") { file ->
            val overrider = QiqFileTypeOverrider()
            val overridden = ReadAction.compute<FileType?, RuntimeException> {
                overrider.getOverriddenFileType(file)
            }

            assertNull(overridden, "File without closing delimiter should remain original type")
            assertTrue(file.getUserData(QiqFileTypeOverrider.QIQ_MARKER) != true)
        }
    }

    private fun <T> withTempVirtualFile(name: String, content: String, block: (VirtualFile) -> T): T {
        val tempDir = Files.createTempDirectory("qiq-filetype-test")
        return try {
            val path = tempDir.resolve(name)
            Files.writeString(path, content, StandardCharsets.UTF_8)

            val virtualFile = WriteAction.compute<VirtualFile?, RuntimeException> {
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())
            } ?: error("VirtualFile not found for $name")

            block(virtualFile)
        } finally {
            runCatching {
                Files.walk(tempDir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }
}
