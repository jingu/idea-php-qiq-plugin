package io.github.jingu.idea_qiq_plugin.inspection

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the pure argument-list scanner that decides whether a helper
 * call's positional argument count can be trusted. Named arguments and spread
 * (`...`) unpacking make the count unreliable, so the inspection skips them.
 *
 * The PSI text may or may not carry the wrapping parentheses, so both shapes
 * are covered.
 */
class QiqHelperArgumentsInspectionTest {

    private fun uncountable(text: String) =
        QiqHelperArgumentsInspection.hasUncountableArgument(text)

    @Test
    fun plainPositionalArgsAreCountable() {
        assertFalse(uncountable("(\$this, '/partial/ad/targeting', ['adLevel' => 'article'])"))
        assertFalse(uncountable("\$this, '/p'")) // unwrapped shape
        assertFalse(uncountable("()"))
    }

    @Test
    fun namedArgumentIsUncountable() {
        assertTrue(uncountable("(templatePath: '/p')"))
        assertTrue(uncountable("\$this, name: \$x")) // unwrapped shape
    }

    @Test
    fun spreadArgumentIsUncountable() {
        assertTrue(uncountable("(...\$args)"))
        assertTrue(uncountable("(\$this, ...\$rest)"))
    }

    @Test
    fun colonInsideStringIsIgnored() {
        assertFalse(uncountable("('https://example.com/path')"))
    }

    @Test
    fun staticAccessColonsAreIgnored() {
        assertFalse(uncountable("(\$this, Foo::BAR)"))
    }

    @Test
    fun spreadNestedInArrayIsNotArgumentLevel() {
        // `[...$a]` is array unpacking inside one argument, not a call spread.
        assertFalse(uncountable("([...\$a], \$b)"))
    }
}
