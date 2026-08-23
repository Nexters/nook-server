package org.every.nook.api.application.processing

@JvmInline
value class ParsingWorkflowId(val value: String) {
    init {
        require(value.isParsingDefinitionId()) { "Parsing workflow id is invalid: $value" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ParsingStepId(val value: String) {
    init {
        require(value.isParsingDefinitionId()) { "Parsing step id is invalid: $value" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ParsingRuleId(val value: String) {
    init {
        require(value.isParsingDefinitionId()) { "Parsing rule id is invalid: $value" }
    }

    override fun toString(): String = value
}

data class ParsingWorkflowDefinition(
    val id: ParsingWorkflowId,
    val title: String,
    val description: String,
    val steps: List<ParsingStepDefinition>,
    val edges: List<ParsingWorkflowEdgeDefinition>,
) {
    init {
        require(title.isNotBlank()) { "Parsing workflow title must not be blank" }
        require(description.isNotBlank()) { "Parsing workflow description must not be blank" }
        require(steps.isNotEmpty()) { "Parsing workflow must have at least one step" }
        require(steps.map(ParsingStepDefinition::id).isUnique()) {
            "Parsing step ids must be unique inside workflow $id"
        }
        val stepIds = steps.mapTo(mutableSetOf(), ParsingStepDefinition::id)
        require(edges.all { it.source in stepIds && it.target in stepIds }) {
            "Parsing workflow edges must reference steps inside workflow $id"
        }
        require(edges.map(ParsingWorkflowEdgeDefinition::id).isUnique()) {
            "Parsing workflow edge ids must be unique inside workflow $id"
        }
    }
}

data class ParsingStepDefinition(
    val id: ParsingStepId,
    val title: String,
    val description: String,
    val inputs: List<ParsingFieldDefinition>,
    val outputs: List<ParsingFieldDefinition>,
    val ruleIds: List<ParsingRuleId>,
) {
    init {
        require(title.isNotBlank()) { "Parsing step title must not be blank" }
        require(description.isNotBlank()) { "Parsing step description must not be blank" }
        require(inputs.map(ParsingFieldDefinition::key).isUnique()) { "Parsing step input keys must be unique: $id" }
        require(outputs.map(ParsingFieldDefinition::key).isUnique()) { "Parsing step output keys must be unique: $id" }
        require(ruleIds.isUnique()) { "Parsing rule ids must be unique inside step $id" }
    }
}

data class ParsingWorkflowEdgeDefinition(
    val id: String,
    val source: ParsingStepId,
    val target: ParsingStepId,
    val label: String? = null,
) {
    init {
        require(id.isParsingDefinitionId()) { "Parsing workflow edge id is invalid: $id" }
        require(label == null || label.isNotBlank()) { "Parsing workflow edge label must not be blank" }
    }
}

data class ParsingFieldDefinition(val key: String, val label: String, val description: String? = null) {
    init {
        require(key.isParsingDefinitionId()) { "Parsing field key is invalid: $key" }
        require(label.isNotBlank()) { "Parsing field label must not be blank" }
        require(description == null || description.isNotBlank()) { "Parsing field description must not be blank" }
    }
}

internal fun String.isParsingDefinitionId(): Boolean = matches(PARSING_DEFINITION_ID_PATTERN)

private fun <T> List<T>.isUnique(): Boolean = distinct().size == size

private val PARSING_DEFINITION_ID_PATTERN = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
