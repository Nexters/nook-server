package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceParsingPersistenceAdapterTest {
    private val jobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val postRepository = mock(PostJpaRepository::class.java)
    private val hashtagRepository = mock(PostHashtagJpaRepository::class.java)
    private val placeRepository = mock(PlaceJpaRepository::class.java)
    private val postPlaceRepository = mock(PostPlaceJpaRepository::class.java)
    private val userSavedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val adapter = PlaceParsingPersistenceAdapter(
        jobRepository = jobRepository,
        postRepository = postRepository,
        hashtagRepository = hashtagRepository,
        placeRepository = placeRepository,
        postPlaceRepository = postPlaceRepository,
        userSavedPostRepository = userSavedPostRepository,
        userPlaceBookmarkRepository = bookmarkRepository,
    )

    @Test
    fun `completed places are bookmarked on by default for every user who saved the post`() {
        val job = PlaceParsingJobEntity(postId = 11, status = PlaceParsingStatus.PROCESSING)
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(jobRepository.findByPostId(11)).thenReturn(job)
        `when`(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "123")).thenReturn(place)
        `when`(userSavedPostRepository.findDistinctUserIdsByPostId(11)).thenReturn(listOf(7, 8))

        adapter.complete(
            postId = 11,
            places = listOf(
                PlaceCandidate(
                    provider = "KAKAO",
                    externalPlaceId = "123",
                    name = "Nook Cafe",
                    address = "Seoul",
                    latitude = BigDecimal("37.1"),
                    longitude = BigDecimal("127.1"),
                    category = null,
                    phoneNumber = null,
                    providerUrl = null,
                ),
            ),
        )

        verify(bookmarkRepository).insertIgnore(7, 17)
        verify(bookmarkRepository).insertIgnore(8, 17)
        assertEquals(PlaceParsingStatus.COMPLETED, job.status)
    }
}
