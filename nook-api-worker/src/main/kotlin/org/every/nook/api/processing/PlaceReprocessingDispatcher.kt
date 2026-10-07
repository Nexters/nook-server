package org.every.nook.api.processing

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.every.nook.api.application.admin.ProcessPlaceReprocessingUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PlaceReprocessingDispatcher(private val processJob: ProcessPlaceReprocessingUseCase) {
    @Scheduled(fixedDelayString = "\${place.reprocessing.dispatcher-interval:5s}")
    @SchedulerLock(name = "placeReprocessing.dispatch", lockAtMostFor = "20m", lockAtLeastFor = "1s")
    fun dispatch() {
        processJob()
    }
}
