package org.every.nook.api.infrastructure.persistence.postshare

import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostGroup
import org.every.nook.api.application.post.model.SavedPostMedia
import org.every.nook.api.application.post.model.SavedPostMediaType
import org.every.nook.api.application.post.model.SavedPostPlace
import org.every.nook.api.application.post.port.SavedPostQueryPort
import org.every.nook.api.infrastructure.persistence.group.GroupEntity
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.place.SharedPlaceBookmarkSyncJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

internal class PostShareTestFixture {
    val links = mock(PostShareLinkJpaRepository::class.java)
    val saved = mock(UserSavedPostJpaRepository::class.java)
    val locks = mock(UserSavedPostLockJpaRepository::class.java)
    val posts = mock(PostJpaRepository::class.java)
    val details = mock(SavedPostQueryPort::class.java)
    val groups = mock(GroupJpaRepository::class.java)
    val groupPosts = mock(GroupPostJpaRepository::class.java)
    val places = mock(UserSavedPostPlaceJpaRepository::class.java)
    val bookmarks = mock(UserPlaceBookmarkJpaRepository::class.java)
    val sharedBookmarks = mock(SharedPlaceBookmarkSyncJpaRepository::class.java)
    val mapper = jacksonObjectMapper()
    val source = savedPost(7, 11).apply { memo = "owner private memo" }
    val detail = SavedPostDetail(
        postId = 11,
        title = "shared title",
        body = "public content",
        authorIdentifier = "author",
        canonicalUrl = "https://www.instagram.com/p/ABC/",
        publishedAt = NOW,
        media = listOf(SavedPostMedia(SavedPostMediaType.IMAGE, "https://cdn.test/post.jpg", 0)),
        hashtags = listOf("카페"),
        memo = "owner private memo",
        savedAt = NOW,
        groups = listOf(SavedPostGroup(1, "owner private archive", "RED")),
        placeParsingStatus = PlaceParsingStatusView.COMPLETED,
        placeParsingFailureReason = null,
        places = listOf(place(31, 2), place(32, 7)),
    )
    val adapter = PostSharePersistenceAdapter(
        links,
        saved,
        locks,
        details,
        PostShareCopyAdapter(saved, locks, posts, groups, groupPosts, places, bookmarks, sharedBookmarks),
        mapper,
        Clock.fixed(NOW, ZoneOffset.UTC),
    )
    lateinit var storedLink: PostShareLinkEntity

    init {
        `when`(locks.findByIdAndUserIdForUpdate(11, 7)).thenReturn(source)
        `when`(locks.findByIdForUpdate(11)).thenReturn(source)
        `when`(saved.findById(11)).thenReturn(Optional.of(source))
        `when`(details.findDetail(7, 11)).thenReturn(detail)
        `when`(posts.findByIdForUpdate(101)).thenReturn(mock(PostEntity::class.java))
        `when`(groups.findByIdAndUserIdForUpdate(9, 8)).thenReturn(mock(GroupEntity::class.java))
        `when`(links.save(any(PostShareLinkEntity::class.java))).thenAnswer {
            storedLink = it.getArgument(0)
            storedLink
        }
        `when`(saved.saveAndFlush(any(UserSavedPostEntity::class.java))).thenAnswer {
            it.getArgument<UserSavedPostEntity>(0).also { entity -> setId(entity, 22) }
        }
    }

    fun issue(): String {
        val token = adapter.issue(7, 11).token
        `when`(links.findByTokenHash(PostShareTokens.hash(token))).thenReturn(storedLink)
        return token
    }

    fun savedPost(userId: Long, id: Long) = UserSavedPostEntity(userId, 101).also { setId(it, id) }

    private fun setId(entity: UserSavedPostEntity, id: Long) {
        UserSavedPostEntity::class.java.getDeclaredField("id").apply { isAccessible = true }.set(entity, id)
    }

    private fun place(id: Long, sequence: Int) = SavedPostPlace(
        id, "MANUAL", id.toString(), "place $id", "서울", BigDecimal.ONE, BigDecimal.TEN,
        null, null, "https://cdn.test/$id.jpg", PlaceThumbnailParsingStatusView.COMPLETED,
        listOf("조용함"), true, "owner place memo", sequence,
    )

    companion object {
        val NOW: Instant = Instant.parse("2026-10-18T00:00:00Z")
    }
}
