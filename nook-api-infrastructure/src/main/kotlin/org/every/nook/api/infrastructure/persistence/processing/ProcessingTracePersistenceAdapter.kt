package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.processing.ProcessingTraceEvent
import org.every.nook.api.application.processing.ProcessingTracePort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Component
class ProcessingTracePersistenceAdapter(
    private val repository: ProcessingTraceJpaRepository,
    private val objectMapper: ObjectMapper,
) : ProcessingTracePort {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun record(event: ProcessingTraceEvent) {
        repository.save(
            ProcessingTraceEntity(
                postId = event.postId,
                flow = event.flow,
                stage = event.stage,
                action = event.action,
                outcome = event.outcome,
                attempt = event.attempt,
                durationMs = event.durationMs,
                details = event.details.takeIf(Map<String, String>::isNotEmpty)
                    ?.let(objectMapper::writeValueAsString),
            ),
        )
    }
}
