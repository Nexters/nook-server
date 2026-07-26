package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.instagram.ExtractInstagramContentUseCase
import org.every.nook.api.application.post.PostTitleGenerator
import org.every.nook.api.application.save.FindSavedPostPlaceParsingUseCase
import org.every.nook.api.application.save.SaveInstagramPostUseCase
import org.every.nook.api.application.save.UpdateSavedPostPlaceBookmarkUseCase
import org.every.nook.api.application.save.port.FindSavedPostPlaceParsingPort
import org.every.nook.api.application.save.port.PostMediaStoragePort
import org.every.nook.api.application.save.port.SaveInstagramPostPort
import org.every.nook.api.application.save.port.UpdateSavedPostPlaceBookmarkPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SavedPostUseCaseConfig {
    @Bean
    fun saveInstagramPostUseCase(
        extractInstagramContentUseCase: ExtractInstagramContentUseCase,
        postTitleGenerator: PostTitleGenerator,
        postMediaStoragePort: PostMediaStoragePort,
        saveInstagramPostPort: SaveInstagramPostPort,
    ): SaveInstagramPostUseCase = SaveInstagramPostUseCase(
        extractInstagramContentUseCase = extractInstagramContentUseCase,
        postTitleGenerator = postTitleGenerator,
        postMediaStoragePort = postMediaStoragePort,
        saveInstagramPostPort = saveInstagramPostPort,
    )

    @Bean
    fun findSavedPostPlaceParsingUseCase(
        findSavedPostPlaceParsingPort: FindSavedPostPlaceParsingPort,
    ): FindSavedPostPlaceParsingUseCase = FindSavedPostPlaceParsingUseCase(findSavedPostPlaceParsingPort)

    @Bean
    fun updateSavedPostPlaceBookmarkUseCase(
        updateSavedPostPlaceBookmarkPort: UpdateSavedPostPlaceBookmarkPort,
    ): UpdateSavedPostPlaceBookmarkUseCase = UpdateSavedPostPlaceBookmarkUseCase(updateSavedPostPlaceBookmarkPort)
}
