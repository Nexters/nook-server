package org.every.nook.api.application.processing

interface ParsingSummaryPort {
    fun candidates(afterId: Long, limit: Int): List<Long>
    fun publish(postId: Long)
}

class PublishParsingSummariesUseCase(private val port: ParsingSummaryPort) {
    private var afterId = 0L

    operator fun invoke() {
        val ids = port.candidates(afterId, BATCH_SIZE)
        ids.forEach { postId ->
            port.publish(postId)
            afterId = postId
        }
        if (ids.size < BATCH_SIZE) afterId = 0L
    }

    private companion object {
        const val BATCH_SIZE = 100
    }
}
