package org.every.nook.api.application.post

import java.time.Duration

class FindOutstandingPostContentParsingJobsUseCase(
    private val jobPort: PostContentParsingJobPort,
    private val processingTimeout: Duration,
) {
    operator fun invoke(): List<OutstandingPostContentParsingJob> = jobPort.findOutstanding(processingTimeout)
}
