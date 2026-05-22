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
import io.github.jingu.idea_qiq_plugin.lang.QiqTokenTypes
import io.github.jingu.idea_qiq_plugin.psi.QiqCodeHost
import io.github.jingu.idea_qiq_plugin.psi.QiqPhpHost
import io.github.jingu.idea_qiq_plugin.settings.QiqSettingsService
import io.github.jingu.idea_qiq_plugin.util.QiqUtil
import java.util.Locale

/**
 * Inject PHP into:
 * 1. Qiq code fragments like {{ ... }}, {{= ... }}, {{h ... }}, {{j ... }}
 * 2. PHP blocks within HTML templates like <?php ... ?>
 */
class QiqPhpInjector : MultiHostInjector, DumbAware {

    private val log = Logger.getInstance(QiqPhpInjector::class.java)

    // Per-file dedup: emit the resolved stub selection once per file so that
    // users (and bug reports) can verify which QiqRuntimeFunctions* class is
    // being injected without scanning thousands of duplicate lines. Logged at
    // debug level — enable `io.github.jingu.idea_qiq_plugin.inject.QiqPhpInjector`
    // in Help > Diagnostic Tools > Debug Log Settings to surface it.
    private val stubSelectionLogged = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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

    private data class ReservedDirectiveRewrite(
        val prefix: String,
        val range: TextRange
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
            // The parser strips the leading modifier byte (h/a/j/u/c/=) from the host
            // text and encodes it in the element type instead, so derive the directive
            // from the element type. Defensive fallback to the first character handles
            // legacy PSI shapes (text-based kind detection in QiqCodeHost).
            val escapeMethod = escapeMethodFor(host)

            if (escapeMethod != null) {
                // Route escape directives through QiqRuntimeFunctions* so that
                // PhpStorm validates the argument against the PHPDoc signature.
                // The runtime class is picked based on the qiq/qiq major
                // version detected in composer.lock: v1 has string-only
                // signatures, while v2+ accept null|scalar|\Stringable.
                val runtimeClass = resolveRuntimeClass(host)
                prefix = "<?= \\$runtimeClass::$escapeMethod("
                suffix = ") ?>"
            } else {
                // {{= ... }} or unclassified print-like host: emit a plain echo tag.
                if (trimmedContent.first() == '=') {
                    var newStart = range.startOffset + 1
                    while (newStart < range.endOffset && raw[newStart].isWhitespace()) {
                        newStart++
                    }
                    if (newStart >= range.endOffset) return null
                    injectionRange = TextRange(newStart, range.endOffset)
                }
                prefix = "<?= "
                suffix = " ?>"
            }
        } else {
            val lower = trimmedContent.lowercase(Locale.ROOT)

            rewriteReservedDirective(raw, range)?.let {
                prefix = it.prefix
                injectionRange = it.range
            }

            val head = lower.substringBefore(' ')
            val needsSemicolon = !trimmedContent.endsWith(";") && !trimmedContent.endsWith(":") &&
                head !in setOf("else", "elseif", "case", "default")

            if (needsSemicolon) suffix = "; ?>"
        }

        return PhpInjectionFragment(host, injectionRange, prefix, suffix)
    }

    private fun resolveRuntimeClass(host: QiqCodeHost): String {
        val vf = host.containingFile?.virtualFile ?: return DEFAULT_RUNTIME_CLASS
        val major = QiqSettingsService.getInstance(host.project).resolveQiqMajorVersion(vf)
        val runtimeClass = if (major == 1) STRICT_RUNTIME_CLASS else DEFAULT_RUNTIME_CLASS
        if (stubSelectionLogged.add(vf.path) && log.isDebugEnabled) {
            log.debug("Qiq stub selection: major=$major, class=$runtimeClass, file=${vf.path}")
        }
        return runtimeClass
    }

    private fun escapeMethodFor(host: QiqCodeHost): Char? = when (host.node.elementType) {
        QiqTokenTypes.ESCAPE_H_CONTENT -> 'h'
        QiqTokenTypes.ESCAPE_A_CONTENT -> 'a'
        QiqTokenTypes.ESCAPE_J_CONTENT -> 'j'
        QiqTokenTypes.ESCAPE_U_CONTENT -> 'u'
        QiqTokenTypes.ESCAPE_C_CONTENT -> 'c'
        else -> null
    }

    private fun rewriteReservedDirective(raw: String, range: TextRange): ReservedDirectiveRewrite? {
        val snippet = raw.substring(range.startOffset, range.endOffset)
        if (snippet.isEmpty()) return null

        val span = QiqUtil.findReservedDirectiveSpan(snippet) ?: return null

        val parenIndex = snippet.indexOf('(', span.startOffset + span.length)
        if (parenIndex == -1) return null

        val injectionStart = range.startOffset + parenIndex

        return ReservedDirectiveRewrite(
            prefix = "<?php \\QiqRuntimeFunctions::extends",
            range = TextRange(injectionStart, range.endOffset)
        )
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

    companion object {
        private const val DEFAULT_RUNTIME_CLASS = "QiqRuntimeFunctions"
        private const val STRICT_RUNTIME_CLASS = "QiqRuntimeFunctionsStrict"
    }
}
