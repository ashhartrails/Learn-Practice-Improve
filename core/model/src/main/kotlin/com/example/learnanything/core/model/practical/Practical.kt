package com.example.learnanything.core.model.practical

import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.TextSafety
import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import com.example.learnanything.core.model.clarify.Clarification
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PracticalKind(val label: String, val exact: Boolean) {
    @SerialName("python") PYTHON("Python", true),
    @SerialName("javascript") JAVASCRIPT("JavaScript", true),
    @SerialName("sql") SQL("SQL", true),
    @SerialName("numeric") NUMERIC("Calculation", true),
    @SerialName("written") WRITTEN("Written answer", false),
    ;

    val runsCode: Boolean get() = this == PYTHON || this == JAVASCRIPT || this == SQL

    companion object {
        fun fromWire(value: String): PracticalKind? = entries.firstOrNull { it.wire == value }
    }

    /** The JSON name, e.g. "python". */
    val wire: String get() = name.lowercase()
}

@Serializable
data class CodeTest(val name: String, val code: String)

@Serializable
data class RubricCriterion(val criterion: String, val points: Int)

/**
 * A chapter practical exactly as the AI returns it. Fields that don't apply to [kind] are empty.
 * Code kinds carry hidden [tests] and a [referenceSolution] that must pass them.
 */
@Serializable
data class PracticalResponse(
    val kind: PracticalKind,
    val title: String,
    val task: String,
    val starterCode: String = "",
    val tests: List<CodeTest> = emptyList(),
    val referenceSolution: String = "",
    /** SQL only: CREATE TABLE and INSERT statements for the sample database. */
    val sqlSetup: String = "",
    /** SQL only: whether row order matters. */
    val ordered: Boolean = false,
    /** Numeric only. */
    val number: Double? = null,
    val tolerance: Double? = null,
    val unit: String? = null,
    /** Written only. */
    val rubric: List<RubricCriterion> = emptyList(),
    val modelAnswer: String = "",
)

object PracticalRules {
    const val WRITTEN_PASS_MARK = 0.7

    fun check(p: PracticalResponse, requested: PracticalKind?): List<String> {
        val e = mutableListOf<String>()
        if (requested != null && p.kind != requested) e += "kind must be \"${requested.wire}\""
        when (p.kind) {
            PracticalKind.PYTHON, PracticalKind.JAVASCRIPT -> {
                if (p.tests.size !in 3..8) e += "code tasks need 3 to 8 tests"
                if (p.referenceSolution.isBlank()) e += "referenceSolution is required for code tasks"
                if (p.starterCode.isBlank()) e += "starterCode is required for code tasks"
                p.tests.forEachIndexed { i, t ->
                    if (t.code.isBlank()) e += "tests[$i].code is empty"
                    if (p.kind == PracticalKind.PYTHON && "assert" !in t.code) e += "tests[$i].code must use assert"
                    if (p.kind == PracticalKind.JAVASCRIPT && "assert" !in t.code) e += "tests[$i].code must call assert or assertEqual"
                }
                val names = p.tests.map { it.name.trim().lowercase() }
                if (names.toSet().size != names.size) e += "test names must be different"
            }
            PracticalKind.SQL -> {
                if (!p.sqlSetup.contains("create table", ignoreCase = true)) e += "sqlSetup must create the tables"
                if (!p.sqlSetup.contains("insert", ignoreCase = true)) e += "sqlSetup must insert sample rows"
                val ref = p.referenceSolution.trim().lowercase()
                if (!(ref.startsWith("select") || ref.startsWith("with"))) e += "referenceSolution must be a single SELECT query"
            }
            PracticalKind.NUMERIC -> {
                if (p.number == null) e += "number is required for numeric tasks"
                if (p.tolerance == null || p.tolerance < 0) e += "tolerance must be 0 or more for numeric tasks"
            }
            PracticalKind.WRITTEN -> {
                if (p.rubric.size !in 3..6) e += "written tasks need 3 to 6 rubric criteria"
                p.rubric.forEachIndexed { i, c -> if (c.points !in 1..5) e += "rubric[$i].points must be 1 to 5" }
                if (p.modelAnswer.isBlank()) e += "modelAnswer is required for written tasks"
            }
        }
        e += TextSafety.urlProblems(
            listOf("title" to p.title, "task" to p.task, "modelAnswer" to p.modelAnswer) +
                p.rubric.mapIndexed { i, c -> "rubric[$i].criterion" to c.criterion },
        )
        return e
    }
}

data class PracticalContext(
    val subjectTitle: String,
    val subjectGoal: String,
    val level: Level,
    val clarifications: List<Clarification>,
    val chapterTitle: String,
    val chapterGoal: String,
    val topics: List<String>,
    /** Null lets the AI choose. */
    val requestedKind: PracticalKind?,
    /** Bumped for each new task so the cache doesn't return the same one. */
    val variant: Int,
    /** Why the previous task was thrown away, if it was. */
    val previousProblem: String? = null,
)

object PracticalPrompt {
    const val VERSION = 1

    private val SYSTEM = """
        You design one hands-on practical that proves the learner met a chapter's goal.
        It must take 10 to 25 minutes and be checkable. Choose "kind":
        - python or javascript: when the course is about programming in that language, or the
          skill is best shown by writing a small function. The learner edits starterCode
          (a function signature with a body that does not solve the task). Write 3 to 8 tests;
          each is a short snippet run after the learner's code. Python tests use assert, for
          example: assert total([1, 2]) == 3. JavaScript tests call assertEqual(actual, expected)
          or assert(condition, message). Tests must not print or read input. referenceSolution
          is a complete solution that passes every test. Use only the standard library.
        - sql: for databases and data querying. sqlSetup creates 1 to 3 small tables (SQLite
          syntax) and inserts 5 to 15 rows. referenceSolution is one SELECT query that answers
          the task. Set "ordered" to true only if the task asks for a specific order.
          starterCode may hold a hint query such as SELECT * FROM table_name.
        - numeric: for maths, physics, finance and other calculations. Give the answer in
          "number", an absolute "tolerance" and "unit" if any.
        - written: only when nothing above can check the skill. Give 3 to 6 rubric criteria
          (points 1 to 5), each checkable from the learner's text, and a modelAnswer.
        "task" explains what to do in plain words; it may use **bold**, `inline code`, fenced
        code blocks and "- " lists. Never reveal the solution or the tests in the task.
        Use empty strings, empty arrays or null for fields that don't apply. Never include URLs.
    """.trimIndent()

    fun build(ctx: PracticalContext): PromptSpec = PromptSpec(
        callType = CallType.PRACTICAL,
        version = VERSION,
        system = SYSTEM,
        user = buildString {
            appendLine("Course: ${ctx.subjectTitle}")
            appendLine("Learner goal: ${ctx.subjectGoal}")
            appendLine("Learner level: ${ctx.level.label}")
            ctx.clarifications.forEach { appendLine("Learner said — ${it.question}: ${it.answer}") }
            appendLine("Chapter: ${ctx.chapterTitle} (goal: ${ctx.chapterGoal})")
            appendLine("Topics covered: ${ctx.topics.joinToString("; ")}")
            ctx.requestedKind?.let { appendLine("The learner asked for kind \"${it.wire}\".") }
            ctx.previousProblem?.let { appendLine("A previous task was rejected because: $it. Avoid that.") }
            append("Practical version ${ctx.variant}. Write the practical.")
        },
        schema = AiSchema.PRACTICAL_V1,
    )
}

/** One hidden test's outcome after running the learner's code. */
@Serializable
data class TestOutcome(val name: String, val passed: Boolean, val message: String = "")

/** What a code runner reports. [error] is set when nothing could run (syntax error, timeout). */
@Serializable
data class RunReport(
    val tests: List<TestOutcome> = emptyList(),
    val error: String? = null,
    val output: String = "",
    val timedOut: Boolean = false,
) {
    /** Principle 1: code tasks pass only when every hidden test passes. */
    val allPassed: Boolean get() = error == null && tests.isNotEmpty() && tests.all { it.passed }
}

/** Runs learner code against hidden tests. The app's version uses a sandboxed WebView. */
interface CodeRunner {
    suspend fun run(practical: PracticalResponse, code: String): RunReport
}

/**
 * Generate, then validate: a code task is kept only if its reference solution passes its
 * own tests and the untouched starter code does not.
 */
object PracticalVerifier {
    sealed interface Verdict {
        data object Ok : Verdict
        data class Rejected(val reason: String) : Verdict
    }

    suspend fun verify(p: PracticalResponse, runner: CodeRunner): Verdict {
        if (!p.kind.runsCode) return Verdict.Ok
        val reference = runner.run(p, p.referenceSolution)
        if (!reference.allPassed) {
            val why = reference.error ?: reference.tests.firstOrNull { !it.passed }?.let { "test \"${it.name}\" failed: ${it.message}" }
            return Verdict.Rejected("the reference solution did not pass its own tests ($why)")
        }
        if (p.starterCode.isNotBlank()) {
            val starter = runner.run(p, p.starterCode)
            if (starter.allPassed) return Verdict.Rejected("the starter code already passes every test, so the tests check nothing")
        }
        return Verdict.Ok
    }
}
