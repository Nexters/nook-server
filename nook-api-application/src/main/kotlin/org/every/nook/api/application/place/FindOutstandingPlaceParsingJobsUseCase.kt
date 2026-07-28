package org.every.nook.api.application.place

import java.time.Duration

class FindOutstandingPlaceParsingJobsUseCase(
    private val jobPort: PlaceParsingJobPort,
    private val processingTimeout: Duration,
) {
    operator fun invoke(): List<OutstandingPlaceParsingJob> = jobPort.findOutstanding(processingTimeout)
}
