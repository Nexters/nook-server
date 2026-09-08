package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.analytics.AnalyticsActivityQueryPort
import org.every.nook.api.application.analytics.GetAnalyticsActivityMembersUseCase
import org.every.nook.api.application.analytics.GetAnalyticsMemberEventsUseCase
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
    fun getAnalyticsActivityMembersUseCase(queryPort: AnalyticsActivityQueryPort) =
        GetAnalyticsActivityMembersUseCase(queryPort)

    @Bean
    fun getAnalyticsMemberEventsUseCase(queryPort: AnalyticsActivityQueryPort) =
        GetAnalyticsMemberEventsUseCase(queryPort)

    @Bean
    fun userAnalyticsEventRecorder(storePort: UserAnalyticsEventStorePort, clock: Clock): UserAnalyticsEventRecorder =
        ReliableUserAnalyticsEventRecorder(storePort, clock)

    @Bean
    fun getUserAnalyticsOverviewUseCase(queryPort: UserAnalyticsEventQueryPort, clock: Clock) =
        GetUserAnalyticsOverviewUseCase(queryPort, clock)
}
