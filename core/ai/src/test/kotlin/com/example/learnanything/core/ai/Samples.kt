package com.example.learnanything.core.ai

object Samples {
    val VALID_OUTLINE = """
        {
          "title": "SQL joins from scratch",
          "summary": "Combine tables confidently with inner, outer and self joins.",
          "chapters": [
            {
              "title": "Relational basics",
              "goal": "Explain keys and write a two-table inner join.",
              "topics": [
                { "title": "Tables and keys", "objectives": ["Identify primary and foreign keys"], "prerequisites": [], "estimatedMinutes": 10 },
                { "title": "Inner joins", "objectives": ["Write an INNER JOIN"], "prerequisites": ["Tables and keys"], "estimatedMinutes": 15 }
              ]
            }
          ]
        }
    """.trimIndent()

    /** Schema-valid but breaks a code rule (duplicate topic titles). */
    val DUPLICATE_TOPICS = """
        {
          "title": "SQL joins",
          "summary": "Combine tables confidently with joins.",
          "chapters": [
            { "title": "Chapter one", "goal": "Write a simple join query.", "topics": [
              { "title": "Joins", "objectives": ["Write a join"], "prerequisites": [], "estimatedMinutes": 10 },
              { "title": "joins", "objectives": ["Write another join"], "prerequisites": [], "estimatedMinutes": 10 }
            ] }
          ]
        }
    """.trimIndent()
}
