package io.github.jingu.idea_qiq_plugin.lang

import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Comparator

@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqFileTypeOverriderTest {

    private val overrider = QiqFileTypeOverrider()

    @Test
    fun `does not override LightVirtualFile`() {
        val lightFile = LightVirtualFile(
            "sample.php",
            Language.ANY,
            "{{ setSection('header') }}\n{{ endSection() }}"
        )

        val type = overrider.getOverriddenFileType(lightFile)
        assertNull(type, "LightVirtualFile must not be overridden as Qiq")
    }

    @Test
    fun `overrides local virtual file`() {
        val text = """
            {{ setSection('header') }}
            content
            {{ endSection() }}
        """.trimIndent()

        val tempDir = Files.createTempDirectory("qiq-overrider-test")
        try {
            val path = tempDir.resolve("template.php")
            Files.writeString(path, text, StandardCharsets.UTF_8)

            val virtualFile = WriteAction.compute<VirtualFile?, RuntimeException> {
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())
            } ?: error("Virtual file not found for temp file")

            val type: FileType? = ReadAction.compute<FileType?, RuntimeException> {
                overrider.getOverriddenFileType(virtualFile)
            }

            assertEquals(QiqFileType, type, "Local template should be detected as Qiq")
            assertTrue(
                virtualFile.getUserData(QiqFileTypeOverrider.QIQ_MARKER) == true,
                "Marker flag should be set to avoid repeated re-evaluation"
            )
        } finally {
            runCatching {
                Files.walk(tempDir).use { stream ->
                    stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }
}
