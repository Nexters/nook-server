package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.post.GetSavedPostRecoveryUseCase
import org.every.nook.api.application.post.RequestSavedPostRecoveryUseCase
import org.every.nook.api.application.post.SavedPostRecoveryPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SavedPostRecoveryConfig {
    @Bean
    fun getSavedPostRecovery(port: SavedPostRecoveryPort) = GetSavedPostRecoveryUseCase(port)

    @Bean
    fun requestSavedPostRecovery(port: SavedPostRecoveryPort) = RequestSavedPostRecoveryUseCase(port)
}
