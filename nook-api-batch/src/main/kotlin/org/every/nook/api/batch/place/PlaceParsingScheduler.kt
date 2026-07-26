package org.every.nook.api.batch.place

import org.every.nook.api.application.place.ProcessNextPlaceParsingJobUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PlaceParsingScheduler(private val processNextPlaceParsingJob: ProcessNextPlaceParsingJobUseCase) {
    @Scheduled(fixedDelayString = "\${place-parsing.worker.fixed-delay:5s}")
    fun processNext() {
        processNextPlaceParsingJob()
    }
}
