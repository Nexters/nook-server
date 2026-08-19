package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.post.PostContentParsingJobRequestedEvent
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.CreatedPost
import org.every.nook.api.application.post.port.ExistingPost
import org.every.nook.api.application.post.port.FindExistingPostPort
import org.every.nook.api.application.post.port.FindPostPlaceParsingPort
import org.every.nook.api.application.post.port.PostPlaceParsingSnapshot
import org.every.nook.api.application.post.port.ReusePostPort
import org.every.nook.api.application.post.port.UpdatePostMemoPort
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.domain.place.GeoPoint
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceProviderReference
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.domain.post.PostSource
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.place.effectiveThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class PostPersistenceAdapter(
    private val postJpaRepository: PostJpaRepository,
    private val postContentParsingJobJpaRepository: PostContentParsingJobJpaRepository,
    private val userSavedPostJpaRepository: UserSavedPostJpaRepository,
    private val userSavedPostPlaceJpaRepository: UserSavedPostPlaceJpaRepository,
    private val placeParsingJobJpaRepository: PlaceParsingJobJpaRepository,
    private val placeJpaRepository: PlaceJpaRepository,
    private val userPlaceBookmarkJpaRepository: UserPlaceBookmarkJpaRepository,
    private val groupJpaRepository: GroupJpaRepository,
    private val groupPostJpaRepository: GroupPostJpaRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) : CreatePostPort,
    FindExistingPostPort,
    FindPostPlaceParsingPort,
    ReusePostPort,
    UpdatePostMemoPort {
    @Transactional(readOnly = true)
    override fun find(source: PostSource): ExistingPost? {
        val post = postJpaRepository.findBySourceTypeAndExternalPostId(source.type, source.externalPostId)
            ?: return null
        val postId = requireNotNull(post.id)
        val contentJob = postContentParsingJobJpaRepository.findByPostId(postId)
            ?: return null
        return ExistingPost(
            contentParsingStatus = contentJob.status,
            placeParsingStatus = placeParsingJobJpaRepository.findByPostId(postId)?.status,
        )
    }

    @Transactional
    override fun create(userId: Long, post: Post, memo: String?, groupIds: Set<Long>): CreatedPost {
        validateOwnedGroups(userId, groupIds)
        val postEntity = postJpaRepository.findBySourceForUpdate(
            post.source.type,
            post.source.externalPostId,
        ) ?: saveNewPost(post)
        val sourcePostId = requireNotNull(postEntity.id)
        val existingContentJob = postContentParsingJobJpaRepository.findByPostId(sourcePostId)
        val contentJob = existingContentJob
            ?: postContentParsingJobJpaRepository.save(
                PostContentParsingJobEntity(
                    postId = sourcePostId,
                    status = PostContentParsingStatus.PENDING,
                    nextAttemptAt = clock.instant(),
                ),
            )
        val userPostCreation = findOrCreateUserPost(userId, sourcePostId, memo)
        val userPost = userPostCreation.entity
        initializeSavedPostPlaces(
            savedPost = userPost,
            created = userPostCreation.created,
            sourcePostId = sourcePostId,
            savedPostPlaceRepository = userSavedPostPlaceJpaRepository,
            bookmarkRepository = userPlaceBookmarkJpaRepository,
        )
        addToGroups(requireNotNull(userPost.id), groupIds)
        if (existingContentJob == null) {
            eventPublisher.publishEvent(PostContentParsingJobRequestedEvent(sourcePostId, clock.instant()))
        } else {
            restartFailedJob(sourcePostId, contentJob)
        }

        val createdPost = CreatedPost(
            postId = requireNotNull(userPost.id),
            contentParsingStatus = contentJob.status,
            placeParsingStatus = placeParsingJobJpaRepository.findByPostId(sourcePostId)?.status,
        )
        logSavedPostMapping(sourcePostId, createdPost.postId, userPostCreation.created, "post.save.completed")
        return createdPost
    }

    @Transactional
    override fun reuse(userId: Long, source: PostSource, memo: String?, groupIds: Set<Long>): CreatedPost {
        validateOwnedGroups(userId, groupIds)
        val post = requireNotNull(
            postJpaRepository.findBySourceForUpdate(source.type, source.externalPostId),
        )
        val sourcePostId = requireNotNull(post.id)
        val contentJob = requireNotNull(postContentParsingJobJpaRepository.findByPostId(sourcePostId))
        val userPostCreation = findOrCreateUserPost(userId, sourcePostId, memo)
        val userPost = userPostCreation.entity
        initializeSavedPostPlaces(
            savedPost = userPost,
            created = userPostCreation.created,
            sourcePostId = sourcePostId,
            savedPostPlaceRepository = userSavedPostPlaceJpaRepository,
            bookmarkRepository = userPlaceBookmarkJpaRepository,
        )
        addToGroups(requireNotNull(userPost.id), groupIds)
        restartFailedJob(sourcePostId, contentJob)
        val placeParsingJob = placeParsingJobJpaRepository.findByPostId(sourcePostId)
        val createdPost = CreatedPost(
            postId = requireNotNull(userPost.id),
            contentParsingStatus = contentJob.status,
            placeParsingStatus = placeParsingJob?.status,
        )
        logSavedPostMapping(sourcePostId, createdPost.postId, userPostCreation.created, "post.reuse.completed")
        return createdPost
    }

    @Transactional
    override fun update(userId: Long, postId: Long, memo: String?): Boolean {
        val userPost = userSavedPostJpaRepository.findByIdAndUserId(postId, userId) ?: return false
        userPost.memo = memo
        return true
    }

    @Transactional(readOnly = true)
    override fun find(userId: Long, postId: Long): PostPlaceParsingSnapshot? {
        val userPost = userSavedPostJpaRepository.findByIdAndUserId(postId, userId) ?: return null
        val parsingJob = placeParsingJobJpaRepository.findByPostId(userPost.postId)
        val savedPostPlaces = userSavedPostPlaceJpaRepository.findAllByUserSavedPostIdOrderBySequenceAsc(postId)
        val bookmarks = if (savedPostPlaces.isEmpty()) {
            emptyList()
        } else {
            userPlaceBookmarkJpaRepository.findAllByUserIdAndPlaceIdIn(userId, savedPostPlaces.map { it.placeId })
        }
        val bookmarkedPlaceIds = bookmarks.mapTo(mutableSetOf()) { it.placeId }
        val memoByPlaceId = bookmarks.mapNotNull { bookmark -> bookmark.memo?.let { bookmark.placeId to it } }.toMap()
        val placesById = placeJpaRepository.findAllById(savedPostPlaces.map { it.placeId })
            .associateBy { requireNotNull(it.id) }
        val effectiveParsingStatus = if (savedPostPlaces.isNotEmpty()) {
            PlaceParsingStatus.COMPLETED
        } else {
            parsingJob?.status ?: PlaceParsingStatus.PENDING
        }

        return PostPlaceParsingSnapshot(
            postId = postId,
            placeParsingStatus = effectiveParsingStatus,
            failureReason = parsingJob?.failureReason.takeIf { effectiveParsingStatus == PlaceParsingStatus.FAILED },
            places = savedPostPlaces.mapNotNull { savedPostPlace ->
                placesById[savedPostPlace.placeId]?.toDomain()?.let { place ->
                    PostPlaceParsingSnapshot.RelatedPlace(
                        place = place,
                        bookmarked = savedPostPlace.placeId in bookmarkedPlaceIds,
                        thumbnailUrl = placesById[savedPostPlace.placeId]?.thumbnailUrl,
                        thumbnailParsingStatus = PlaceThumbnailParsingStatusView.from(
                            placesById[savedPostPlace.placeId]?.effectiveThumbnailParsingStatus()
                                ?: error("Place must exist for postPlace"),
                        ),
                        tags = placesById[savedPostPlace.placeId]?.representativeTags.orEmpty().map { it.displayName },
                        memo = memoByPlaceId[savedPostPlace.placeId],
                    )
                }
            },
        )
    }

    private fun saveNewPost(post: Post): PostEntity {
        val postEntity = postJpaRepository.save(
            PostEntity(
                sourceType = post.source.type,
                externalPostId = post.source.externalPostId,
                canonicalUrl = post.canonicalUrl,
                authorIdentifier = post.authorIdentifier,
                title = post.title,
                body = post.body,
                publishedAt = post.publishedAt,
                sourceLocationTag = post.sourceLocationTag,
            ),
        )
        return postEntity
    }

    private fun findOrCreateUserPost(userId: Long, postId: Long, memo: String?): UserPostCreation {
        userSavedPostJpaRepository.restoreByUserIdAndPostId(userId, postId)
        return userSavedPostJpaRepository.findByUserIdAndPostId(userId, postId)
            ?.let { UserPostCreation(it, created = false) }
            ?: UserPostCreation(
                entity = userSavedPostJpaRepository.save(
                    UserSavedPostEntity(
                        userId = userId,
                        postId = postId,
                        memo = memo,
                    ),
                ),
                created = true,
            )
    }

    private fun addToGroups(userSavedPostId: Long, groupIds: Set<Long>) {
        val activeGroupIds = groupPostJpaRepository.findAllByUserSavedPostId(userSavedPostId)
            .mapTo(mutableSetOf(), GroupPostEntity::groupId)
        val missingGroupIds = (groupIds - activeGroupIds).filter { groupId ->
            groupPostJpaRepository.restore(groupId, userSavedPostId) == 0
        }
        groupPostJpaRepository.saveAll(
            missingGroupIds.map { groupId ->
                GroupPostEntity(
                    groupId = groupId,
                    userSavedPostId = userSavedPostId,
                )
            },
        )
    }

    private data class UserPostCreation(val entity: UserSavedPostEntity, val created: Boolean)

    private fun restartFailedJob(postId: Long, contentJob: PostContentParsingJobEntity) {
        if (contentJob.status == PostContentParsingStatus.FAILED) {
            contentJob.status = PostContentParsingStatus.PENDING
            contentJob.failureReason = null
            contentJob.attemptCount = 0
            contentJob.nextAttemptAt = clock.instant()
            eventPublisher.publishEvent(PostContentParsingJobRequestedEvent(postId, clock.instant()))
            return
        }
        if (contentJob.status != PostContentParsingStatus.COMPLETED) {
            return
        }
        val placeJob = placeParsingJobJpaRepository.findByPostId(postId)
            ?: placeParsingJobJpaRepository.save(
                PlaceParsingJobEntity(
                    postId = postId,
                    status = PlaceParsingStatus.PENDING,
                    nextAttemptAt = clock.instant(),
                ),
            ).also {
                eventPublisher.publishEvent(PlaceParsingJobRequestedEvent(postId, clock.instant()))
            }
        if (placeJob.status == PlaceParsingStatus.FAILED) {
            placeJob.status = PlaceParsingStatus.PENDING
            placeJob.failureReason = null
            placeJob.attemptCount = 0
            placeJob.nextAttemptAt = clock.instant()
            eventPublisher.publishEvent(PlaceParsingJobRequestedEvent(placeJob.postId, clock.instant()))
        }
    }

    private fun validateOwnedGroups(userId: Long, groupIds: Set<Long>) {
        if (groupIds.isEmpty()) {
            throw InvalidGroupException(IllegalArgumentException("At least one group is required"))
        }
        val ownedGroupIds = groupJpaRepository.findAllByUserIdAndIdIn(userId, groupIds)
            .mapTo(mutableSetOf()) { requireNotNull(it.id) }
        if (ownedGroupIds != groupIds) {
            throw GroupNotFoundException()
        }
    }

    private fun PlaceEntity.toDomain(): Place = Place(
        providerReference = PlaceProviderReference(
            provider = provider,
            externalPlaceId = externalPlaceId,
        ),
        name = name,
        address = address,
        location = GeoPoint(latitude = latitude, longitude = longitude),
        city = city,
        category = category,
        phoneNumber = phoneNumber,
        id = id,
    )
}

private fun logSavedPostMapping(sourcePostId: Long, savedPostId: Long, created: Boolean, action: String) {
    postPersistenceEventLogger.info(
        ProcessingLogEvent(
            action = action,
            flow = "post-save",
            stage = "persist",
            outcome = "success",
            sourcePostId = sourcePostId,
            fields = mapOf(
                "saved_post.id" to savedPostId,
                "saved_post.created" to created,
            ),
        ),
    )
}

private fun initializeSavedPostPlaces(
    savedPost: UserSavedPostEntity,
    created: Boolean,
    sourcePostId: Long,
    savedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
    bookmarkRepository: UserPlaceBookmarkJpaRepository,
) {
    if (!created) {
        return
    }
    val savedPostId = requireNotNull(savedPost.id)
    savedPostPlaceRepository.insertAllFromPost(savedPostId, sourcePostId)
    savedPostPlaceRepository.findAllByUserSavedPostIdOrderBySequenceAsc(savedPostId).forEach { place ->
        bookmarkRepository.insertIgnoreWithMemo(
            userId = savedPost.userId,
            placeId = place.placeId,
            memo = savedPost.memo,
        )
    }
}

private val postPersistenceEventLogger = LoggerFactory.getLogger(PostPersistenceAdapter::class.java)
