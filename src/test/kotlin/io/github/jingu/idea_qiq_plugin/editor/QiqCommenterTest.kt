package io.github.jingu.idea_qiq_plugin.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QiqCommenterTest {

    private val commenter = QiqCommenter()

    @Test
    fun htmlLineWithQiqExpressionUsesHtmlCommentWrapper() {
        val original = "<h1>{{h \$article->title }}</h1>"
        val commented = requireNotNull(commenter.commentLineText(original))

        assertEquals("<!-- <h1>{{//h \$article->title }}</h1> -->", commented)

        val restored = requireNotNull(commenter.uncommentLineText(commented))

        assertEquals(original, restored)

        val doubleCommentAttempt = commenter.commentLineText(commented)

        assertNull(doubleCommentAttempt)
    }

    @Test
    fun plainTemplateDataFallsBackToQiqLineComment() {
        val original = "Hello Qiq"
        val commented = requireNotNull(commenter.commentLineText(original))

        assertEquals("{{ // Hello Qiq }}", commented)

        val restored = requireNotNull(commenter.uncommentLineText(commented))

        assertEquals(original, restored)
    }
}
