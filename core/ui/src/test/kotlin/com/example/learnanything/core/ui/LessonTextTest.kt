package com.example.learnanything.core.ui

import com.example.learnanything.core.ui.text.LessonText
import com.example.learnanything.core.ui.text.LessonText.Block
import com.example.learnanything.core.ui.text.LessonText.Span
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LessonTextTest {
    @Test fun inlineBoldAndCode() {
        assertThat(LessonText.parseInline("Use **JOIN** with `ON id`."))
            .containsExactly(Span.Plain("Use "), Span.Bold("JOIN"), Span.Plain(" with "), Span.Code("ON id"), Span.Plain(".")).inOrder()
    }

    @Test fun unclosedMarkersStayPlain() {
        assertThat(LessonText.parseInline("2 ** 3 and a ` tick")).containsExactly(Span.Plain("2 ** 3 and a ` tick"))
    }

    @Test fun blocks() {
        val text = """
            First line
            continues here.

            - one
            - **two**
            ```
            SELECT *
              FROM t;
            ```
            Last.
        """.trimIndent()
        val blocks = LessonText.parse(text)
        assertThat(blocks).hasSize(4)
        assertThat(blocks[0]).isEqualTo(Block.Paragraph(listOf(Span.Plain("First line continues here."))))
        assertThat((blocks[1] as Block.Bullets).items).hasSize(2)
        assertThat(blocks[2]).isEqualTo(Block.Code("SELECT *\n  FROM t;"))
        assertThat(blocks[3]).isEqualTo(Block.Paragraph(listOf(Span.Plain("Last."))))
    }

    @Test fun htmlAndLinksAreJustText() {
        val blocks = LessonText.parse("<b>hi</b> [x](y)")
        assertThat(blocks).containsExactly(Block.Paragraph(listOf(Span.Plain("<b>hi</b> [x](y)"))))
    }

    @Test fun unterminatedCodeBlockRunsToTheEnd() {
        assertThat(LessonText.parse("```\ncode")).containsExactly(Block.Code("code"))
    }
}
