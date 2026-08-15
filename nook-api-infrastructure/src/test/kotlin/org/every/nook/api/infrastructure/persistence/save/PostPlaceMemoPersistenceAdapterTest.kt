package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostPlaceMemoPersistenceAdapterTest {
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val postPlaceRepository = mock(PostPlaceJpaRepository::class.java)
    private val memoRepository = mock(UserSavedPostPlaceMemoJpaRepository::class.java)
    private val adapter = PostPlaceMemoPersistenceAdapter(
        userSavedPostRepository = savedPostRepository,
        postPlaceRepository = postPlaceRepository,
        memoRepository = memoRepository,
    )

    @Test
    fun `creates a memo for an owned saved post place`() {
        val savedPost = UserSavedPostEntity(userId = 7, postId = 101)
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(savedPost)
        `when`(postPlaceRepository.findByPostIdAndPlaceId(101, 17))
            .thenReturn(PostPlaceEntity(postId = 101, placeId = 17, sequence = 0))
        `when`(memoRepository.findByUserSavedPostIdAndPlaceId(11, 17)).thenReturn(null)

        val updated = adapter.update(userId = 7, postId = 11, placeId = 17, memo = "창가 자리 좋음")

        assertTrue(updated)
        verify(memoRepository).insertIgnore(7, 11, 17, "창가 자리 좋음")
    }

    @Test
    fun `updates an existing memo for an owned saved post place`() {
        val savedPost = UserSavedPostEntity(userId = 7, postId = 101)
        val existingMemo = UserSavedPostPlaceMemoEntity(
            userId = 7,
            userSavedPostId = 11,
            placeId = 17,
            memo = "기존 메모",
        )
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(savedPost)
        `when`(postPlaceRepository.findByPostIdAndPlaceId(101, 17))
            .thenReturn(PostPlaceEntity(postId = 101, placeId = 17, sequence = 0))
        `when`(memoRepository.findByUserSavedPostIdAndPlaceId(11, 17)).thenReturn(existingMemo)

        val updated = adapter.update(userId = 7, postId = 11, placeId = 17, memo = "수정 메모")

        assertTrue(updated)
        assertEquals("수정 메모", existingMemo.memo)
        verify(memoRepository, never()).insertIgnore(7, 11, 17, "수정 메모")
    }

    @Test
    fun `deletes an existing memo with null`() {
        val savedPost = UserSavedPostEntity(userId = 7, postId = 101)
        val existingMemo = UserSavedPostPlaceMemoEntity(
            userId = 7,
            userSavedPostId = 11,
            placeId = 17,
            memo = "기존 메모",
        )
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(savedPost)
        `when`(postPlaceRepository.findByPostIdAndPlaceId(101, 17))
            .thenReturn(PostPlaceEntity(postId = 101, placeId = 17, sequence = 0))
        `when`(memoRepository.findByUserSavedPostIdAndPlaceId(11, 17)).thenReturn(existingMemo)

        val updated = adapter.update(userId = 7, postId = 11, placeId = 17, memo = null)

        assertTrue(updated)
        verify(memoRepository).delete(existingMemo)
    }

    @Test
    fun `does not update another user's saved post`() {
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(null)

        val updated = adapter.update(userId = 7, postId = 11, placeId = 17, memo = "침범")

        assertFalse(updated)
        verifyNoInteractions(postPlaceRepository, memoRepository)
    }

    @Test
    fun `does not update a place unrelated to the saved post`() {
        val savedPost = UserSavedPostEntity(userId = 7, postId = 101)
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(savedPost)
        `when`(postPlaceRepository.findByPostIdAndPlaceId(101, 17)).thenReturn(null)

        val updated = adapter.update(userId = 7, postId = 11, placeId = 17, memo = "침범")

        assertFalse(updated)
        verifyNoInteractions(memoRepository)
    }
}
