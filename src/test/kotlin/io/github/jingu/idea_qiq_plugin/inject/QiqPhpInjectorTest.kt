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

        /** Always prepended to the first fragment so `$this->...()` resolves to the stub. */
        private const val THIS_PRELUDE = "<?php /** @var \\QiqTemplate \$this */ ?>"
    }

    private val injector = QiqPhpInjector()

    @Test
    fun aggregatesAllFragmentsIntoSinglePhpStream(project: Project) {
        val expected = THIS_PRELUDE + """
            <?php foreach (${ '$'}items as ${ '$'}item): ?><?= \QiqRuntimeFunctions::h(${ '$'}item->name) ?><?php endforeach; ?>
            """.trimIndent()

        ApplicationManager.getApplication().runReadAction {
            val psiFile = createQiqFile(
                project,
                """
                {{ foreach (${ '$'}items as ${ '$'}item): }}
                <li>{{h ${ '$'}item->name }}</li>
                {{ endforeach }}
                """.trimIndent()
            )
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

    @Test
    fun escapeDirectivesAreRoutedThroughRuntimeFunctions(project: Project) {
        val cases = mapOf(
            "h" to "<?= \\QiqRuntimeFunctions::h(",
            "a" to "<?= \\QiqRuntimeFunctions::a(",
            "j" to "<?= \\QiqRuntimeFunctions::j(",
            "u" to "<?= \\QiqRuntimeFunctions::u(",
            "c" to "<?= \\QiqRuntimeFunctions::c(",
        )

        ApplicationManager.getApplication().runReadAction {
            for ((modifier, expectedPrefix) in cases) {
                val psiFile = createQiqFile(project, "{{$modifier \$value }}")
                val host = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).single()

                val registrar = RecordingRegistrar()
                injector.getLanguagesToInject(registrar, host)

                val call = registrar.calls.single { it.language == PhpLanguage.INSTANCE }
                assertEquals(THIS_PRELUDE + expectedPrefix, call.prefix, "Wrong prefix for {{$modifier ... }}")
                assertEquals(") ?>", call.suffix, "Wrong suffix for {{$modifier ... }}")
            }
        }
    }

    @Test
    fun strictTypesPreludeIsAbsentByDefault(project: Project) {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = createQiqFile(project, "{{h \$value }}")
            val host = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).single()

            val registrar = RecordingRegistrar()
            injector.getLanguagesToInject(registrar, host)

            val call = registrar.calls.single { it.language == PhpLanguage.INSTANCE }
            val prefix = call.prefix.orEmpty()
            assert(!prefix.contains("declare(strict_types")) {
                "Expected no strict_types prelude by default, got prefix='$prefix'"
            }
        }
    }

    @Test
    fun strictTypesPreludeIsPrependedWhenEnabled(project: Project) {
        val settings = io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService.getInstance(project)
        val previous = settings.isStrictTypesEnabled()
        settings.setStrictTypesEnabled(true)
        try {
            ApplicationManager.getApplication().runReadAction {
                val psiFile = createQiqFile(project, "{{h \$value }}")
                val host = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).single()

                val registrar = RecordingRegistrar()
                injector.getLanguagesToInject(registrar, host)

                val call = registrar.calls.single { it.language == PhpLanguage.INSTANCE }
                val prefix = call.prefix.orEmpty()
                assert(prefix.startsWith("<?php declare(strict_types=1); ?>")) {
                    "Expected strict_types prelude to be prepended, got prefix='$prefix'"
                }
                // The existing escape-routing prefix must still be present after
                // the prelude so the directive type information is preserved.
                assert(prefix.contains("QiqRuntimeFunctions") && prefix.endsWith("h(")) {
                    "Original escape prefix should follow the prelude, got prefix='$prefix'"
                }
            }
        } finally {
            settings.setStrictTypesEnabled(previous)
        }
    }

    @Test
    fun strictTypesPreludeOnlyTouchesFirstFragment(project: Project) {
        val settings = io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService.getInstance(project)
        val previous = settings.isStrictTypesEnabled()
        settings.setStrictTypesEnabled(true)
        try {
            ApplicationManager.getApplication().runReadAction {
                val psiFile = createQiqFile(
                    project,
                    """
                    {{ foreach (${ '$'}items as ${ '$'}item): }}
                    <li>{{h ${ '$'}item->name }}</li>
                    {{ endforeach }}
                    """.trimIndent(),
                )
                val hosts = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).toList()
                val registrar = RecordingRegistrar()
                injector.getLanguagesToInject(registrar, hosts.first())

                val phpCalls = registrar.calls.filter { it.language == PhpLanguage.INSTANCE }
                assertEquals(3, phpCalls.size, "Expected 3 PHP fragments")

                val preludeMarker = "declare(strict_types=1)"
                val withPrelude = phpCalls.count { it.prefix.orEmpty().contains(preludeMarker) }
                assertEquals(1, withPrelude, "Strict types prelude must appear in exactly one fragment")
                assert(phpCalls.first().prefix.orEmpty().contains(preludeMarker)) {
                    "Prelude must be on the FIRST fragment"
                }
            }
        } finally {
            settings.setStrictTypesEnabled(previous)
        }
    }

    @Test
    fun togglingStrictTypesSettingInvalidatesCachedInjectionPlan(project: Project) {
        val settings = io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService.getInstance(project)
        val previous = settings.isStrictTypesEnabled()
        try {
            ApplicationManager.getApplication().runReadAction {
                val psiFile = createQiqFile(project, "{{h \$value }}")
                val host = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).single()

                // First pass with the setting OFF: prelude must be absent.
                settings.setStrictTypesEnabled(false)
                val off = RecordingRegistrar().also { injector.getLanguagesToInject(it, host) }
                assert(!off.calls.single { it.language == PhpLanguage.INSTANCE }.prefix.orEmpty()
                    .contains("declare(strict_types"))

                // Toggle ON without editing the file: prelude must appear
                // even though the modificationStamp is unchanged. Regression
                // test for the cache key originally including only the
                // modification stamp.
                settings.setStrictTypesEnabled(true)
                val on = RecordingRegistrar().also { injector.getLanguagesToInject(it, host) }
                assert(on.calls.single { it.language == PhpLanguage.INSTANCE }.prefix.orEmpty()
                    .startsWith("<?php declare(strict_types=1); ?>"))

                // Toggle OFF again: prelude must disappear.
                settings.setStrictTypesEnabled(false)
                val offAgain = RecordingRegistrar().also { injector.getLanguagesToInject(it, host) }
                assert(!offAgain.calls.single { it.language == PhpLanguage.INSTANCE }.prefix.orEmpty()
                    .contains("declare(strict_types"))
            }
        } finally {
            settings.setStrictTypesEnabled(previous)
        }
    }

    @Test
    fun rawDirectiveUsesPlainEchoTag(project: Project) {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = createQiqFile(project, "{{= \$value }}")
            val host = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).single()

            val registrar = RecordingRegistrar()
            injector.getLanguagesToInject(registrar, host)

            val call = registrar.calls.single { it.language == PhpLanguage.INSTANCE }
            assertEquals(THIS_PRELUDE + "<?= ", call.prefix, "{{= ... }} should keep the plain echo tag")
            assertEquals(" ?>", call.suffix)
        }
    }

    @Test
    fun rawEchoHelperCallStartingWithEscapeLetterIsNotStripped(project: Project) {
        // Regression: `{{= asset(...) }}` is a RAW_CONTENT host whose content
        // starts with `a`. The injector must NOT treat the leading `a` as the
        // `{{a }}` attribute-escape modifier — doing so corrupted the call to
        // `sset(...)`. Helper names beginning with h/a/j/u/c (asset,
        // currentUrl, upper, ...) must pass through verbatim.
        val cases = listOf(
            "{{= asset('/js/common.js') }}" to "asset('/js/common.js')",
            "{{= currentUrl(\$url) }}" to "currentUrl(\$url)",
            "{{= upper(\$name) }}" to "upper(\$name)",
        )

        ApplicationManager.getApplication().runReadAction {
            for ((template, expectedExpr) in cases) {
                val psiFile = createQiqFile(project, template)
                val host = PsiTreeUtil.collectElementsOfType(psiFile, QiqCodeHost::class.java).single()

                val registrar = RecordingRegistrar()
                injector.getLanguagesToInject(registrar, host)

                val call = registrar.calls.single { it.language == PhpLanguage.INSTANCE }
                assertEquals(THIS_PRELUDE + "<?= ", call.prefix, "Raw echo must use a plain echo tag for $template")
                assertEquals(" ?>", call.suffix, "Wrong suffix for $template")

                val injectedExpr = call.host.text.substring(call.range.startOffset, call.range.endOffset)
                assertEquals(expectedExpr, injectedExpr, "Leading byte must not be stripped for $template")
            }
        }
    }

    private fun createQiqFile(project: Project, text: String): PsiFile {
        // In-memory PSI construction: no write lock required, and wrapping in
        // WriteAction throws if the caller is already inside a ReadAction (which
        // some test extensions enter implicitly).
        return PsiFileFactory.getInstance(project)
            .createFileFromText("sample.qiq", QiqFileType, text)
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
    }

    private data class RecordedCall(
        val language: Language,
        val prefix: String?,
        val suffix: String?,
        val host: PsiLanguageInjectionHost,
        val range: TextRange
    )
}
