package org.every.nook.api.application.processing

class ParsingRuleCatalog(val workflows: List<ParsingWorkflowDefinition>, rules: List<ParsingRuleDefinition>) {
    private val workflowsById: Map<ParsingWorkflowId, ParsingWorkflowDefinition>
    private val stepsById: Map<ParsingStepId, ParsingStepDefinition>
    private val rulesById: Map<ParsingRuleId, ParsingRuleDefinition>

    init {
        require(workflows.isNotEmpty()) { "Parsing rule catalog must have at least one workflow" }
        workflowsById = workflows.associateUnique(ParsingWorkflowDefinition::id, "workflow")
        stepsById = workflows.flatMap(ParsingWorkflowDefinition::steps)
            .associateUnique(ParsingStepDefinition::id, "step")
        rulesById = rules.associateUnique(ParsingRuleDefinition::id, "rule")
        require(stepsById.values.flatMap(ParsingStepDefinition::ruleIds).all(rulesById::containsKey)) {
            "Parsing steps must reference rules in the catalog"
        }
        require(workflows.all { it.hasValidRuleEffectReferences(rulesById) }) {
            "Parsing rule effects must reference steps inside their workflow"
        }
    }

    fun workflow(id: ParsingWorkflowId): ParsingWorkflowDefinition? = workflowsById[id]

    fun step(id: ParsingStepId): ParsingStepDefinition? = stepsById[id]

    fun rule(id: ParsingRuleId): ParsingRuleDefinition? = rulesById[id]
}

private fun ParsingWorkflowDefinition.hasValidRuleEffectReferences(
    rulesById: Map<ParsingRuleId, ParsingRuleDefinition>,
): Boolean {
    val stepIds = steps.mapTo(mutableSetOf(), ParsingStepDefinition::id)
    return steps.flatMap(ParsingStepDefinition::ruleIds)
        .mapNotNull(rulesById::get)
        .flatMap { listOfNotNull(it.onPassed.nextStepId, it.onFailed.nextStepId) }
        .all(stepIds::contains)
}

private fun <T, K> List<T>.associateUnique(keySelector: (T) -> K, targetName: String): Map<K, T> {
    val keys = map(keySelector)
    require(keys.distinct().size == keys.size) { "Parsing $targetName ids must be globally unique" }
    return associateBy(keySelector)
}
