package org.every.nook.api.processing

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.application.processing.PublishParsingSummariesUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ParsingSummaryDispatcher(private val publish: PublishParsingSummariesUseCase) {
    @Scheduled(fixedDelayString = "\${parsing.summary.interval:10s}")
    @SchedulerLock(name = "parsingSummary.publish", lockAtMostFor = "2m", lockAtLeastFor = "1s")
    fun dispatch() = publish()
}
