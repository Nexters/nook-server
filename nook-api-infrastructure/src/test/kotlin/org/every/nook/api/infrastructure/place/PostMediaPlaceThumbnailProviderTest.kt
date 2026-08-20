package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PostMediaPlaceThumbnailProviderTest {
    @Test
    fun `uses the matching image after a carousel cover and stores it`() {
        val repository = mock(PostMediaJpaRepository::class.java)
        `when`(
            repository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(
            listOf(
                media(11, "https://instagram.example/cover.jpg", 0),
                media(11, "https://instagram.example/place-1.jpg", 1),
                media(11, "https://instagram.example/place-2.jpg", 2),
            ),
        )
        val storedUrls = mutableListOf<String>()
        val storage = PostMediaStoragePort { source ->
            storedUrls += source.url
            source.copy(url = "https://cdn.example/${source.sequence}.jpg")
        }
        val provider = PostMediaPlaceThumbnailProvider(
            mediaRepository = repository,
            mediaStorage = storage,
            storedMediaBaseUrl = "https://cdn.example",
            obsoleteFixedThumbnailUrl = OBSOLETE_FIXED_URL,
        )

        val result = provider.fetch(
            PlaceThumbnailProvider.Request(
                place = place(),
                sourcePostId = 11,
                sourceMediaSequence = 2,
                postMediaFallbackAllowed = true,
            ),
        )

        assertEquals(listOf("https://instagram.example/place-2.jpg"), storedUrls)
        assertEquals(listOf("https://cdn.example/2.jpg"), result?.photoUrls)
        assertEquals(OBSOLETE_FIXED_URL, result?.replaceThumbnailUrl)
    }

    @Test
    fun `does not store media that is already in the bucket`() {
        val repository = mock(PostMediaJpaRepository::class.java)
        `when`(
            repository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(11, PostMedia.MediaType.IMAGE),
        ).thenReturn(listOf(media(11, "https://cdn.example/place.jpg", 0)))
        val provider = PostMediaPlaceThumbnailProvider(
            mediaRepository = repository,
            mediaStorage = PostMediaStoragePort { error("must not store bucket media again") },
            storedMediaBaseUrl = "https://cdn.example/",
            obsoleteFixedThumbnailUrl = OBSOLETE_FIXED_URL,
        )

        val result = provider.fetch(
            PlaceThumbnailProvider.Request(
                place = place(),
                sourcePostId = 11,
                sourceMediaSequence = 0,
                postMediaFallbackAllowed = true,
            ),
        )

        assertEquals(listOf("https://cdn.example/place.jpg"), result?.photoUrls)
    }

    @Test
    fun `does not use post media without confirmed OCR evidence`() {
        val repository = mock(PostMediaJpaRepository::class.java)
        val provider = PostMediaPlaceThumbnailProvider(
            mediaRepository = repository,
            mediaStorage = PostMediaStoragePort { error("must not store unverified media") },
            storedMediaBaseUrl = "https://cdn.example/",
            obsoleteFixedThumbnailUrl = OBSOLETE_FIXED_URL,
        )

        val result = provider.fetch(
            PlaceThumbnailProvider.Request(place(), sourcePostId = 11, sourceMediaSequence = 0),
        )

        assertEquals(null, result)
    }

    private fun media(postId: Long, url: String, sequence: Int) = PostMediaEntity(
        postId = postId,
        mediaType = PostMedia.MediaType.IMAGE,
        mediaUrl = url,
        sequence = sequence,
    )

    private fun place() = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "123",
        name = "Nook Cafe",
        address = "Seoul",
        latitude = BigDecimal("37.1"),
        longitude = BigDecimal("127.1"),
        category = null,
        phoneNumber = null,
        providerUrl = null,
    )

    private companion object {
        const val OBSOLETE_FIXED_URL = "https://cdn.example/fixed.jpg"
    }
}
