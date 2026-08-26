package org.every.nook.api.infrastructure.openai

import mu.KotlinLogging
import org.every.nook.api.application.providerusage.OpenAiTokenUsage
import org.every.nook.api.application.providerusage.OpenAiTokenUsageRecorder
import tools.jackson.databind.JsonNode
import java.time.Clock
import java.time.Instant

class OpenAiTokenUsageTracker(
    private val recorder: OpenAiTokenUsageRecorder,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun record(root: JsonNode, feature: String, fallbackModel: String) {
        val usage = root.path("usage")
        if (usage.isMissingNode || usage.isNull) return
        val required = listOf("input_tokens", "output_tokens", "total_tokens")
        if (required.any { usage.path(it).isMissingNode || usage.path(it).isNull }) return
        val inputTokens = usage.path("input_tokens").asLong()
        val outputTokens = usage.path("output_tokens").asLong()
        val totalTokens = usage.path("total_tokens").asLong()
        val cachedTokens = usage.path("input_tokens_details").path("cached_tokens").longValueOrNull() ?: 0L
        val model = root.path("model").takeUnless { it.isMissingNode || it.isNull }
            ?.asText()?.trim()?.ifBlank { null } ?: fallbackModel
        runCatching {
            recorder.record(
                OpenAiTokenUsage(
                    feature = feature,
                    model = model,
                    inputTokens = inputTokens,
                    cachedInputTokens = cachedTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens,
                    occurredAt = Instant.now(clock),
                ),
            )
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to record OpenAI token usage: feature=$feature, model=$model" }
        }
    }

    private fun JsonNode.longValueOrNull(): Long? = takeUnless { isMissingNode || isNull }?.asLong()

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
