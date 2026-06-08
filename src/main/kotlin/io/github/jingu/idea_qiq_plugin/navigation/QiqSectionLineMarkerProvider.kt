package io.github.jingu.idea_qiq_plugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import io.github.jingu.idea_qiq_plugin.block.QiqBlockType
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport
import io.github.jingu.idea_qiq_plugin.util.QiqSectionCall
import io.github.jingu.idea_qiq_plugin.util.QiqSectionIndex

/**
 * A gutter marker on a `setSection('x')` / `setBlock('x')` name that navigates to
 * the `getSection('x')` / `getBlock('x')` usages of that name across the detected
 * template roots — the reverse of the section-name Go to Declaration.
 */
class QiqSectionLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Anchor on a leaf only (line markers must target leaf elements).
        if (element.firstChild != null) return
        val literal = element.parent as? StringLiteralExpression ?: return
        if (literal.firstChild !== element) return
        if (!QiqInjectionSupport.isInQiqFile(literal)) return

        val type = QiqSectionCall.writerTypeForArg(literal) ?: return
        val name = literal.contents
        if (name.isEmpty()) return

        val contextFile = InjectedLanguageManager.getInstance(element.project).getTopLevelFile(literal)?.virtualFile
            ?: return

        val usages = QiqSectionIndex.usagesByName(element.project, contextFile, name, type)
        if (usages.isEmpty()) return

        val head = if (type == QiqBlockType.BLOCK) "getBlock" else "getSection"
        val psiManager = PsiManager.getInstance(element.project)
        val targets = usages.mapNotNull { usage ->
            psiManager.findFile(usage.file)?.let { QiqSectionTarget(it, usage.name, head, usage.nameRange.startOffset) }
        }
        if (targets.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod)
            .setTargets(targets)
            .setTooltipText("Go to $head('$name') usages")
        result.add(builder.createLineMarkerInfo(element))
    }
}
