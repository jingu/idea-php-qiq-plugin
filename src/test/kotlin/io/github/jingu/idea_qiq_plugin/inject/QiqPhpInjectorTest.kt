package io.github.jingu.idea_qiq_plugin.inject

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.resources.ProjectExtension
import com.jetbrains.php.lang.PhpLanguage
import io.github.jingu.idea_qiq_plugin.lang.QiqFileType
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@RunInEdt
@ExtendWith(TestApplicationExtension::class)
class QiqPhpInjectorTest {

    companion object {
        @JvmField
        @RegisterExtension
        val projectExtension = ProjectExtension()
    }

    private val injector = QiqPhpInjector()

    @Test
    fun aggregatesAllFragmentsIntoSinglePhpStream(project: Project) {
        val psiFile = createQiqFile(
            project,
            """
            {{ foreach (${ '$'}items as ${ '$'}item): }}
            <li>{{h ${ '$'}item->name }}</li>
            {{ endforeach }}
            """.trimIndent()
        )

        val expected = """
            <?php foreach (${ '$'}items as ${ '$'}item): ?><?= ${ '$'}item->name ?><?php endforeach; ?>
            """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val hosts = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).toList()
            assertEquals(3, hosts.size, "Unexpected number of QiqCodeHost elements")

            val registrar = RecordingRegistrar()
            injector.getLanguagesToInject(registrar, hosts.first())

            val phpCalls = registrar.calls.filter { it.language == PhpLanguage.INSTANCE }
            assertEquals(3, phpCalls.size, "Missing PHP injection fragments")
            assertEquals(hosts.toSet(), phpCalls.map { it.host }.toSet(), "Not all hosts received injections")

            val assembled = assembleInjectedText(phpCalls)
            assertEquals(expected, assembled)

            hosts.forEach { host ->
                val perHostRegistrar = RecordingRegistrar()
                injector.getLanguagesToInject(perHostRegistrar, host)
                val perHostCalls = perHostRegistrar.calls.filter { it.language == PhpLanguage.INSTANCE }
                assertEquals(
                    3,
                    perHostCalls.size,
                    "Injection result is inconsistent for host ${'$'}{host.text}"
                )
            }
        }
    }

    private fun createQiqFile(project: Project, text: String): PsiFile {
        return WriteAction.compute<PsiFile, RuntimeException> {
            PsiFileFactory.getInstance(project).createFileFromText("sample.qiq", QiqFileType, text)
        }
    }

    private fun assembleInjectedText(calls: List<RecordedCall>): String {
        val builder = StringBuilder()
        calls.forEach { call ->
            call.prefix?.let(builder::append)
            builder.append(call.host.text.substring(call.range.startOffset, call.range.endOffset))
            call.suffix?.let(builder::append)
        }
        return builder.toString()
    }

    private class RecordingRegistrar : MultiHostRegistrar {
        val calls = mutableListOf<RecordedCall>()
        private var currentLanguage: Language? = null

        override fun startInjecting(language: Language): MultiHostRegistrar {
            currentLanguage = language
            return this
        }

        override fun addPlace(prefix: String?, suffix: String?, host: PsiLanguageInjectionHost, range: TextRange): MultiHostRegistrar {
            val language = requireNotNull(currentLanguage) { "addPlace called before startInjecting" }
            calls += RecordedCall(language, prefix, suffix, host, range)
            return this
        }

        override fun doneInjecting() {
            currentLanguage = null
        }

        fun clear() {
            calls.clear()
            currentLanguage = null
        }

        fun addPlace(prefix: String?, suffix: String?, host: PsiLanguageInjectionHost, ranges: List<TextRange>): MultiHostRegistrar {
            ranges.forEach { addPlace(prefix, suffix, host, it) }
            return this
        }

        fun addPlace(prefix: String?, suffix: String?, host: PsiElement, range: TextRange): MultiHostRegistrar {
            require(host is PsiLanguageInjectionHost) { "PsiLanguageInjectionHost required" }
            return addPlace(prefix, suffix, host, range)
        }

        fun addPlace(prefix: String?, suffix: String?, host: PsiElement, ranges: List<TextRange>): MultiHostRegistrar {
            ranges.forEach { addPlace(prefix, suffix, host, it) }
            return this
        }
    }

    private data class RecordedCall(
        val language: Language,
        val prefix: String?,
        val suffix: String?,
        val host: PsiLanguageInjectionHost,
        val range: TextRange
    )
}
