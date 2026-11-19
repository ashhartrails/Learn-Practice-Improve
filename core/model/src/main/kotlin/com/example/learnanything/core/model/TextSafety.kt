package com.example.learnanything.core.model

/** Principle 3: the AI never supplies URLs. Any text that contains one is rejected. */
object TextSafety {
    private val URL = Regex("""(?i)\b(?:https?://|www\.)\S+|\b[a-z0-9-]+\.(?:com|org|net|io|edu|gov|dev|app|co)(?:/\S*)?\b""")

    fun containsUrl(text: String): Boolean = URL.containsMatchIn(text)

    /** Returns a problem for each labelled text that contains a URL. */
    fun urlProblems(texts: List<Pair<String, String>>): List<String> =
        texts.filter { containsUrl(it.second) }.map { "${it.first} contains a URL; never include links or web addresses" }
}
