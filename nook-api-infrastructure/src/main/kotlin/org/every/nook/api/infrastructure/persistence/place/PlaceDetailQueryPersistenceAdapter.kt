package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceDetailView
import org.every.nook.api.application.place.PlacePostGroupView
import org.every.nook.api.application.place.PlacePostMediaTypeView
import org.every.nook.api.application.place.PlacePostMediaView
import org.every.nook.api.application.place.PlacePostPageView
import org.every.nook.api.application.place.PlacePostView
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.place.port.PlaceDetailQueryPort
import org.every.nook.api.application.place.port.SharedPlaceDetailQueryPort
import org.every.nook.api.application.place.snapshot
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.save.SharedGroupContentJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class PlaceDetailQueryPersistenceAdapter(
    private val placeRepository: PlaceJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val postRepository: PostJpaRepository,
    private val mediaRepository: PostMediaJpaRepository,
    private val groupRepository: GroupJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val tagCatalogPort: PlaceTagCatalogQueryPort = PlaceTagCatalogQueryPort { PlaceTag.defaultDefinitions },
    private val clock: Clock = Clock.systemUTC(),
    private val sharedContentRepository: SharedGroupContentJpaRepository? = null,
) : PlaceDetailQueryPort,
    SharedPlaceDetailQueryPort {
    @Transactional(readOnly = true)
    override fun find(userId: Long, placeId: Long, page: Int, size: Int): PlaceDetailView? =
        findInternal(userId, groupId = null, placeId, page, size)

    @Transactional(readOnly = true)
    override fun findInGroup(userId: Long, groupId: Long, placeId: Long, page: Int, size: Int): PlaceDetailView? =
        findInternal(userId, groupId, placeId, page, size)

    private fun findInternal(userId: Long, groupId: Long?, placeId: Long, page: Int, size: Int): PlaceDetailView? {
        val place = placeRepository.findById(placeId).orElse(null) ?: return null
        val savedPosts = findSavedPosts(userId, groupId, placeId, page, size)
        val bookmark = bookmarkRepository.findByUserIdAndPlaceId(userId, placeId)
        val bookmarked = bookmark != null
        if (!bookmarked && savedPosts.totalElements == 0L) {
            return null
        }
        val sourcePostIds = savedPosts.content.map(UserSavedPostEntity::postId)
        val postsById = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            postRepository.findAllById(sourcePostIds).associateBy { requireNotNull(it.id) }
        }
        val representativeMediaByPostId = findRepresentativeMedia(sourcePostIds)
        val savedPostIds = savedPosts.content.mapNotNull(UserSavedPostEntity::id)
        val groupsBySavedPostId = findGroups(userId, savedPostIds, groupId)
        return PlaceDetailView(
            id = requireNotNull(place.id),
            provider = place.provider,
            externalPlaceId = place.externalPlaceId,
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            category = place.category,
            phoneNumber = place.phoneNumber,
            thumbnailUrl = place.thumbnailUrl,
            thumbnailParsingStatus = PlaceThumbnailParsingStatusView.from(place.effectiveThumbnailParsingStatus()),
            photoUrls = place.photoUrls,
            openingHours = place.openingHours,
            openNow = place.openingHours?.isOpenAt(clock.instant()),
            tags = tagCatalogPort.snapshot().displayNames(place.representativeTags),
            bookmarked = bookmarked,
            memo = bookmark?.memo,
            posts = PlacePostPageView(
                items = savedPosts.content.mapNotNull { savedPost ->
                    postsById[savedPost.postId]?.toView(
                        savedPost = savedPost,
                        representativeMedia = representativeMediaByPostId[savedPost.postId],
                        groups = groupsBySavedPostId[requireNotNull(savedPost.id)].orEmpty(),
                    )
                },
                page = savedPosts.number,
                size = savedPosts.size,
                totalElements = savedPosts.totalElements,
                totalPages = savedPosts.totalPages,
                hasNext = savedPosts.hasNext(),
            ),
        )
    }

    private fun findSavedPosts(
        userId: Long,
        groupId: Long?,
        placeId: Long,
        page: Int,
        size: Int,
    ): Page<UserSavedPostEntity> {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
        )
        return if (groupId == null) {
            savedPostRepository.findAllByUserIdAndPlaceId(userId, placeId, pageable)
        } else {
            requireNotNull(sharedContentRepository)
                .findAllByUserIdAndGroupIdAndPlaceId(userId, groupId, placeId, pageable)
        }
    }

    private fun findRepresentativeMedia(postIds: List<Long>): Map<Long, PlacePostMediaView> = if (postIds.isEmpty()) {
        emptyMap()
    } else {
        mediaRepository
            .findAllByPostIdInOrderByPostIdAscSequenceAsc(postIds)
            .groupBy(PostMediaEntity::postId)
            .mapValues { (_, media) -> media.first().toView() }
    }

    private fun PostEntity.toView(
        savedPost: UserSavedPostEntity,
        representativeMedia: PlacePostMediaView?,
        groups: List<PlacePostGroupView>,
    ): PlacePostView = PlacePostView(
        postId = requireNotNull(savedPost.id),
        title = title,
        authorIdentifier = authorIdentifier,
        representativeMedia = representativeMedia,
        savedAt = savedPost.createdAt,
        groups = groups,
    )

    private fun findGroups(
        userId: Long,
        savedPostIds: List<Long>,
        requestedGroupId: Long? = null,
    ): Map<Long, List<PlacePostGroupView>> {
        if (savedPostIds.isEmpty()) {
            return emptyMap()
        }
        val groupPostsBySavedPostId = groupPostRepository
            .findAllByUserSavedPostIdIn(savedPostIds)
            .groupBy { it.userSavedPostId }
        val groupIds = groupPostsBySavedPostId.values
            .flatten()
            .mapTo(mutableSetOf()) { it.groupId }
        val groupsById = if (groupIds.isEmpty()) {
            emptyMap()
        } else {
            groupRepository.findAllByUserIdAndIdIn(userId, groupIds).associateBy { requireNotNull(it.id) }
        }

        return savedPostIds.associateWith { savedPostId ->
            groupPostsBySavedPostId[savedPostId].orEmpty()
                .filter { requestedGroupId == null || it.groupId == requestedGroupId }
                .mapNotNull { groupPost ->
                    groupsById[groupPost.groupId]?.let { group ->
                        PlacePostGroupView(
                            id = requireNotNull(group.id),
                            name = group.name,
                            color = group.color.name,
                        )
                    }
                }
        }
    }

    private fun PostMediaEntity.toView(): PlacePostMediaView = PlacePostMediaView(
        type = PlacePostMediaTypeView.valueOf(mediaType.name),
        url = mediaUrl,
    )
}
