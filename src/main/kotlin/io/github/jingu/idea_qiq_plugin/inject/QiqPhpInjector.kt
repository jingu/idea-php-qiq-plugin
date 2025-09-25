package io.github.jingu.idea_qiq_plugin.inject

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.PhpLanguage
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost
import io.github.jingu.idea_qiq_plugin.psi.QiqPhpHost
import java.util.Locale

/**
 * Inject PHP into:
 * 1. Qiq code fragments like {{ ... }}, {{= ... }}, {{h ... }}, {{j ... }}
 * 2. PHP blocks within HTML templates like <?php ... ?>
 */
class QiqPhpInjector : MultiHostInjector, DumbAware {

    private val log = Logger.getInstance(QiqPhpInjector::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        when (host) {
            is QiqCodeHost -> injectIntoQiqCodeHost(registrar, host)
            is QiqPhpHost -> injectIntoQiqPhpHost(registrar, host)
        }
    }

    private fun injectIntoQiqCodeHost(registrar: MultiHostRegistrar, host: QiqCodeHost) {
        if (!host.isValidHost) return

        val raw = host.text
        if (raw.isEmpty()) return

        // Debug hook (will appear in idea.log)
        log.debug("QiqPhpInjector: QiqCodeHost, text='${raw.take(40)}'")

        // Skip leading whitespace for injection
        val leadingWs = raw.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) raw.length else it }
        if (leadingWs >= raw.length) return

        val lastContentIndex = raw.indexOfLast { !it.isWhitespace() }
        if (lastContentIndex < leadingWs) return

        var range = TextRange(leadingWs, lastContentIndex + 1)

        val isPrintLike = host.isPrintLike()
        var trimmedContent = raw.substring(range.startOffset, range.endOffset).trim()
        if (trimmedContent.isEmpty()) return

        var prefix = "<?php "
        var suffix = " ?>"
        var injectionRange = range

        if (isPrintLike) {
            val firstChar = trimmedContent.first()
            if (firstChar in setOf('=', 'h', 'j', 'a', 'u', 'c')) {
                var newStart = range.startOffset + 1
                while (newStart < range.endOffset && raw[newStart].isWhitespace()) {
                    newStart++
                }
                if (newStart >= range.endOffset) return
                injectionRange = TextRange(newStart, range.endOffset)
            }
            val injectionContent = raw.substring(injectionRange.startOffset, injectionRange.endOffset)
            val variables = extractVariables(injectionContent)
            prefix = buildPrintPrefix(variables)
            suffix = " ?>"
        } else {
            val lower = trimmedContent.lowercase(Locale.ROOT)

            val closingStub = closingDirectiveBody(lower)
            if (closingStub != null) {
                prefix += closingStub
                suffix = "; ?>"
            } else {
                val head = lower.substringBefore(' ')
                val needsSemicolon = !trimmedContent.endsWith(";") && !trimmedContent.endsWith(":") &&
                    head !in setOf("else", "elseif", "case", "default")

                if (needsSemicolon) suffix = "; ?>"
            }
        }

        registrar.startInjecting(PhpLanguage.INSTANCE)
        registrar.addPlace(prefix, suffix, host, injectionRange)
        registrar.doneInjecting()
    }

    private fun buildPrintPrefix(variables: Set<String>): String {
        if (variables.isEmpty()) return "<?= "
        val annotations = variables.joinToString(separator = " ") { "/** @var mixed $it */" }
        return "<?php $annotations ?><?= "
    }

    private fun extractVariables(content: String): Set<String> {
        val regex = Regex("\\$[a-zA-Z_][a-zA-Z0-9_]*")
        return regex.findAll(content)
            .map { it.value }
            .filterNot { it == "\$this" }
            .toSet()
    }

    private fun closingDirectiveBody(lowerContent: String): String? {
        return when {
            lowerContent.startsWith("endif") -> "if (false): "
            lowerContent.startsWith("endforeach") -> "foreach ([] as ${'$'}__qiqStub): "
            lowerContent.startsWith("endfor") -> "for (${ '$' }__qiqI = 0; ${ '$' }__qiqI < 0; ${ '$' }__qiqI++): "
            lowerContent.startsWith("endwhile") -> "while (false): "
            lowerContent.startsWith("endswitch") -> "switch (false): "
            else -> null
        }
    }

    private fun injectIntoQiqPhpHost(registrar: MultiHostRegistrar, host: QiqPhpHost) {
        if (!host.isValidHost) return

        val raw = host.text
        if (raw.isEmpty()) return

        // Debug hook (will appear in idea.log)
        log.debug("QiqPhpInjector: QiqPhpHost, text='${raw.take(40)}'")

        // For PHP blocks, inject the entire content as PHP
        val range = TextRange(0, raw.length)

        registrar.startInjecting(PhpLanguage.INSTANCE)
        registrar.addPlace("<?php ", " ?>", host, range)
        registrar.doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(QiqCodeHost::class.java, QiqPhpHost::class.java)
}
