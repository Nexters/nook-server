package org.every.nook.api.processing

import mu.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.application.processing.ProcessParsingFollowUpJobsUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ParsingFollowUpJobDispatcher(private val processJobs: ProcessParsingFollowUpJobsUseCase) {
    @Scheduled(fixedDelayString = "\${parsing.follow-up.dispatcher-interval:5s}")
    @SchedulerLock(
        name = "parsingFollowUp.dispatchJobs",
        lockAtMostFor = "5m",
        lockAtLeastFor = "1s",
    )
    fun dispatch() {
        val count = processJobs()
        if (count > 0) {
            logger.info { "Parsing follow-up jobs processed: jobCount=$count" }
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
