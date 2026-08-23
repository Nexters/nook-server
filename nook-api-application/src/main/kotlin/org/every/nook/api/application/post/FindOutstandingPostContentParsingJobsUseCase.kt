package org.every.nook.api.application.post

import java.time.Duration

class FindOutstandingPostContentParsingJobsUseCase(
    private val jobPort: PostContentParsingJobPort,
    private val processingTimeout: Duration,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    init {
        require(batchSize > 0) { "Parsing dispatcher batch size must be positive" }
    }

    operator fun invoke(): List<OutstandingPostContentParsingJob> = jobPort.findOutstanding(
        processingTimeout,
        batchSize,
    )

    private companion object {
        const val DEFAULT_BATCH_SIZE = 20
    }
}
