package io.github.jingu.idea_qiq_plugin.inject

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
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

    private data class InjectionPlan(
        val modificationStamp: Long,
        val fragments: List<PhpInjectionFragment>
    )

    private val injectionPlanKey = Key.create<InjectionPlan>(
        "io.github.jingu.idea_qiq_plugin.inject.QiqPhpInjector.PLAN"
    )

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        if (host !is QiqCodeHost && host !is QiqPhpHost) return

        val file = host.containingFile ?: return
        val modificationStamp = file.modificationStamp

        val plan = ensureInjectionPlan(file, modificationStamp)
        if (plan.fragments.isEmpty()) return
        if (plan.fragments.none { it.host == host }) return
        if (!host.isValid) return

        registrar.startInjecting(PhpLanguage.INSTANCE)
        plan.fragments.forEach { fragment ->
            registrar.addPlace(fragment.prefix, fragment.suffix, fragment.host, fragment.range)
        }
        registrar.doneInjecting()
    }

    private fun ensureInjectionPlan(file: PsiFile, modificationStamp: Long): InjectionPlan {
        val existing = file.getUserData(injectionPlanKey)
        if (existing != null && existing.modificationStamp == modificationStamp) {
            return existing
        }

        val injectionHosts = collectInjectionHosts(file)
        val fragments = injectionHosts.mapNotNull { buildInjectionFragment(it) }
        val plan = InjectionPlan(modificationStamp, fragments)
        file.putUserData(injectionPlanKey, plan)
        return plan
    }

    private fun collectInjectionHosts(file: PsiFile): List<PsiLanguageInjectionHost> {
        val codeHosts = PsiTreeUtil.collectElementsOfType(file, QiqCodeHost::class.java)
        val phpHosts = PsiTreeUtil.collectElementsOfType(file, QiqPhpHost::class.java)

        return (codeHosts.asSequence() + phpHosts.asSequence())
            .filter { it.isValidHost }
            .sortedBy { it.textRange.startOffset }
            .toList()
    }

    private data class PhpInjectionFragment(
        val host: PsiLanguageInjectionHost,
        val range: TextRange,
        val prefix: String?,
        val suffix: String?
    )

    private fun buildInjectionFragment(host: PsiLanguageInjectionHost): PhpInjectionFragment? = when (host) {
        is QiqCodeHost -> buildCodeHostFragment(host)
        is QiqPhpHost -> buildPhpHostFragment(host)
        else -> null
    }

    private fun buildCodeHostFragment(host: QiqCodeHost): PhpInjectionFragment? {
        if (!host.isValidHost) return null

        val raw = host.text
        if (raw.isEmpty()) return null

        log.debug("QiqPhpInjector: QiqCodeHost, text='${raw.take(40)}'")

        val leadingWs = raw.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) raw.length else it }
        if (leadingWs >= raw.length) return null

        val lastContentIndex = raw.indexOfLast { !it.isWhitespace() }
        if (lastContentIndex < leadingWs) return null

        var range = TextRange(leadingWs, lastContentIndex + 1)

        val isPrintLike = host.isPrintLike()
        val trimmedContent = raw.substring(range.startOffset, range.endOffset).trim()
        if (trimmedContent.isEmpty()) return null

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
                if (newStart >= range.endOffset) return null
                injectionRange = TextRange(newStart, range.endOffset)
            }

            prefix = "<?= "
            suffix = " ?>"
        } else {
            val lower = trimmedContent.lowercase(Locale.ROOT)

            val head = lower.substringBefore(' ')
            val needsSemicolon = !trimmedContent.endsWith(";") && !trimmedContent.endsWith(":") &&
                head !in setOf("else", "elseif", "case", "default")

            if (needsSemicolon) suffix = "; ?>"
        }

        return PhpInjectionFragment(host, injectionRange, prefix, suffix)
    }

    private fun extractVariables(content: String): Set<String> {
        val regex = Regex("\\$[a-zA-Z_][a-zA-Z0-9_]*")
        return regex.findAll(content)
            .map { it.value }
            .filterNot { it == "\$this" }
            .toSet()
    }

    private fun buildPhpHostFragment(host: QiqPhpHost): PhpInjectionFragment? {
        if (!host.isValidHost) return null

        val raw = host.text
        if (raw.isEmpty()) return null

        log.debug("QiqPhpInjector: QiqPhpHost, text='${raw.take(40)}'")

        val range = TextRange(0, raw.length)

        return PhpInjectionFragment(host, range, "<?php ", " ?>")
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(QiqCodeHost::class.java, QiqPhpHost::class.java)
}
