package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.save.FindSavedPostPlaceParsingUseCase
import org.every.nook.api.application.save.SaveInstagramPostUseCase
import org.every.nook.api.application.save.port.FindSavedPostPlaceParsingPort
import org.every.nook.api.application.save.port.PostMediaStoragePort
import org.every.nook.api.application.save.port.SaveInstagramPostPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SavedPostUseCaseConfig {
    @Bean
    fun saveInstagramPostUseCase(
        extractInstagramContentUseCase: ExtractInstagramContentUseCase,
        postMediaStoragePort: PostMediaStoragePort,
        saveInstagramPostPort: SaveInstagramPostPort,
    ): SaveInstagramPostUseCase = SaveInstagramPostUseCase(
        extractInstagramContentUseCase = extractInstagramContentUseCase,
        postMediaStoragePort = postMediaStoragePort,
        saveInstagramPostPort = saveInstagramPostPort,
    )

    @Bean
    fun findSavedPostPlaceParsingUseCase(
        findSavedPostPlaceParsingPort: FindSavedPostPlaceParsingPort,
    ): FindSavedPostPlaceParsingUseCase = FindSavedPostPlaceParsingUseCase(findSavedPostPlaceParsingPort)
}
