package com.example.learnanything.core.model

import com.example.learnanything.core.model.practical.CodeRunner
import com.example.learnanything.core.model.practical.CodeTest
import com.example.learnanything.core.model.practical.CriterionGrade
import com.example.learnanything.core.model.practical.GradeResponse
import com.example.learnanything.core.model.practical.GradeRules
import com.example.learnanything.core.model.practical.PracticalKind
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.practical.PracticalRules
import com.example.learnanything.core.model.practical.PracticalVerifier
import com.example.learnanything.core.model.practical.RubricCriterion
import com.example.learnanything.core.model.practical.RunReport
import com.example.learnanything.core.model.practical.TestOutcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PracticalRulesTest {
    private val tests = listOf(CodeTest("a", "assert f(1) == 1"), CodeTest("b", "assert f(2) == 2"), CodeTest("c", "assert f(3) == 3"))
    private val python = PracticalResponse(PracticalKind.PYTHON, "Identity", "Write f(x) that returns x.", "def f(x):\n    pass", tests, "def f(x):\n    return x")

    @Test fun validCodeTaskHasNoProblems() {
        assertThat(PracticalRules.check(python, null)).isEmpty()
    }

    @Test fun codeTaskNeedsEnoughTestsAndAReference() {
        val e = PracticalRules.check(python.copy(tests = tests.take(2), referenceSolution = ""), null)
        assertThat(e).contains("code tasks need 3 to 8 tests")
        assertThat(e).contains("referenceSolution is required for code tasks")
    }

    @Test fun requestedKindIsEnforced() {
        assertThat(PracticalRules.check(python, PracticalKind.SQL)).contains("kind must be \"sql\"")
    }

    @Test fun sqlNeedsTablesRowsAndASelect() {
        val sql = PracticalResponse(PracticalKind.SQL, "Totals", "Sum the orders per customer.", sqlSetup = "CREATE TABLE t(a INT);", referenceSolution = "DELETE FROM t")
        assertThat(PracticalRules.check(sql, null)).containsAtLeast("sqlSetup must insert sample rows", "referenceSolution must be a single SELECT query")
    }

    @Test fun urlsInTheTaskAreRejected() {
        assertThat(PracticalRules.check(python.copy(task = "Read https://example.com first, then write f."), null)).isNotEmpty()
    }

    @Test fun verifierRejectsFailingReferenceAndPassingStarter() = runTest {
        val alwaysPass = object : CodeRunner {
            override suspend fun run(practical: PracticalResponse, code: String) = RunReport(listOf(TestOutcome("a", true)))
        }
        val alwaysFail = object : CodeRunner {
            override suspend fun run(practical: PracticalResponse, code: String) = RunReport(listOf(TestOutcome("a", false, "boom")))
        }
        assertThat(PracticalVerifier.verify(python, alwaysFail)).isInstanceOf(PracticalVerifier.Verdict.Rejected::class.java)
        assertThat((PracticalVerifier.verify(python, alwaysPass) as PracticalVerifier.Verdict.Rejected).reason).contains("starter code")
    }

    private val rubric = listOf(RubricCriterion("Defines it", 2), RubricCriterion("Gives an example", 3))

    @Test fun gradeQuotesMustComeFromTheAnswer() {
        val answer = "A join combines   tables. For example orders and customers."
        val good = GradeResponse(listOf(CriterionGrade(0, true, "a join combines tables", "ok"), CriterionGrade(1, true, "For example ... customers", "ok")), "Nice.")
        assertThat(GradeRules.check(good, rubric, answer)).isEmpty()
        val made = good.copy(criteria = listOf(good.criteria[0], CriterionGrade(1, true, "joins are fast", "ok")))
        assertThat(GradeRules.check(made, rubric, answer)).hasSize(1)
        val missing = good.copy(criteria = good.criteria.take(1))
        assertThat(GradeRules.check(missing, rubric, answer)).isNotEmpty()
    }

    @Test fun rubricScoreUsesPoints() {
        val g = GradeResponse(listOf(CriterionGrade(0, false, "", "no"), CriterionGrade(1, true, "x", "ok")), "s")
        val score = GradeRules.score(g, rubric)
        assertThat(score.earned).isEqualTo(3)
        assertThat(score.passed).isFalse() // 60% < 70%
    }
}
