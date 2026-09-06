package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.analytics.GetUserAnalyticsOverviewUseCase
import org.every.nook.api.application.analytics.ReliableUserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsEventQueryPort
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsEventStorePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class UserAnalyticsConfig {
    @Bean
    fun userAnalyticsEventRecorder(storePort: UserAnalyticsEventStorePort, clock: Clock): UserAnalyticsEventRecorder =
        ReliableUserAnalyticsEventRecorder(storePort, clock)

    @Bean
    fun getUserAnalyticsOverviewUseCase(queryPort: UserAnalyticsEventQueryPort, clock: Clock) =
        GetUserAnalyticsOverviewUseCase(queryPort, clock)
}
