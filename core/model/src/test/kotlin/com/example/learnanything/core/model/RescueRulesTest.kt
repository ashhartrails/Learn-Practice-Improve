package com.example.learnanything.core.model

import com.example.learnanything.core.model.practical.RubricCriterion
import com.example.learnanything.core.model.question.QuestionDto
import com.example.learnanything.core.model.rescue.Blank
import com.example.learnanything.core.model.rescue.RescueLadder
import com.example.learnanything.core.model.rescue.RescueResponse
import com.example.learnanything.core.model.rescue.RescueRules
import com.example.learnanything.core.model.rescue.RescueRung
import com.example.learnanything.core.model.resources.PhetCatalog
import com.example.learnanything.core.model.resources.ResourcesResponse
import com.example.learnanything.core.model.resources.ResourcesRules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RescueRulesTest {
    private val q = QuestionDto("single_choice", "Which one?", listOf("A", "B", "C"), listOf(0), explanation = "A it is.")

    @Test fun defaultLadderOrderAndNext() {
        val order = RescueLadder.order(emptyMap())
        assertThat(order).isEqualTo(RescueRung.entries)
        assertThat(RescueLadder.next(order, RescueRung.SIMPLER)).isEqualTo(RescueRung.ANALOGY)
        assertThat(RescueLadder.next(order, RescueRung.PREREQUISITE)).isNull()
    }

    @Test fun rungThatHelpedMostStartsTheLadder() {
        val order = RescueLadder.order(mapOf(RescueRung.VIDEO to 3, RescueRung.ANALOGY to 1))
        assertThat(order.first()).isEqualTo(RescueRung.VIDEO)
        assertThat(order).hasSize(RescueRung.entries.size)
        assertThat(order.drop(1)).isEqualTo(RescueRung.entries - RescueRung.VIDEO)
    }

    @Test fun blanksMustMatchMarkersAndAreCheckedByCode() {
        val r = RescueResponse("Gaps", "Start from [1] then join on [2].", blanks = listOf(Blank(listOf("orders")), Blank(listOf("customer_id", "customer id"))))
        assertThat(RescueRules.check(r, RescueRung.BLANKS)).isEmpty()
        assertThat(RescueRules.check(r.copy(body = "Only [1] here."), RescueRung.BLANKS)).isNotEmpty()
        assertThat(RescueRules.checkBlanks(r, listOf(" Orders ", "customer id"))).containsExactly(true, true).inOrder()
        assertThat(RescueRules.checkBlanks(r, listOf("customers", ""))).containsExactly(false, false).inOrder()
    }

    @Test fun eachRungNeedsItsOwnParts() {
        val text = (1..45).joinToString(" ") { "w$it" }
        assertThat(RescueRules.check(RescueResponse("T", text), RescueRung.SIMPLER)).isEmpty()
        assertThat(RescueRules.check(RescueResponse("T", "Too short."), RescueRung.ANALOGY)).isNotEmpty()
        assertThat(RescueRules.check(RescueResponse("T", "Intro.", questions = listOf(q)), RescueRung.SOCRATIC)).isNotEmpty()
        assertThat(RescueRules.check(RescueResponse("T", "Intro.", questions = listOf(q, q.copy(prompt = "Next?"))), RescueRung.SOCRATIC)).isEmpty()
        val rubric = (1..3).map { RubricCriterion("Criterion $it", 2) }
        assertThat(RescueRules.check(RescueResponse("T", "Explain it.", rubric = rubric), RescueRung.TEACH_BACK)).isEmpty()
        assertThat(RescueRules.check(RescueResponse("T", "$text see www.example.com"), RescueRung.SIMPLER)).isNotEmpty()
    }

    @Test fun resourcesOnlyAcceptCatalogKeys() {
        val ok = ResourcesResponse(listOf("states of matter explained"), "phet:states-of-matter", "")
        assertThat(ResourcesRules.check(ok)).isEmpty()
        assertThat(ResourcesRules.check(ok.copy(phetKey = ""))).isEmpty()
        assertThat(ResourcesRules.check(ok.copy(phetKey = "phet:made-up-sim")).joinToString()).contains("not in the catalog")
        assertThat(ResourcesRules.check(ok.copy(searchQueries = listOf("https://youtube.com/watch?v=x")))).isNotEmpty()
    }

    @Test fun catalogBuildsUrlsItself() {
        val sim = PhetCatalog.find("phet:ohms-law")!!
        assertThat(sim.url).isEqualTo("https://phet.colorado.edu/sims/html/ohms-law/latest/ohms-law_all.html")
        assertThat(PhetCatalog.all.map { it.key }.toSet()).hasSize(PhetCatalog.all.size)
    }
}
