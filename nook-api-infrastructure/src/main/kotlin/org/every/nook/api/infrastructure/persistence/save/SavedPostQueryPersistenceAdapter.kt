package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostMedia
import org.every.nook.api.application.post.model.SavedPostMediaType
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.model.SavedPostPlace
import org.every.nook.api.application.post.model.SavedPostSummary
import org.every.nook.api.application.post.port.SavedPostQueryPort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SavedPostQueryPersistenceAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val postRepository: PostJpaRepository,
    private val mediaRepository: PostMediaJpaRepository,
    private val hashtagRepository: PostHashtagJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val parsingJobRepository: PlaceParsingJobJpaRepository,
) : SavedPostQueryPort {
    @Transactional(readOnly = true)
    override fun findAll(userId: Long, page: Int, size: Int): SavedPostPage {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
        )
        val savedPosts = savedPostRepository.findAllByUserId(userId, pageable)
        val sourcePostIds = savedPosts.content.map(UserSavedPostEntity::postId)
        val postsById = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            postRepository.findAllById(sourcePostIds).associateBy { requireNotNull(it.id) }
        }
        val firstMediaByPostId = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            mediaRepository
                .findAllByPostIdInOrderByPostIdAscSequenceAsc(sourcePostIds)
                .groupBy(PostMediaEntity::postId)
                .mapValues { (_, media) -> media.first().toView() }
        }

        return SavedPostPage(
            items = savedPosts.content.mapNotNull { savedPost ->
                postsById[savedPost.postId]?.toSummary(
                    savedPost = savedPost,
                    representativeMedia = firstMediaByPostId[savedPost.postId],
                )
            },
            page = savedPosts.number,
            size = savedPosts.size,
            totalElements = savedPosts.totalElements,
            totalPages = savedPosts.totalPages,
            hasNext = savedPosts.hasNext(),
        )
    }

    @Transactional(readOnly = true)
    override fun findDetail(userId: Long, postId: Long): SavedPostDetail? {
        val savedPost = savedPostRepository.findByIdAndUserId(postId, userId) ?: return null
        val sourcePost = postRepository.findById(savedPost.postId).orElse(null) ?: return null
        val media = mediaRepository
            .findAllByPostIdInOrderByPostIdAscSequenceAsc(listOf(savedPost.postId))
            .map { it.toView() }
        val hashtags = hashtagRepository
            .findAllByPostIdOrderBySequenceAsc(savedPost.postId)
            .map { it.hashtag }
        val postPlaces = postPlaceRepository.findAllByPostIdOrderBySequenceAsc(savedPost.postId)
        val placesById = placeRepository.findAllById(postPlaces.map { it.placeId })
            .associateBy { requireNotNull(it.id) }
        val bookmarkedPlaceIds = if (postPlaces.isEmpty()) {
            emptySet()
        } else {
            bookmarkRepository
                .findAllByUserIdAndPlaceIdIn(userId, postPlaces.map { it.placeId })
                .mapTo(mutableSetOf()) { it.placeId }
        }
        val parsingJob = parsingJobRepository.findByPostId(savedPost.postId)

        return SavedPostDetail(
            postId = postId,
            title = sourcePost.title,
            body = sourcePost.body,
            authorIdentifier = sourcePost.authorIdentifier,
            canonicalUrl = sourcePost.canonicalUrl,
            publishedAt = sourcePost.publishedAt,
            media = media,
            hashtags = hashtags,
            memo = savedPost.memo ?: sourcePost.memo,
            savedAt = savedPost.createdAt,
            placeParsingStatus = PlaceParsingStatusView.from(parsingJob?.status ?: PlaceParsingStatus.PENDING),
            placeParsingFailureReason = parsingJob?.failureReason,
            places = postPlaces.mapNotNull { postPlace ->
                placesById[postPlace.placeId]?.let { place ->
                    SavedPostPlace(
                        id = requireNotNull(place.id),
                        provider = place.provider,
                        externalPlaceId = place.externalPlaceId,
                        name = place.name,
                        address = place.address,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        category = place.category,
                        phoneNumber = place.phoneNumber,
                        bookmarked = postPlace.placeId in bookmarkedPlaceIds,
                        sequence = postPlace.sequence,
                    )
                }
            },
        )
    }

    private fun PostEntity.toSummary(
        savedPost: UserSavedPostEntity,
        representativeMedia: SavedPostMedia?,
    ): SavedPostSummary = SavedPostSummary(
        postId = requireNotNull(savedPost.id),
        title = title,
        authorIdentifier = authorIdentifier,
        representativeMedia = representativeMedia,
        memo = savedPost.memo ?: memo,
        savedAt = savedPost.createdAt,
    )

    private fun PostMediaEntity.toView(): SavedPostMedia = SavedPostMedia(
        type = SavedPostMediaType.valueOf(mediaType.name),
        url = mediaUrl,
        sequence = sequence,
    )
}
