package org.every.nook.api.application.save

import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.port.InstagramPostProviderPort
import org.every.nook.api.application.save.port.PostMediaStoragePort
import org.every.nook.api.application.save.port.SaveInstagramPostPort
import org.every.nook.api.application.save.port.SavedInstagramPost
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.domain.post.PostSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveInstagramPostUseCaseTest {
    @Test
    fun `stores provider media before persisting the saved post`() {
        val calls = mutableListOf<String>()
        val provider = InstagramPostProviderPort {
            calls += "provider"
            Post(
                source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
                canonicalUrl = "https://www.instagram.com/p/ABC123/",
                media = listOf(PostMedia(PostMedia.MediaType.IMAGE, "https://source/image.jpg", 0)),
            )
        }
        val mediaStorage = PostMediaStoragePort { media ->
            calls += "media"
            media.copy(url = "https://cdn/image.jpg")
        }
        val persistence = SaveInstagramPostPort { userId, post ->
            calls += "persistence"
            assertEquals(7, userId)
            assertEquals("https://cdn/image.jpg", post.media.single().url)
            SavedInstagramPost(11, 13, PlaceParsingStatus.PENDING)
        }
        val useCase = SaveInstagramPostUseCase(provider, mediaStorage, persistence)

        val result = useCase(SaveInstagramPostUseCase.Command(7, "https://www.instagram.com/p/ABC123/"))

        assertEquals(listOf("provider", "media", "persistence"), calls)
        assertEquals(11, result.savedPostId)
        assertEquals(13, result.postId)
        assertEquals(PlaceParsingStatusView.PENDING, result.placeParsingStatus)
    }
}
