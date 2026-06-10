package io.github.jingu.idea_qiq_plugin.util

import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.Variable
import java.util.Locale

/**
 * Recognizes the Qiq section calls — `setSection('name')` (the definition),
 * `getSection('name')` / `hasSection('name')` (the readers) — bare or
 * `$this->`-qualified. Shared by the name completion, navigation, and the
 * undefined-name inspection so they agree on what counts as a section call.
 *
 * Only sections are handled here: blocks read with an argument-less `getBlock()`,
 * so there is no by-name block reader to navigate. The matching definitions are
 * parsed by [io.github.jingu.idea_qiq_plugin.block.QiqSectionModel].
 */
object QiqSectionCall {

    /** Readers offered completion and Go to Declaration to a definition. */
    private val READER_HEADS = setOf("getsection", "hassection")

    /** Reader the undefined-name inspection flags. `hasSection` is excluded: it
     *  legitimately tests a possibly-absent name, so a missing one is not a bug. */
    private const val INSPECTABLE_READER = "getsection"

    private const val WRITER_HEAD = "setsection"

    /** True if [literal] is the name argument of a `getSection`/`hasSection` reader. */
    fun isReaderArg(literal: StringLiteralExpression): Boolean = headOfArg(literal) in READER_HEADS

    /** True if [literal] is the name argument of a `setSection` definition. */
    fun isWriterArg(literal: StringLiteralExpression): Boolean = headOfArg(literal) == WRITER_HEAD

    /** True if [call] is a `getSection` reader (for the inspection; `hasSection` excluded). */
    fun isInspectableReader(call: FunctionReference): Boolean = headOfCall(call) == INSPECTABLE_READER

    /** The lowercased call name when [literal] is its first argument and the
     *  receiver is bare or `$this`, else null. */
    private fun headOfArg(literal: StringLiteralExpression): String? {
        val parameterList = literal.parent as? ParameterList ?: return null
        val call = parameterList.parent as? FunctionReference ?: return null
        if (parameterList.parameters.firstOrNull() !== literal) return null
        return headOfCall(call)
    }

    /** The lowercased call name, or null when the receiver is an object other than `$this`. */
    private fun headOfCall(call: FunctionReference): String? {
        val name = call.name?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        val receiver = (call as? MethodReference)?.classReference
        if (receiver is Variable && receiver.name != "this") return null
        return name
    }
}
