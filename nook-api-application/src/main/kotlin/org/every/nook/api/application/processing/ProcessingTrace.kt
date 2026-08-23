package org.every.nook.api.application.processing

data class ProcessingTraceEvent(
    val postId: Long,
    val flow: String,
    val stage: String,
    val action: String,
    val outcome: String,
    val attempt: Int? = null,
    val durationMs: Long? = null,
    val details: Map<String, String> = emptyMap(),
)

fun interface ProcessingTracePort {
    fun record(event: ProcessingTraceEvent)
}

object NoOpProcessingTracePort : ProcessingTracePort {
    override fun record(event: ProcessingTraceEvent) = Unit
}
