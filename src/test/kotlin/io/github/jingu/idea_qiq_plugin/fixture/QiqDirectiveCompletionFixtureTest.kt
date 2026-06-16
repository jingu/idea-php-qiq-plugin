package io.github.jingu.idea_qiq_plugin.fixture

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration coverage for QiqDirectiveCompletionContributor (#34 / #32): drives
 * real completion (dummy-identifier + PHP injection) at and away from the directive
 * head, complementing the pure head-gate unit tests.
 */
class QiqDirectiveCompletionFixtureTest : BasePlatformTestCase() {

    private fun completionAt(text: String): List<String> {
        myFixture.configureByText("page.qiq", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun testDirectiveHeadOffersControlAndApiKeywords() {
        val items = completionAt("{{ <caret> }}")
        assertContainsElements(items, "if", "foreach", "for", "setSection", "setLayout")
    }

    fun testWhileTypingFiltersToMatchingDirective() {
        val items = completionAt("{{ fore<caret> }}")
        assertContainsElements(items, "foreach")
    }

    fun testOutputTagDoesNotOfferControlDirectives() {
        // Inside `{{= }}` the head is an expression, so the control keywords that
        // only make sense as a directive head must not be offered.
        val items = completionAt("{{= <caret> }}")
        assertDoesntContain(items, "endif", "endforeach")
    }
}
