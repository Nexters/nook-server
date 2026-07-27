package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostSource
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostPersistenceAdapterTest {
    private val postRepository = mock(PostJpaRepository::class.java)
    private val userSavedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val parsingJobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val adapter = PostPersistenceAdapter(
        postJpaRepository = postRepository,
        postMediaJpaRepository = mock(PostMediaJpaRepository::class.java),
        postHashtagJpaRepository = mock(PostHashtagJpaRepository::class.java),
        userSavedPostJpaRepository = userSavedPostRepository,
        placeParsingJobJpaRepository = parsingJobRepository,
        postPlaceJpaRepository = mock(PostPlaceJpaRepository::class.java),
        placeJpaRepository = mock(PlaceJpaRepository::class.java),
        userPlaceBookmarkJpaRepository = mock(UserPlaceBookmarkJpaRepository::class.java),
    )

    @Test
    fun `stores a creation memo on the user saved post`() {
        val postEntity = mock(PostEntity::class.java)
        val parsingJob = mock(PlaceParsingJobEntity::class.java)
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(postEntity.id).thenReturn(101)
        `when`(parsingJob.status).thenReturn(PlaceParsingStatus.PENDING)
        `when`(savedPost.id).thenReturn(11)
        `when`(postRepository.save(org.mockito.ArgumentMatchers.any(PostEntity::class.java))).thenReturn(postEntity)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(parsingJob)
        `when`(
            userSavedPostRepository.save(
                org.mockito.ArgumentMatchers.any(UserSavedPostEntity::class.java),
            ),
        ).thenReturn(savedPost)

        adapter.create(
            userId = 7,
            post = Post(
                source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
                canonicalUrl = "https://www.instagram.com/p/ABC123/",
            ),
            memo = "주말에 방문",
        )

        val captor = ArgumentCaptor.forClass(UserSavedPostEntity::class.java)
        verify(userSavedPostRepository).save(captor.capture())
        assertEquals(7, captor.value.userId)
        assertEquals(101, captor.value.postId)
        assertEquals("주말에 방문", captor.value.memo)
    }

    @Test
    fun `updates only the saved post owned by the user`() {
        val ownedPost = UserSavedPostEntity(userId = 7, postId = 101, memo = "기존 메모")
        `when`(userSavedPostRepository.findByIdAndUserId(11, 7)).thenReturn(ownedPost)

        val updated = adapter.update(userId = 7, postId = 11, memo = "새 메모")

        assertTrue(updated)
        assertEquals("새 메모", ownedPost.memo)
    }

    @Test
    fun `deletes an owned memo with null`() {
        val ownedPost = UserSavedPostEntity(userId = 7, postId = 101, memo = "기존 메모")
        `when`(userSavedPostRepository.findByIdAndUserId(11, 7)).thenReturn(ownedPost)

        val updated = adapter.update(userId = 7, postId = 11, memo = null)

        assertTrue(updated)
        assertEquals(null, ownedPost.memo)
    }

    @Test
    fun `does not update another user's saved post`() {
        `when`(userSavedPostRepository.findByIdAndUserId(11, 7)).thenReturn(null)

        assertFalse(adapter.update(userId = 7, postId = 11, memo = "침범"))
    }
}
