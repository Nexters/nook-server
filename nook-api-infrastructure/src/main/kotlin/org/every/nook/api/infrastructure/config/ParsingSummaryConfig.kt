package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.processing.ParsingSummaryPort
import org.every.nook.api.application.processing.PublishParsingSummariesUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ParsingSummaryConfig {
    @Bean
    fun publishParsingSummaries(port: ParsingSummaryPort) = PublishParsingSummariesUseCase(port)
}
