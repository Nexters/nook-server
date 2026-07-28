package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostSource
import org.every.nook.api.infrastructure.persistence.group.GroupEntity
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
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
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostPersistenceAdapterTest {
    private val postRepository = mock(PostJpaRepository::class.java)
    private val userSavedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val parsingJobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val eventPublisher = mock(org.springframework.context.ApplicationEventPublisher::class.java)
    private val adapter = PostPersistenceAdapter(
        postJpaRepository = postRepository,
        postMediaJpaRepository = mock(PostMediaJpaRepository::class.java),
        postHashtagJpaRepository = mock(PostHashtagJpaRepository::class.java),
        userSavedPostJpaRepository = userSavedPostRepository,
        placeParsingJobJpaRepository = parsingJobRepository,
        postPlaceJpaRepository = mock(PostPlaceJpaRepository::class.java),
        placeJpaRepository = mock(PlaceJpaRepository::class.java),
        userPlaceBookmarkJpaRepository = mock(UserPlaceBookmarkJpaRepository::class.java),
        groupJpaRepository = groupRepository,
        groupPostJpaRepository = groupPostRepository,
        eventPublisher = eventPublisher,
    )

    @Test
    fun `stores a creation memo on the user saved post`() {
        val group = mock(GroupEntity::class.java)
        val postEntity = mock(PostEntity::class.java)
        val parsingJob = mock(PlaceParsingJobEntity::class.java)
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(group.id).thenReturn(17)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17))).thenReturn(listOf(group))
        `when`(postEntity.id).thenReturn(101)
        `when`(parsingJob.status).thenReturn(PlaceParsingStatus.PENDING)
        `when`(savedPost.id).thenReturn(11)
        `when`(postRepository.save(org.mockito.ArgumentMatchers.any(PostEntity::class.java))).thenReturn(postEntity)
        `when`(
            parsingJobRepository.save(
                org.mockito.ArgumentMatchers.any(PlaceParsingJobEntity::class.java),
            ),
        ).thenReturn(parsingJob)
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
            groupIds = setOf(17),
        )

        val captor = ArgumentCaptor.forClass(UserSavedPostEntity::class.java)
        verify(userSavedPostRepository).save(captor.capture())
        assertEquals(7, captor.value.userId)
        assertEquals(101, captor.value.postId)
        assertEquals("주말에 방문", captor.value.memo)
        val eventCaptor = ArgumentCaptor.forClass(PlaceParsingJobRequestedEvent::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertEquals(101, eventCaptor.value.postId)
    }

    @Test
    fun `rejects empty groups before creating any post data`() {
        assertFailsWith<InvalidGroupException> {
            adapter.create(
                userId = 7,
                post = Post(
                    source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
                    canonicalUrl = "https://www.instagram.com/p/ABC123/",
                ),
                memo = null,
                groupIds = emptySet(),
            )
        }

        verifyNoInteractions(postRepository)
    }

    @Test
    fun `stores the saved post in each owned group`() {
        val firstGroup = mock(GroupEntity::class.java)
        val secondGroup = mock(GroupEntity::class.java)
        val postEntity = mock(PostEntity::class.java)
        val parsingJob = mock(PlaceParsingJobEntity::class.java)
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(firstGroup.id).thenReturn(17)
        `when`(secondGroup.id).thenReturn(18)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17, 18)))
            .thenReturn(listOf(firstGroup, secondGroup))
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
            memo = null,
            groupIds = setOf(17, 18),
        )

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<GroupPostEntity>>
        verify(groupPostRepository).saveAll(captor.capture())
        assertEquals(setOf(17L, 18L), captor.value.map { it.groupId }.toSet())
        assertTrue(captor.value.all { it.userSavedPostId == 11L })
    }

    @Test
    fun `reuses the user saved post and restarts only a failed parsing job`() {
        val firstGroup = mock(GroupEntity::class.java)
        val secondGroup = mock(GroupEntity::class.java)
        val postEntity = mock(PostEntity::class.java)
        val savedPost = mock(UserSavedPostEntity::class.java)
        val existingGroupPost = GroupPostEntity(groupId = 17, userSavedPostId = 11)
        val parsingJob = PlaceParsingJobEntity(
            postId = 101,
            status = PlaceParsingStatus.FAILED,
            failureReason = "No place candidate matched",
            attemptCount = 4,
        )
        `when`(firstGroup.id).thenReturn(17)
        `when`(secondGroup.id).thenReturn(18)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17, 18)))
            .thenReturn(listOf(firstGroup, secondGroup))
        `when`(postEntity.id).thenReturn(101)
        `when`(postRepository.findBySourceForUpdate("INSTAGRAM", "ABC123")).thenReturn(postEntity)
        `when`(parsingJobRepository.findByPostId(101)).thenReturn(parsingJob)
        `when`(savedPost.id).thenReturn(11)
        `when`(userSavedPostRepository.findByUserIdAndPostId(7, 101)).thenReturn(savedPost)
        `when`(groupPostRepository.findAllByUserSavedPostId(11)).thenReturn(listOf(existingGroupPost))

        val result = adapter.reuse(
            userId = 7,
            source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
            memo = "덮어쓰지 않을 메모",
            groupIds = setOf(17, 18),
        )

        assertEquals(11, result.postId)
        assertEquals(PlaceParsingStatus.PENDING, result.placeParsingStatus)
        assertEquals(PlaceParsingStatus.PENDING, parsingJob.status)
        assertEquals(0, parsingJob.attemptCount)
        assertNull(parsingJob.failureReason)
        verify(userSavedPostRepository, never()).save(
            org.mockito.ArgumentMatchers.any(UserSavedPostEntity::class.java),
        )
        @Suppress("UNCHECKED_CAST")
        val groupCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<GroupPostEntity>>
        verify(groupPostRepository).saveAll(groupCaptor.capture())
        assertEquals(listOf(18L), groupCaptor.value.map(GroupPostEntity::groupId))
        val eventCaptor = ArgumentCaptor.forClass(PlaceParsingJobRequestedEvent::class.java)
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertEquals(101, eventCaptor.value.postId)
    }

    @Test
    fun `rejects inaccessible groups before creating any post data`() {
        val ownedGroup = mock(GroupEntity::class.java)
        `when`(ownedGroup.id).thenReturn(17)
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(17, 18))).thenReturn(listOf(ownedGroup))

        assertFailsWith<GroupNotFoundException> {
            adapter.create(
                userId = 7,
                post = Post(
                    source = PostSource(type = "INSTAGRAM", externalPostId = "ABC123"),
                    canonicalUrl = "https://www.instagram.com/p/ABC123/",
                ),
                memo = null,
                groupIds = setOf(17, 18),
            )
        }

        verifyNoInteractions(postRepository)
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
