package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.group.GroupPostPage
import org.every.nook.api.application.group.GroupPostSummary
import org.every.nook.api.application.group.port.GroupPostQueryPort
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PostProcessingView
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostGroup
import org.every.nook.api.application.post.model.SavedPostMedia
import org.every.nook.api.application.post.model.SavedPostMediaType
import org.every.nook.api.application.post.model.SavedPostPage
import org.every.nook.api.application.post.model.SavedPostPlace
import org.every.nook.api.application.post.model.SavedPostSummary
import org.every.nook.api.application.post.port.SavedPostQueryPort
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.place.effectiveThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class SavedPostQueryPersistenceAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val postRepository: PostJpaRepository,
    private val mediaRepository: PostMediaJpaRepository,
    private val hashtagRepository: PostHashtagJpaRepository,
    private val savedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val parsingJobRepository: PlaceParsingJobJpaRepository,
    private val contentParsingJobRepository: PostContentParsingJobJpaRepository,
    private val groupRepository: GroupJpaRepository,
    private val groupPostRepository: GroupPostJpaRepository,
    private val memberRepository: MemberJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : SavedPostQueryPort,
    GroupPostQueryPort {
    @Transactional(readOnly = true)
    override fun findAll(userId: Long, page: Int, size: Int): SavedPostPage {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
        )
        return savedPostRepository.findAllByUserId(userId, pageable).toPage(usePlaceThumbnail = true)
    }

    @Transactional(readOnly = true)
    override fun findAll(userId: Long, groupId: Long, page: Int, size: Int): GroupPostPage? {
        val group = groupRepository.findByIdAndUserId(groupId, userId) ?: return null
        val owner = memberRepository.findById(group.userId).orElse(null) ?: return null
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
        )
        val savedPosts = savedPostRepository.findAllByUserIdAndGroupId(userId, groupId, pageable)
        val savedPostPage = savedPosts.toPage(usePlaceThumbnail = false)
        val savedPostIds = savedPosts.content.mapNotNull(UserSavedPostEntity::id)
        val placeCountsBySavedPostId = if (savedPostIds.isEmpty()) {
            emptyMap()
        } else {
            savedPostPlaceRepository
                .findAllByUserSavedPostIdInOrderByUserSavedPostIdAscSequenceAsc(savedPostIds)
                .groupingBy { it.userSavedPostId }
                .eachCount()
        }

        return GroupPostPage(
            ownerNickname = owner.nickname,
            items = savedPostPage.items.map { post ->
                GroupPostSummary(
                    post = post,
                    placeCount = placeCountsBySavedPostId.getOrDefault(post.postId, 0).toLong(),
                )
            },
            page = savedPostPage.page,
            size = savedPostPage.size,
            totalElements = savedPostPage.totalElements,
            totalPages = savedPostPage.totalPages,
            hasNext = savedPostPage.hasNext,
        )
    }

    private fun Page<UserSavedPostEntity>.toPage(usePlaceThumbnail: Boolean): SavedPostPage {
        val savedPosts = this
        val sourcePostIds = savedPosts.content.map(UserSavedPostEntity::postId)
        val savedPostIds = savedPosts.content.mapNotNull(UserSavedPostEntity::id)
        val postsById = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            postRepository.findAllById(sourcePostIds).associateBy { requireNotNull(it.id) }
        }
        val firstMediaByPostId = findFirstMediaByPostId(sourcePostIds)
        val savedPostPlaces = findSavedPostPlaces(savedPostIds)
        val savedPostIdsWithPlaces = savedPostPlaces.mapTo(mutableSetOf()) { it.userSavedPostId }
        val firstPlaceThumbnailBySavedPostId = if (usePlaceThumbnail) {
            findFirstPlaceThumbnailBySavedPostId(savedPostPlaces)
        } else {
            emptyMap()
        }
        val contentJobByPostId = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            contentParsingJobRepository.findAllByPostIdIn(sourcePostIds).associate {
                it.postId to it
            }
        }
        val placeJobByPostId = if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            parsingJobRepository.findAllByPostIdIn(sourcePostIds).associate {
                it.postId to it
            }
        }
        return SavedPostPage(
            items = savedPosts.content.mapNotNull { savedPost ->
                val contentJob = contentJobByPostId[savedPost.postId]
                val placeJob = placeJobByPostId[savedPost.postId]
                val processing = PostProcessingView.from(
                    contentStatus = contentJob?.status ?: PostContentParsingStatus.COMPLETED,
                    placeStatus = placeJob?.status.effectiveFor(savedPostIdsWithPlaces.contains(savedPost.id)),
                    contentStartedAt = contentJob?.processingStartedAt(),
                    placeStartedAt = placeJob?.processingStartedAt(),
                    now = clock.instant(),
                )
                postsById[savedPost.postId]?.toSummary(
                    savedPost = savedPost,
                    representativeMedia = firstPlaceThumbnailBySavedPostId[savedPost.id]
                        ?.let { SavedPostMedia(SavedPostMediaType.IMAGE, it, THUMBNAIL_SEQUENCE) }
                        ?: firstMediaByPostId[savedPost.postId],
                    processing = processing,
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
        val savedPostPlaces = savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(postId)
        val placesById = placeRepository.findAllById(savedPostPlaces.map { it.placeId })
            .associateBy { requireNotNull(it.id) }
        val bookmarks = if (savedPostPlaces.isEmpty()) {
            emptyList()
        } else {
            bookmarkRepository.findAllByUserIdAndPlaceIdIn(userId, savedPostPlaces.map { it.placeId })
        }
        val bookmarkedPlaceIds = bookmarks.mapTo(mutableSetOf()) { it.placeId }
        val memoByPlaceId = bookmarks.mapNotNull { bookmark -> bookmark.memo?.let { bookmark.placeId to it } }.toMap()
        val parsingJob = parsingJobRepository.findByPostId(savedPost.postId)
        val contentParsingJob = contentParsingJobRepository.findByPostId(savedPost.postId)
        val processing = PostProcessingView.from(
            contentStatus = contentParsingJob?.status ?: PostContentParsingStatus.COMPLETED,
            placeStatus = parsingJob?.status.effectiveFor(savedPostPlaces.isNotEmpty()),
            contentStartedAt = contentParsingJob?.processingStartedAt(),
            placeStartedAt = parsingJob?.processingStartedAt(),
            now = clock.instant(),
        )
        val groups = findGroups(userId, listOf(postId)).getValue(postId)

        return SavedPostDetail(
            postId = postId,
            title = sourcePost.title,
            body = sourcePost.body,
            authorIdentifier = sourcePost.authorIdentifier,
            canonicalUrl = sourcePost.canonicalUrl,
            publishedAt = sourcePost.publishedAt,
            media = media,
            hashtags = hashtags,
            memo = savedPost.memo,
            savedAt = savedPost.createdAt,
            groups = groups,
            placeParsingStatus = PlaceParsingStatusView.from(
                parsingJob?.status.effectiveFor(savedPostPlaces.isNotEmpty()) ?: PlaceParsingStatus.PENDING,
            ),
            placeParsingFailureReason = parsingJob?.failureReason
                .takeIf { parsingJob?.status == PlaceParsingStatus.FAILED && savedPostPlaces.isEmpty() },
            places = savedPostPlaces.toSavedPostPlaces(placesById, bookmarkedPlaceIds, memoByPlaceId),
            processingStatus = processing.status,
            processingStage = processing.stage,
            processingPercent = processing.processingPercent,
        )
    }

    private fun List<UserSavedPostPlaceEntity>.toSavedPostPlaces(
        placesById: Map<Long, PlaceEntity>,
        bookmarkedPlaceIds: Set<Long>,
        memoByPlaceId: Map<Long, String>,
    ): List<SavedPostPlace> = mapNotNull { postPlace ->
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
                thumbnailUrl = place.thumbnailUrl,
                tags = place.representativeTags.map { it.displayName },
                bookmarked = postPlace.placeId in bookmarkedPlaceIds,
                memo = memoByPlaceId[postPlace.placeId],
                thumbnailParsingStatus = PlaceThumbnailParsingStatusView.from(place.effectiveThumbnailParsingStatus()),
                sequence = postPlace.sequence,
            )
        }
    }

    private fun PostEntity.toSummary(
        savedPost: UserSavedPostEntity,
        representativeMedia: SavedPostMedia?,
        processing: PostProcessingView,
    ): SavedPostSummary = SavedPostSummary(
        postId = requireNotNull(savedPost.id),
        title = title,
        authorIdentifier = authorIdentifier,
        representativeMedia = representativeMedia,
        memo = savedPost.memo,
        savedAt = savedPost.createdAt,
        processingStatus = processing.status,
        processingStage = processing.stage,
        processingPercent = processing.processingPercent,
    )

    private fun findGroups(userId: Long, savedPostIds: List<Long>): Map<Long, List<SavedPostGroup>> {
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
            groupPostsBySavedPostId[savedPostId].orEmpty().mapNotNull { groupPost ->
                groupsById[groupPost.groupId]?.let { group ->
                    SavedPostGroup(
                        id = requireNotNull(group.id),
                        name = group.name,
                        color = group.color.name,
                    )
                }
            }
        }
    }

    private fun PostMediaEntity.toView(): SavedPostMedia = SavedPostMedia(
        type = SavedPostMediaType.valueOf(mediaType.name),
        url = mediaUrl,
        sequence = sequence,
    )

    private fun findFirstMediaByPostId(sourcePostIds: List<Long>): Map<Long, SavedPostMedia> =
        if (sourcePostIds.isEmpty()) {
            emptyMap()
        } else {
            mediaRepository
                .findAllByPostIdInOrderByPostIdAscSequenceAsc(sourcePostIds)
                .groupBy(PostMediaEntity::postId)
                .mapValues { (_, media) -> media.first().toView() }
        }

    private fun findFirstPlaceThumbnailBySavedPostId(
        savedPostPlaces: List<UserSavedPostPlaceEntity>,
    ): Map<Long, String> {
        if (savedPostPlaces.isEmpty()) {
            return emptyMap()
        }
        val placeIds = savedPostPlaces.mapTo(mutableSetOf()) { it.placeId }
        if (placeIds.isEmpty()) {
            return emptyMap()
        }
        val thumbnailByPlaceId = placeRepository.findAllById(placeIds)
            .mapNotNull { place -> place.thumbnailUrl?.let { requireNotNull(place.id) to it } }
            .toMap()

        return savedPostPlaces
            .asSequence()
            .mapNotNull { savedPostPlace ->
                thumbnailByPlaceId[savedPostPlace.placeId]?.let { savedPostPlace.userSavedPostId to it }
            }
            .distinctBy { (savedPostId) -> savedPostId }
            .toMap()
    }

    private fun findSavedPostPlaces(savedPostIds: List<Long>): List<UserSavedPostPlaceEntity> =
        if (savedPostIds.isEmpty()) {
            emptyList()
        } else {
            savedPostPlaceRepository.findAllByUserSavedPostIdInOrderByUserSavedPostIdAscSequenceAsc(savedPostIds)
        }

    private companion object {
        const val THUMBNAIL_SEQUENCE = 0
    }
}

private fun PostContentParsingJobEntity.processingStartedAt() =
    if (status == PostContentParsingStatus.PROCESSING) updatedAt else null

private fun PlaceParsingJobEntity.processingStartedAt() =
    if (status == PlaceParsingStatus.PROCESSING) updatedAt else null

private fun PlaceParsingStatus?.effectiveFor(hasPlaces: Boolean): PlaceParsingStatus? =
    if (hasPlaces) PlaceParsingStatus.COMPLETED else this
