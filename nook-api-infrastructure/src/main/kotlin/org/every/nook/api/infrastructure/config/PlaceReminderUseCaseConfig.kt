package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.post.ListUnlinkedSavedPostsUseCase
import org.every.nook.api.application.post.port.UnlinkedSavedPostQueryPort
import org.every.nook.api.application.reminder.GetPlaceReminderUseCase
import org.every.nook.api.application.reminder.PlaceReminderPreferencePort
import org.every.nook.api.application.reminder.UpdatePlaceReminderUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PlaceReminderUseCaseConfig {
    @Bean
    fun listUnlinkedSavedPostsUseCase(queryPort: UnlinkedSavedPostQueryPort): ListUnlinkedSavedPostsUseCase =
        ListUnlinkedSavedPostsUseCase(queryPort)

    @Bean
    fun getPlaceReminderUseCase(
        preferencePort: PlaceReminderPreferencePort,
        queryPort: UnlinkedSavedPostQueryPort,
    ): GetPlaceReminderUseCase = GetPlaceReminderUseCase(preferencePort, queryPort)

    @Bean
    fun updatePlaceReminderUseCase(
        preferencePort: PlaceReminderPreferencePort,
        queryPort: UnlinkedSavedPostQueryPort,
    ): UpdatePlaceReminderUseCase = UpdatePlaceReminderUseCase(preferencePort, queryPort)
}
