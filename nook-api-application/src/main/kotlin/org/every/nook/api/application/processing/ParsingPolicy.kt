package org.every.nook.api.application.processing

data class ParsingRuleDefinition(
    val id: ParsingRuleId,
    val title: String,
    val description: String,
    val condition: String,
    val expression: String? = null,
    val source: String,
    val section: String = "판정 규칙",
    val parameters: Map<String, ParsingFactValue> = emptyMap(),
    val onPassed: ParsingRuleEffect,
    val onFailed: ParsingRuleEffect,
) {
    init {
        require(title.isNotBlank()) { "Parsing rule title must not be blank" }
        require(description.isNotBlank()) { "Parsing rule description must not be blank" }
        require(condition.isNotBlank()) { "Parsing rule condition must not be blank" }
        require(expression == null || expression.isNotBlank()) { "Parsing rule expression must not be blank" }
        require(source.isNotBlank()) { "Parsing rule source must not be blank" }
        require(section.isNotBlank()) { "Parsing rule section must not be blank" }
        require(parameters.keys.all(String::isParsingDefinitionId)) { "Parsing rule parameter keys are invalid" }
    }
}

data class ParsingRuleEffect(val description: String, val nextStepId: ParsingStepId? = null) {
    init {
        require(description.isNotBlank()) { "Parsing rule effect description must not be blank" }
    }
}

class ParsingRule<C>(val definition: ParsingRuleDefinition, private val evaluator: (C) -> ParsingRuleDecision) {
    fun evaluate(context: C): ParsingRuleEvaluation {
        val decision = evaluator(context)
        return ParsingRuleEvaluation(
            ruleId = definition.id,
            outcome = decision.outcome,
            facts = decision.facts,
            reason = decision.reason,
            nextStepId = decision.nextStepId,
        )
    }
}

abstract class ParsingPolicy<C, R>(val step: ParsingStepDefinition) {
    fun evaluate(context: C): ParsingPolicyEvaluation<R> = evaluatePolicy(context).also { evaluation ->
        val definedRuleIds = step.ruleIds.toSet()
        require(evaluation.ruleEvaluations.all { it.ruleId in definedRuleIds }) {
            "Parsing policy evaluation must reference rules declared by step ${step.id}"
        }
    }

    protected abstract fun evaluatePolicy(context: C): ParsingPolicyEvaluation<R>
}

data class ParsingPolicyEvaluation<out R>(val result: R, val ruleEvaluations: List<ParsingRuleEvaluation>) {
    init {
        require(ruleEvaluations.map(ParsingRuleEvaluation::ruleId).distinct().size == ruleEvaluations.size) {
            "A parsing policy must evaluate each rule at most once"
        }
    }
}

data class ParsingRuleDecision(
    val outcome: ParsingRuleOutcome,
    val facts: List<ParsingFact> = emptyList(),
    val reason: String? = null,
    val nextStepId: ParsingStepId? = null,
) {
    init {
        require(facts.map(ParsingFact::key).distinct().size == facts.size) {
            "Parsing rule fact keys must be unique"
        }
        require(reason == null || reason.isNotBlank()) { "Parsing rule decision reason must not be blank" }
    }
}

data class ParsingRuleEvaluation(
    val ruleId: ParsingRuleId,
    val outcome: ParsingRuleOutcome,
    val facts: List<ParsingFact>,
    val reason: String?,
    val nextStepId: ParsingStepId?,
)

enum class ParsingRuleOutcome {
    PASSED,
    FAILED,
    SKIPPED,
}

data class ParsingFact(val key: String, val label: String, val value: ParsingFactValue) {
    init {
        require(key.isParsingDefinitionId()) { "Parsing fact key is invalid: $key" }
        require(label.isNotBlank()) { "Parsing fact label must not be blank" }
    }
}

sealed interface ParsingFactValue {
    data class Text(val value: String) : ParsingFactValue

    data class Count(val value: Int) : ParsingFactValue {
        init {
            require(value >= 0) { "Parsing fact count must not be negative" }
        }
    }

    data class Flag(val value: Boolean) : ParsingFactValue

    data class TextList(val value: List<String>) : ParsingFactValue
}

fun ParsingFactValue.displayValue(): String = when (this) {
    is ParsingFactValue.Count -> value.toString()
    is ParsingFactValue.Flag -> value.toString()
    is ParsingFactValue.Text -> value
    is ParsingFactValue.TextList -> value.joinToString("\n")
}

fun ParsingRuleEvaluation.traceDetails(): Map<String, String> = buildMap {
    put("ruleId", ruleId.value)
    put("ruleOutcome", outcome.name)
    reason?.let { put("reason", it) }
    nextStepId?.let { put("nextStepId", it.value) }
    facts.forEach { fact -> put("fact.${fact.key}", fact.value.displayValue()) }
}
