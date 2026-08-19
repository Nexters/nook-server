package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.domain.group.GroupColor
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.group.GroupEntity
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceDetailQueryPersistenceAdapterTest {
    private val placeRepository = mock(PlaceJpaRepository::class.java)
    private val bookmarkRepository = mock(UserPlaceBookmarkJpaRepository::class.java)
    private val savedPostRepository = mock(UserSavedPostJpaRepository::class.java)
    private val postRepository = mock(PostJpaRepository::class.java)
    private val mediaRepository = mock(PostMediaJpaRepository::class.java)
    private val groupRepository = mock(GroupJpaRepository::class.java)
    private val groupPostRepository = mock(GroupPostJpaRepository::class.java)
    private val adapter = PlaceDetailQueryPersistenceAdapter(
        placeRepository,
        bookmarkRepository,
        savedPostRepository,
        postRepository,
        mediaRepository,
        groupRepository,
        groupPostRepository,
    )

    @Test
    fun `bookmarked place is returned with an empty post page`() {
        val pageable = expectedPageable(page = 0, size = 20)
        val place = place()
        `when`(placeRepository.findById(17)).thenReturn(Optional.of(place))
        `when`(savedPostRepository.findAllByUserIdAndPlaceId(7, 17, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(bookmark(memo = null))

        val detail = assertNotNull(adapter.find(userId = 7, placeId = 17, page = 0, size = 20))

        assertTrue(detail.bookmarked)
        assertTrue(detail.posts.items.isEmpty())
        assertEquals(0L, detail.posts.totalElements)
        verifyNoInteractions(postRepository, mediaRepository, groupRepository, groupPostRepository)
    }

    @Test
    fun `place detail completes a stale pending status when a thumbnail URL exists`() {
        val pageable = expectedPageable(page = 0, size = 20)
        val place = place(
            thumbnailUrl = "https://cdn.example.com/place.jpg",
            thumbnailParsingStatus = PlaceThumbnailParsingStatus.PENDING,
        )
        `when`(placeRepository.findById(17)).thenReturn(Optional.of(place))
        `when`(savedPostRepository.findAllByUserIdAndPlaceId(7, 17, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(bookmark(memo = null))

        val detail = assertNotNull(adapter.find(userId = 7, placeId = 17, page = 0, size = 20))

        assertEquals(PlaceThumbnailParsingStatusView.COMPLETED, detail.thumbnailParsingStatus)
    }

    @Test
    fun `unbookmarked place remains accessible through the current user's related post`() {
        val pageable = expectedPageable(page = 1, size = 10)
        val place = place(thumbnailUrl = "https://example.com/place.jpg")
        val savedPost = savedPost()
        val sourcePost = sourcePost()
        val media = PostMediaEntity(101, PostMedia.MediaType.IMAGE, "https://example.com/image.jpg", 0)
        val group = mock(GroupEntity::class.java)
        `when`(group.id).thenReturn(301)
        `when`(group.name).thenReturn("맛집")
        `when`(group.color).thenReturn(GroupColor.YELLOW)
        `when`(placeRepository.findById(17)).thenReturn(Optional.of(place))
        `when`(savedPostRepository.findAllByUserIdAndPlaceId(7, 17, pageable))
            .thenReturn(PageImpl(listOf(savedPost), pageable, 11))
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(null)
        `when`(postRepository.findAllById(listOf(101))).thenReturn(listOf(sourcePost))
        `when`(mediaRepository.findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(101)))
            .thenReturn(listOf(media))
        `when`(groupPostRepository.findAllByUserSavedPostIdIn(listOf(21)))
            .thenReturn(listOf(GroupPostEntity(groupId = 301, userSavedPostId = 21)))
        `when`(groupRepository.findAllByUserIdAndIdIn(7, setOf(301))).thenReturn(listOf(group))

        val detail = assertNotNull(adapter.find(userId = 7, placeId = 17, page = 1, size = 10))

        assertFalse(detail.bookmarked)
        assertEquals(21, detail.posts.items.single().postId)
        assertNull(detail.memo)
        assertEquals(listOf("맛집"), detail.posts.items.single().groups.map { it.name })
        assertEquals(listOf("YELLOW"), detail.posts.items.single().groups.map { it.color })
        assertEquals("https://example.com/image.jpg", detail.posts.items.single().representativeMedia?.url)
        assertEquals(11L, detail.posts.totalElements)
        assertEquals(listOf("createdAt: DESC", "id: DESC"), pageable.sort.map { it.toString() }.toList())
        verify(savedPostRepository).findAllByUserIdAndPlaceId(7, 17, pageable)
    }

    @Test
    fun `place thumbnail does not replace missing post representative media`() {
        val pageable = expectedPageable(page = 0, size = 20)
        val place = place(thumbnailUrl = "https://example.com/place.jpg")
        val savedPost = savedPost()
        val sourcePost = sourcePost()
        `when`(placeRepository.findById(17)).thenReturn(Optional.of(place))
        `when`(savedPostRepository.findAllByUserIdAndPlaceId(7, 17, pageable))
            .thenReturn(PageImpl(listOf(savedPost), pageable, 1))
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(bookmark(memo = null))
        `when`(postRepository.findAllById(listOf(101))).thenReturn(listOf(sourcePost))
        `when`(mediaRepository.findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(101)))
            .thenReturn(emptyList())
        `when`(groupPostRepository.findAllByUserSavedPostIdIn(listOf(21)))
            .thenReturn(emptyList())

        val detail = assertNotNull(adapter.find(userId = 7, placeId = 17, page = 0, size = 20))

        assertNull(detail.posts.items.single().representativeMedia)
    }

    @Test
    fun `place memo is read from the bookmark, not from the post memo`() {
        val pageable = expectedPageable(page = 0, size = 20)
        val place = place()
        val savedPost = savedPost()
        val sourcePost = sourcePost()
        `when`(placeRepository.findById(17)).thenReturn(Optional.of(place))
        `when`(savedPostRepository.findAllByUserIdAndPlaceId(7, 17, pageable))
            .thenReturn(PageImpl(listOf(savedPost), pageable, 1))
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(bookmark(memo = "장소 메모"))
        `when`(postRepository.findAllById(listOf(101))).thenReturn(listOf(sourcePost))

        val detail = assertNotNull(adapter.find(userId = 7, placeId = 17, page = 0, size = 20))

        // savedPost.memo 는 "내 메모" 지만 장소 메모는 북마크에서만 온다.
        assertEquals("장소 메모", detail.memo)
    }

    @Test
    fun `place without a bookmark or a related post is hidden`() {
        val pageable = expectedPageable(page = 0, size = 20)
        val place = place()
        `when`(placeRepository.findById(17)).thenReturn(Optional.of(place))
        `when`(savedPostRepository.findAllByUserIdAndPlaceId(7, 17, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))
        `when`(bookmarkRepository.findByUserIdAndPlaceId(7, 17)).thenReturn(null)

        assertNull(adapter.find(userId = 7, placeId = 17, page = 0, size = 20))
        verifyNoInteractions(postRepository, mediaRepository, groupRepository, groupPostRepository)
    }

    private fun expectedPageable(page: Int, size: Int): PageRequest = PageRequest.of(
        page,
        size,
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
    )

    private fun place(
        thumbnailUrl: String? = null,
        thumbnailParsingStatus: PlaceThumbnailParsingStatus = PlaceThumbnailParsingStatus.COMPLETED,
    ): PlaceEntity {
        val place = mock(PlaceEntity::class.java)
        `when`(place.id).thenReturn(17)
        `when`(place.provider).thenReturn("KAKAO")
        `when`(place.externalPlaceId).thenReturn("1234")
        `when`(place.name).thenReturn("원동미나리삼겹살")
        `when`(place.address).thenReturn("서울 용산구")
        `when`(place.latitude).thenReturn(BigDecimal("37.1"))
        `when`(place.longitude).thenReturn(BigDecimal("127.1"))
        `when`(place.thumbnailUrl).thenReturn(thumbnailUrl)
        `when`(place.thumbnailParsingStatus).thenReturn(thumbnailParsingStatus)
        return place
    }

    private fun savedPost(): UserSavedPostEntity {
        val savedPost = mock(UserSavedPostEntity::class.java)
        `when`(savedPost.id).thenReturn(21)
        `when`(savedPost.postId).thenReturn(101)
        `when`(savedPost.memo).thenReturn("내 메모")
        `when`(savedPost.createdAt).thenReturn(Instant.parse("2026-07-27T00:00:00Z"))
        return savedPost
    }

    private fun bookmark(memo: String?): UserPlaceBookmarkEntity =
        UserPlaceBookmarkEntity(userId = 7, placeId = 17, memo = memo)

    private fun sourcePost(): PostEntity {
        val post = mock(PostEntity::class.java)
        `when`(post.id).thenReturn(101)
        `when`(post.title).thenReturn("용산 미나리삼겹살")
        `when`(post.authorIdentifier).thenReturn("author")
        return post
    }
}
