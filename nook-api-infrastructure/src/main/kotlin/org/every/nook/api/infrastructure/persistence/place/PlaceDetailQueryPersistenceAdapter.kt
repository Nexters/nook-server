package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceDetailView
import org.every.nook.api.application.place.PlacePostMediaTypeView
import org.every.nook.api.application.place.PlacePostMediaView
import org.every.nook.api.application.place.PlacePostPageView
import org.every.nook.api.application.place.PlacePostView
import org.every.nook.api.application.place.port.PlaceDetailQueryPort
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostEntity
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceDetailQueryPersistenceAdapter(
    private val placeRepository: PlaceJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val postRepository: PostJpaRepository,
    private val mediaRepository: PostMediaJpaRepository,
) : PlaceDetailQueryPort {
    @Transactional(readOnly = true)
    override fun find(userId: Long, placeId: Long, page: Int, size: Int): PlaceDetailView? {
        val place = placeRepository.findById(placeId).orElse(null) ?: return null
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
        )
        val savedPosts = savedPostRepository.findAllByUserIdAndPlaceId(userId, placeId, pageable)
        val bookmarked = bookmarkRepository.existsByUserIdAndPlaceId(userId, placeId)
        if (!bookmarked && savedPosts.totalElements == 0L) {
            return null
        }

        val sourcePostIds = savedPosts.content.map(UserSavedPostEntity::postId)
        val postsById = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            postRepository.findAllById(sourcePostIds).associateBy { requireNotNull(it.id) }
        }
        val representativeMediaByPostId = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            mediaRepository
                .findAllByPostIdInOrderByPostIdAscSequenceAsc(sourcePostIds)
                .groupBy(PostMediaEntity::postId)
                .mapValues { (_, media) -> media.first().toView() }
        }

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
            bookmarked = bookmarked,
            posts = PlacePostPageView(
                items = savedPosts.content.mapNotNull { savedPost ->
                    postsById[savedPost.postId]?.toView(
                        savedPost = savedPost,
                        representativeMedia = representativeMediaByPostId[savedPost.postId],
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

    private fun PostEntity.toView(
        savedPost: UserSavedPostEntity,
        representativeMedia: PlacePostMediaView?,
    ): PlacePostView = PlacePostView(
        postId = requireNotNull(savedPost.id),
        title = title,
        authorIdentifier = authorIdentifier,
        representativeMedia = representativeMedia,
        memo = savedPost.memo,
        savedAt = savedPost.createdAt,
    )

    private fun PostMediaEntity.toView(): PlacePostMediaView = PlacePostMediaView(
        type = PlacePostMediaTypeView.valueOf(mediaType.name),
        url = mediaUrl,
    )
}
