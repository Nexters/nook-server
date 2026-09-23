package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.push.DeletePushTokenUseCase
import org.every.nook.api.application.push.GetPushPreferenceUseCase
import org.every.nook.api.application.push.PushNotificationSender
import org.every.nook.api.application.push.PushPreferencePort
import org.every.nook.api.application.push.PushTokenPort
import org.every.nook.api.application.push.RegisterPushTokenUseCase
import org.every.nook.api.application.push.SendPostProcessingPushUseCase
import org.every.nook.api.application.push.UpdatePushPreferenceUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PushUseCaseConfig {
    @Bean
    fun registerPushTokenUseCase(pushTokenPort: PushTokenPort): RegisterPushTokenUseCase =
        RegisterPushTokenUseCase(pushTokenPort)

    @Bean
    fun deletePushTokenUseCase(pushTokenPort: PushTokenPort): DeletePushTokenUseCase =
        DeletePushTokenUseCase(pushTokenPort)

    @Bean
    fun getPushPreferenceUseCase(pushPreferencePort: PushPreferencePort): GetPushPreferenceUseCase =
        GetPushPreferenceUseCase(pushPreferencePort)

    @Bean
    fun updatePushPreferenceUseCase(pushPreferencePort: PushPreferencePort): UpdatePushPreferenceUseCase =
        UpdatePushPreferenceUseCase(pushPreferencePort)

    @Bean
    fun sendPostProcessingPushUseCase(
        pushTokenPort: PushTokenPort,
        pushPreferencePort: PushPreferencePort,
        sender: PushNotificationSender,
    ): SendPostProcessingPushUseCase = SendPostProcessingPushUseCase(pushTokenPort, pushPreferencePort, sender)
}
