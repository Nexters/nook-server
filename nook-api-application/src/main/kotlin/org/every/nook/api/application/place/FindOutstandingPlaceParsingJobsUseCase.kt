package org.every.nook.api.application.place

import java.time.Duration

class FindOutstandingPlaceParsingJobsUseCase(
    private val jobPort: PlaceParsingJobPort,
    private val processingTimeout: Duration,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    init {
        require(batchSize > 0) { "Parsing dispatcher batch size must be positive" }
    }

    operator fun invoke(): List<OutstandingPlaceParsingJob> = jobPort.findOutstanding(processingTimeout, batchSize)

    private companion object {
        const val DEFAULT_BATCH_SIZE = 20
    }
}
