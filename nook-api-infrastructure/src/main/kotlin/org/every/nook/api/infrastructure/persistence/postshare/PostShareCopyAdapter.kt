package org.every.nook.api.infrastructure.persistence.postshare

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.post.share.PostShareError
import org.every.nook.api.application.post.share.PostShareException
import org.every.nook.api.application.post.share.PostShareSnapshot
import org.every.nook.api.application.post.share.SavePostShareResult
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.place.SharedPlaceBookmarkSyncJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostLockJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PostShareCopyAdapter(
    private val savedPosts: UserSavedPostJpaRepository,
    private val savedPostLocks: UserSavedPostLockJpaRepository,
    private val posts: PostJpaRepository,
    private val groups: GroupJpaRepository,
    private val groupPosts: GroupPostJpaRepository,
    private val savedPlaces: UserSavedPostPlaceJpaRepository,
    private val bookmarks: UserPlaceBookmarkJpaRepository,
    private val sharedBookmarks: SharedPlaceBookmarkSyncJpaRepository,
) {
    @Transactional
    fun save(
        userId: Long,
        link: PostShareLinkEntity,
        snapshot: PostShareSnapshot,
        groupIds: Set<Long>,
    ): SavePostShareResult {
        // Owned groups -> common post -> saved post rows. Normal saves also lock the common post.
        groupIds.sorted().forEach { groupId ->
            groups.findByIdAndUserIdForUpdate(groupId, userId) ?: throw GroupNotFoundException()
        }
        posts.findByIdForUpdate(link.sourcePostId) ?: throw PostShareException(PostShareError.EXPIRED)
        requirePostShareSource(link, savedPostLocks.findByIdForUpdate(link.sourceSavedPostId))
        val restored = savedPosts.restoreByUserIdAndPostId(userId, link.sourcePostId) > 0
        val existing = savedPostLocks.findByUserIdAndPostIdForUpdate(userId, link.sourcePostId)
        val saved = existing ?: savedPosts.saveAndFlush(UserSavedPostEntity(userId, link.sourcePostId))
        val savedId = requireNotNull(saved.id)
        if (existing == null || restored) copyPlaces(userId, saved, snapshot)
        addToGroups(savedId, groupIds)
        return SavePostShareResult(savedId, alreadySaved = existing != null && !restored)
    }

    private fun copyPlaces(userId: Long, saved: UserSavedPostEntity, snapshot: PostShareSnapshot) {
        val savedId = requireNotNull(saved.id)
        saved.placeLinksFrozen = true
        savedPlaces.deleteAllByUserSavedPostId(savedId)
        savedPlaces.saveAll(
            snapshot.places.map { UserSavedPostPlaceEntity(savedId, it.id, it.sequence, it.thumbnailUrl) },
        )
        snapshot.places.forEach { bookmarks.insertOrTouchWithMemo(userId, it.id, memo = null) }
    }

    private fun addToGroups(savedId: Long, groupIds: Set<Long>) {
        // Locking reads see preceding saves even if the initial token lookup established an older snapshot.
        val active = groupPosts.findAllByUserSavedPostIdForUpdate(savedId)
            .mapTo(mutableSetOf(), GroupPostEntity::groupId)
        val missing = (groupIds - active).filter { groupPosts.restore(it, savedId) == 0 }
        groupPosts.saveAll(missing.map { GroupPostEntity(it, savedId) })
        sharedBookmarks.insertAllForActiveSubscribers(savedId, groupIds)
    }
}

internal fun requirePostShareSource(link: PostShareLinkEntity, saved: UserSavedPostEntity?): UserSavedPostEntity =
    saved?.takeIf {
        it.deletedAt == null && it.shareGeneration == link.sourceGeneration && it.postId == link.sourcePostId
    } ?: throw PostShareException(PostShareError.EXPIRED)
