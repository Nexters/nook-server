package org.every.nook.api.processing

import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.every.nook.api.application.processing.ProcessingMetrics
import org.springframework.stereotype.Component

@Component
class MicrometerProcessingMetrics(private val meterRegistry: MeterRegistry) : ProcessingMetrics {
    override fun record(measurement: ProcessingMetrics.Measurement) {
        meterRegistry.timer(
            METRIC_NAME,
            FLOW_TAG,
            measurement.flow,
            STAGE_TAG,
            measurement.stage,
            OUTCOME_TAG,
            measurement.outcome.name.lowercase(),
        ).record(measurement.duration)
        logger.info {
            "Processing stage measured: flow=${measurement.flow}, stage=${measurement.stage}, " +
                "postId=${measurement.postId}, attempt=${measurement.attempt}, " +
                "outcome=${measurement.outcome.name.lowercase()}, durationMs=${measurement.duration.toMillis()}"
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        const val METRIC_NAME = "nook.processing.stage"
        const val FLOW_TAG = "flow"
        const val STAGE_TAG = "stage"
        const val OUTCOME_TAG = "outcome"
    }
}
