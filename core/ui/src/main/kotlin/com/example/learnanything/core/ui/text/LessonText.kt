package com.example.learnanything.core.ui.text

/**
 * The only formatting lesson text may use: paragraphs, "- " bullet lists, ``` code blocks,
 * **bold** and `inline code`. Everything else is shown as plain text. There's no HTML,
 * no links and no WebView, so model output can't inject anything.
 */
object LessonText {
    sealed interface Block {
        data class Paragraph(val spans: List<Span>) : Block
        data class Bullets(val items: List<List<Span>>) : Block
        data class Code(val code: String) : Block
    }

    sealed interface Span {
        val text: String
        data class Plain(override val text: String) : Span
        data class Bold(override val text: String) : Span
        data class Code(override val text: String) : Span
    }

    fun parse(source: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        val lines = source.replace("\r\n", "\n").lines()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) blocks += Block.Paragraph(parseInline(paragraph.joinToString(" ")))
            paragraph.clear()
        }
        fun flushBullets() {
            if (bullets.isNotEmpty()) blocks += Block.Bullets(bullets.map(::parseInline))
            bullets.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flushParagraph(); flushBullets()
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) code += lines[i++]
                    blocks += Block.Code(code.joinToString("\n").trimEnd())
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    bullets += trimmed.drop(2).trim()
                }
                trimmed.isEmpty() -> { flushParagraph(); flushBullets() }
                else -> { flushBullets(); paragraph += trimmed }
            }
            i++
        }
        flushParagraph(); flushBullets()
        return blocks
    }

    fun parseInline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = StringBuilder()
        var i = 0
        fun flush() { if (plain.isNotEmpty()) spans += Span.Plain(plain.toString()); plain.clear() }
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) { flush(); spans += Span.Bold(text.substring(i + 2, end)); i = end + 2; continue }
            }
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) { flush(); spans += Span.Code(text.substring(i + 1, end)); i = end + 1; continue }
            }
            plain.append(text[i]); i++
        }
        flush()
        return spans
    }
}
