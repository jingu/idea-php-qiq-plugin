package io.github.jingu.idea_qiq_plugin.util

import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import io.github.jingu.idea_qiq_plugin.block.QiqBlockType
import java.util.Locale

/**
 * Recognizes the Qiq section/block *reader* calls — `getSection('name')` /
 * `getBlock('name')`, bare or `$this->`-qualified — and maps them to the block
 * type whose `setSection`/`setBlock` definitions supply their names. Shared by the
 * name completion, reference (navigation), and undefined-name inspection so they
 * agree on what counts as a reader call. (The matching *definitions* are parsed by
 * [io.github.jingu.idea_qiq_plugin.block.QiqSectionModel].)
 */
object QiqSectionCall {

    private val READERS = mapOf(
        "getsection" to QiqBlockType.SECTION,
        "getblock" to QiqBlockType.BLOCK,
    )

    private val WRITERS = mapOf(
        "setsection" to QiqBlockType.SECTION,
        "setblock" to QiqBlockType.BLOCK,
    )

    /**
     * The block type [call] reads (getSection -> SECTION, getBlock -> BLOCK), or
     * null if it is not a reader call. An *instance* call counts only when the
     * receiver is `$this`, so an unrelated `$other->getSection(...)` is ignored —
     * matching the template-path inspection's receiver gate.
     */
    fun readerType(call: FunctionReference): QiqBlockType? {
        val name = call.name?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        val type = READERS[name] ?: return null
        val receiver = (call as? MethodReference)?.classReference
        if (receiver is Variable && receiver.name != "this") return null
        return type
    }

    /**
     * The reader type if [literal] is the first (name) argument of a reader call,
     * else null. Used by the completion and reference contributors, which start
     * from the string literal under the caret.
     */
    fun readerTypeForArg(literal: StringLiteralExpression): QiqBlockType? =
        typeForArg(literal, ::readerType)

    /**
     * The block type if [literal] is the first (name) argument of a *writer* call
     * (`setSection`/`setBlock`), else null. Used by the line marker that links a
     * definition to its `getSection`/`getBlock` usages.
     */
    fun writerTypeForArg(literal: StringLiteralExpression): QiqBlockType? =
        typeForArg(literal) { call ->
            val name = call.name?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return@typeForArg null
            val type = WRITERS[name] ?: return@typeForArg null
            val receiver = (call as? MethodReference)?.classReference
            if (receiver is Variable && receiver.name != "this") null else type
        }

    private inline fun typeForArg(
        literal: StringLiteralExpression,
        type: (FunctionReference) -> QiqBlockType?,
    ): QiqBlockType? {
        val parameterList = literal.parent as? ParameterList ?: return null
        val call = parameterList.parent as? FunctionReference ?: return null
        if (parameterList.parameters.firstOrNull() !== literal) return null
        return type(call)
    }
}
