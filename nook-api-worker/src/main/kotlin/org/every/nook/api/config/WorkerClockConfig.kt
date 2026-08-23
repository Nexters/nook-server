package org.every.nook.api.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class WorkerClockConfig {
    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun workerClock(): Clock = Clock.systemUTC()
}
