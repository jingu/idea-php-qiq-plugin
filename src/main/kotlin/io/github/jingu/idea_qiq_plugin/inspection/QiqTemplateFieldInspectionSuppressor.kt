package io.github.jingu.idea_qiq_plugin.inspection

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
import io.github.jingu.idea_qiq_plugin.lang.QiqInjectionSupport

/**
 * Suppresses PhpStorm's "Property accessed via magic method" weak warning
 * (PhpUndefinedFieldInspection) for `$this->name` inside Qiq templates.
 *
 * Templates read their data as `$this->key`, which Qiq\TemplateCore resolves
 * through `__get` at runtime. Because the plugin types `$this` as the QiqTemplate
 * stub (so `$this->setSection(...)` etc. resolve), every such data access would
 * otherwise be flagged as going through a magic method — noise, since the keys
 * are dynamic and all valid. Only `$this->...` field access is suppressed; access
 * on any other receiver keeps the warning.
 */
class QiqTemplateFieldInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != UNDEFINED_FIELD_INSPECTION) return false

        val fieldReference = element as? FieldReference
            ?: PsiTreeUtil.getParentOfType(element, FieldReference::class.java, false)
            ?: return false

        if ((fieldReference.classReference as? Variable)?.name != "this") return false

        return QiqInjectionSupport.isInQiqFile(fieldReference)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY

    private companion object {
        private const val UNDEFINED_FIELD_INSPECTION = "PhpUndefinedFieldInspection"
    }
}
