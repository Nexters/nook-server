package org.every.nook.api.application.processing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParsingPolicyContractTest {
    @Test
    fun `executable rule attaches its stable id to the evaluation`() {
        val rule = ParsingRule<Int>(ruleDefinition()) { candidateCount ->
            ParsingRuleDecision(
                outcome = if (candidateCount == 1) ParsingRuleOutcome.PASSED else ParsingRuleOutcome.FAILED,
                facts = listOf(
                    ParsingFact("candidate-count", "후보 수", ParsingFactValue.Count(candidateCount)),
                ),
                reason = "후보 수 판정",
                nextStepId = ParsingStepId("place.save"),
            )
        }

        val evaluation = rule.evaluate(1)

        assertEquals(ParsingRuleId("place.candidate.unique-match"), evaluation.ruleId)
        assertEquals(ParsingRuleOutcome.PASSED, evaluation.outcome)
        assertEquals(ParsingFactValue.Count(1), evaluation.facts.single().value)
        assertEquals(ParsingStepId("place.save"), evaluation.nextStepId)
    }

    @Test
    fun `catalog indexes workflow steps and rules`() {
        val workflow = workflow()
        val catalog = ParsingRuleCatalog(listOf(workflow), listOf(ruleDefinition()))

        assertEquals(workflow, catalog.workflow(ParsingWorkflowId("place-parsing")))
        assertEquals(workflow.steps.first(), catalog.step(ParsingStepId("place.candidate-resolution")))
        assertEquals(ruleDefinition(), catalog.rule(ParsingRuleId("place.candidate.unique-match")))
    }

    @Test
    fun `catalog rejects duplicate rule definitions`() {
        val duplicateRule = ruleDefinition()

        assertFailsWith<IllegalArgumentException> {
            ParsingRuleCatalog(listOf(workflow()), listOf(duplicateRule, duplicateRule))
        }
    }

    @Test
    fun `catalog rejects duplicate workflow ids`() {
        val workflow = workflow()

        assertFailsWith<IllegalArgumentException> {
            ParsingRuleCatalog(listOf(workflow, workflow), listOf(ruleDefinition()))
        }
    }

    @Test
    fun `catalog rejects duplicate step ids across workflows`() {
        val duplicateStepId = ParsingStepId("place.save")
        val anotherWorkflow = ParsingWorkflowDefinition(
            id = ParsingWorkflowId("content-parsing"),
            title = "콘텐츠 파싱",
            description = "콘텐츠를 파싱합니다.",
            steps = listOf(
                ParsingStepDefinition(
                    id = duplicateStepId,
                    title = "콘텐츠 저장",
                    description = "콘텐츠를 저장합니다.",
                    inputs = emptyList(),
                    outputs = emptyList(),
                    ruleIds = emptyList(),
                ),
            ),
            edges = emptyList(),
        )

        assertFailsWith<IllegalArgumentException> {
            ParsingRuleCatalog(listOf(workflow(), anotherWorkflow), listOf(ruleDefinition()))
        }
    }

    @Test
    fun `stable ids reject display text and uppercase characters`() {
        assertFailsWith<IllegalArgumentException> { ParsingRuleId("Place Candidate 선택") }
    }

    @Test
    fun `workflow rejects an edge referencing an unknown step`() {
        assertFailsWith<IllegalArgumentException> {
            workflow().copy(
                edges = listOf(
                    ParsingWorkflowEdgeDefinition(
                        id = "place.resolve-to-unknown",
                        source = ParsingStepId("place.candidate-resolution"),
                        target = ParsingStepId("place.unknown"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `policy evaluation rejects evaluating the same rule twice`() {
        val evaluation = ParsingRuleEvaluation(
            ruleId = ParsingRuleId("place.candidate.unique-match"),
            outcome = ParsingRuleOutcome.PASSED,
            facts = emptyList(),
            reason = null,
            nextStepId = null,
        )

        assertFailsWith<IllegalArgumentException> {
            ParsingPolicyEvaluation(Unit, listOf(evaluation, evaluation))
        }
    }

    @Test
    fun `policy rejects an evaluation for a rule not declared by its step`() {
        val policy = object : ParsingPolicy<Unit, Unit>(
            step(ParsingStepId("place.candidate-resolution"), ruleDefinition()),
        ) {
            override fun evaluatePolicy(context: Unit) = ParsingPolicyEvaluation(
                Unit,
                listOf(
                    ParsingRuleEvaluation(
                        ruleId = ParsingRuleId("place.candidate.unknown"),
                        outcome = ParsingRuleOutcome.SKIPPED,
                        facts = emptyList(),
                        reason = null,
                        nextStepId = null,
                    ),
                ),
            )
        }

        assertFailsWith<IllegalArgumentException> { policy.evaluate(Unit) }
    }

    @Test
    fun `catalog rejects a rule effect referencing another workflow`() {
        val foreignStep = ParsingStepId("content.save")
        val crossWorkflowRule = ruleDefinition().copy(
            onPassed = ParsingRuleEffect("다른 workflow로 이동", foreignStep),
        )
        val placeWorkflow = workflow().copy(
            steps = listOf(step(ParsingStepId("place.candidate-resolution"), crossWorkflowRule), saveStep()),
        )
        val contentWorkflow = ParsingWorkflowDefinition(
            id = ParsingWorkflowId("content-parsing"),
            title = "콘텐츠 파싱",
            description = "콘텐츠를 파싱합니다.",
            steps = listOf(
                ParsingStepDefinition(
                    id = foreignStep,
                    title = "콘텐츠 저장",
                    description = "콘텐츠를 저장합니다.",
                    inputs = emptyList(),
                    outputs = emptyList(),
                    ruleIds = emptyList(),
                ),
            ),
            edges = emptyList(),
        )

        assertFailsWith<IllegalArgumentException> {
            ParsingRuleCatalog(listOf(placeWorkflow, contentWorkflow), listOf(crossWorkflowRule))
        }
    }

    private fun workflow() = ParsingWorkflowDefinition(
        id = ParsingWorkflowId("place-parsing"),
        title = "장소 파싱",
        description = "장소 단서를 실제 장소로 확정합니다.",
        steps = listOf(
            step(ParsingStepId("place.candidate-resolution"), ruleDefinition()),
            saveStep(),
        ),
        edges = listOf(
            ParsingWorkflowEdgeDefinition(
                id = "place.resolve-to-save",
                source = ParsingStepId("place.candidate-resolution"),
                target = ParsingStepId("place.save"),
                label = "후보 확정",
            ),
        ),
    )

    private fun step(id: ParsingStepId, rule: ParsingRuleDefinition) = ParsingStepDefinition(
        id = id,
        title = "장소 후보 판정",
        description = "검색 후보를 판정합니다.",
        inputs = listOf(ParsingFieldDefinition("candidate-count", "후보 수")),
        outputs = listOf(ParsingFieldDefinition("selected-place", "선택 장소")),
        ruleIds = listOf(rule.id),
    )

    private fun saveStep() = ParsingStepDefinition(
        id = ParsingStepId("place.save"),
        title = "장소 저장",
        description = "확정 장소를 저장합니다.",
        inputs = listOf(ParsingFieldDefinition("selected-place", "선택 장소")),
        outputs = emptyList(),
        ruleIds = emptyList(),
    )

    private fun ruleDefinition() = ParsingRuleDefinition(
        id = ParsingRuleId("place.candidate.unique-match"),
        title = "유일 후보 확인",
        description = "안전 조건을 만족하는 후보가 하나인지 확인합니다.",
        condition = "candidateCount == 1",
        expression = "candidateCount == 1",
        source = "CandidateResolutionPolicy",
        parameters = mapOf("required-count" to ParsingFactValue.Count(1)),
        onPassed = ParsingRuleEffect("장소 확정", ParsingStepId("place.save")),
        onFailed = ParsingRuleEffect("다음 후보 판정"),
    )
}
