package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.domain.group.GroupColor
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.group.GroupEntity
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.member.MemberEntity
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkEntity
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedPostQueryPersistenceAdapterTest {
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val postRepository = mock(PostJpaRepository::class.java)
    private val mediaRepository = mock(PostMediaJpaRepository::class.java)
    private val hashtagRepository = mock(PostHashtagJpaRepository::class.java)
    private val postPlaceRepository = mock(PostPlaceJpaRepository::class.java)
    private val placeRepository = mock(PlaceJpaRepository::class.java)
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val parsingJobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val contentParsingJobRepository = mock(PostContentParsingJobJpaRepository::class.java)
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val memberRepository = mock(MemberJpaRepository::class.java)
    private val adapter = SavedPostQueryPersistenceAdapter(
        savedPostRepository,
        postRepository,
        mediaRepository,
        hashtagRepository,
        postPlaceRepository,
        placeRepository,
        bookmarkRepository,
        parsingJobRepository,
        contentParsingJobRepository,
        groupRepository,
        groupPostRepository,
        memberRepository,
    )

    @Test
    fun `detail preserves media hashtag and place order with bookmark state`() {
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(savedPost.postId).thenReturn(101)
        `when`(savedPost.memo).thenReturn("내 메모")
        `when`(savedPost.createdAt).thenReturn(Instant.parse("2026-07-27T00:00:00Z"))
        val post = PostEntity(
            sourceType = "INSTAGRAM",
            externalPostId = "ABC",
            canonicalUrl = "https://www.instagram.com/p/ABC/",
            authorIdentifier = "author",
            title = "title",
            body = "body",
        )
        val firstMedia = PostMediaEntity(101, PostMedia.MediaType.IMAGE, "https://example.com/1.jpg", 0)
        val secondMedia = PostMediaEntity(101, PostMedia.MediaType.VIDEO, "https://example.com/2.mp4", 1)
        val firstPlace = PostPlaceEntity(101, 201, 0)
        val secondPlace = PostPlaceEntity(101, 202, 1)
        val placeOne = place(id = 201, name = "첫 장소")
        val placeTwo = place(id = 202, name = "둘째 장소")
        val bookmark = mock(UserPlaceBookmarkEntity::class.java)
        val group = mock(GroupEntity::class.java)
        `when`(bookmark.placeId).thenReturn(202)
        `when`(group.id).thenReturn(301)
        `when`(group.name).thenReturn("맛집")
        `when`(group.color).thenReturn(GroupColor.YELLOW)

        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(savedPost)
        `when`(postRepository.findById(101)).thenReturn(Optional.of(post))
        `when`(mediaRepository.findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(101)))
            .thenReturn(listOf(firstMedia, secondMedia))
        `when`(hashtagRepository.findAllByPostIdOrderBySequenceAsc(101))
            .thenReturn(listOf(PostHashtagEntity(101, "첫태그", 0), PostHashtagEntity(101, "둘째태그", 1)))
        `when`(postPlaceRepository.findAllByPostIdOrderBySequenceAsc(101))
            .thenReturn(listOf(firstPlace, secondPlace))
        `when`(placeRepository.findAllById(listOf(201, 202))).thenReturn(listOf(placeTwo, placeOne))
        `when`(bookmarkRepository.findAllByUserIdAndPlaceIdIn(7, listOf(201, 202))).thenReturn(listOf(bookmark))
        `when`(parsingJobRepository.findByPostId(101))
            .thenReturn(PlaceParsingJobEntity(101, PlaceParsingStatus.COMPLETED))
        `when`(groupPostRepository.findAllByUserSavedPostIdIn(listOf(11)))
            .thenReturn(listOf(GroupPostEntity(groupId = 301, userSavedPostId = 11)))
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(301))).thenReturn(listOf(group))

        val detail = requireNotNull(adapter.findDetail(userId = 7, postId = 11))

        assertEquals(listOf(0, 1), detail.media.map { it.sequence })
        assertEquals(listOf("첫태그", "둘째태그"), detail.hashtags)
        assertEquals(listOf("첫 장소", "둘째 장소"), detail.places.map { it.name })
        assertEquals(listOf(false, true), detail.places.map { it.bookmarked })
        assertEquals(PlaceParsingStatusView.COMPLETED, detail.placeParsingStatus)
        assertEquals("내 메모", detail.memo)
        assertEquals(listOf("맛집"), detail.groups.map { it.name })
        assertEquals(listOf("YELLOW"), detail.groups.map { it.color })
    }

    @Test
    fun `another user's saved post is not returned`() {
        `when`(savedPostRepository.findByIdAndUserId(11, 7)).thenReturn(null)

        assertNull(adapter.findDetail(userId = 7, postId = 11))
        verify(savedPostRepository).findByIdAndUserId(11, 7)
    }

    @Test
    fun `list uses stable newest first pagination`() {
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(savedPost.id).thenReturn(11)
        `when`(savedPost.postId).thenReturn(101)
        `when`(savedPost.memo).thenReturn("목록 메모")
        `when`(savedPost.createdAt).thenReturn(Instant.parse("2026-07-27T00:00:00Z"))
        val post = mock(PostEntity::class.java)
        `when`(post.id).thenReturn(101)
        `when`(post.title).thenReturn("title")
        val requestedPage = PageRequest.of(
            2,
            10,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
        )
        `when`(savedPostRepository.findAllByUserId(7, requestedPage))
            .thenReturn(PageImpl(listOf(savedPost), requestedPage, 21))
        `when`(postRepository.findAllById(listOf(101))).thenReturn(listOf(post))
        `when`(mediaRepository.findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(101))).thenReturn(emptyList())

        val result = adapter.findAll(userId = 7, page = 2, size = 10)

        verify(savedPostRepository).findAllByUserId(7, requestedPage)
        assertEquals(listOf("createdAt: DESC", "id: DESC"), requestedPage.sort.map { it.toString() }.toList())
        assertEquals(2, result.page)
        assertEquals(21L, result.totalElements)
        assertEquals(false, result.hasNext)
        assertEquals("목록 메모", result.items.single().memo)
    }

    @Test
    fun `group list returns the actual owner nickname and batched place counts`() {
        val firstSavedPost = savedPost(id = 11, sourcePostId = 101)
        val secondSavedPost = savedPost(id = 12, sourcePostId = 102)
        val firstPost = sourcePost(id = 101, title = "첫 게시물")
        val secondPost = sourcePost(id = 102, title = "둘째 게시물")
        val group = mock(GroupEntity::class.java)
        val owner = mock(MemberEntity::class.java)
        val requestedPage = PageRequest.of(
            0,
            20,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
        )
        `when`(group.userId).thenReturn(9)
        `when`(owner.nickname).thenReturn("Purr")
        `when`(groupRepository.findByIdAndUserId(17, 7)).thenReturn(group)
        `when`(memberRepository.findById(9)).thenReturn(Optional.of(owner))
        `when`(savedPostRepository.findAllByUserIdAndGroupId(7, 17, requestedPage))
            .thenReturn(PageImpl(listOf(firstSavedPost, secondSavedPost), requestedPage, 2))
        `when`(postRepository.findAllById(listOf(101L, 102L))).thenReturn(listOf(firstPost, secondPost))
        `when`(mediaRepository.findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(101L, 102L)))
            .thenReturn(emptyList())
        `when`(postPlaceRepository.findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(101L, 102L)))
            .thenReturn(listOf(PostPlaceEntity(101, 201, 0), PostPlaceEntity(101, 202, 1)))

        val result = requireNotNull(adapter.findAll(userId = 7, groupId = 17, page = 0, size = 20))

        assertEquals("Purr", result.ownerNickname)
        assertEquals(listOf(2L, 0L), result.items.map { it.placeCount })
        assertEquals(2, result.totalElements)
        verify(memberRepository).findById(9)
        verify(postPlaceRepository).findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(101L, 102L))
        verify(savedPostRepository).findAllByUserIdAndGroupId(7, 17, requestedPage)
    }

    @Test
    fun `another users group is not returned`() {
        `when`(groupRepository.findByIdAndUserId(17, 7)).thenReturn(null)

        assertNull(adapter.findAll(userId = 7, groupId = 17, page = 0, size = 20))
    }

    private fun savedPost(id: Long, sourcePostId: Long): UserSavedPostEntity {
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(savedPost.id).thenReturn(id)
        `when`(savedPost.postId).thenReturn(sourcePostId)
        `when`(savedPost.createdAt).thenReturn(Instant.parse("2026-07-27T00:00:00Z"))
        return savedPost
    }

    private fun sourcePost(id: Long, title: String): PostEntity {
        val post = mock(PostEntity::class.java)
        `when`(post.id).thenReturn(id)
        `when`(post.title).thenReturn(title)
        return post
    }

    private fun place(id: Long, name: String): PlaceEntity {
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(id)
        `when`(place.provider).thenReturn("KAKAO")
        `when`(place.externalPlaceId).thenReturn(id.toString())
        `when`(place.name).thenReturn(name)
        `when`(place.address).thenReturn("서울")
        `when`(place.latitude).thenReturn(BigDecimal("37.1"))
        `when`(place.longitude).thenReturn(BigDecimal("127.1"))
        return place
    }
}
