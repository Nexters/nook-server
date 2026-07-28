package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.error.InvalidGroupException
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.post.port.CreatePostPort
import org.every.nook.api.application.post.port.CreatedPost
import org.every.nook.api.application.post.port.ExistingPost
import org.every.nook.api.application.post.port.FindExistingPostPort
import org.every.nook.api.application.post.port.FindPostPlaceParsingPort
import org.every.nook.api.application.post.port.PostPlaceParsingSnapshot
import org.every.nook.api.application.post.port.ReusePostPort
import org.every.nook.api.application.post.port.UpdatePostMemoPort
import org.every.nook.api.domain.place.GeoPoint
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceProviderReference
import org.every.nook.api.domain.post.Post
import org.every.nook.api.domain.post.PostSource
import org.every.nook.api.infrastructure.persistence.group.GroupJpaRepository
import org.every.nook.api.infrastructure.persistence.group.GroupPostEntity
import org.every.nook.api.infrastructure.persistence.group.GroupPostJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.place.UserPlaceBookmarkJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class PostPersistenceAdapter(
    private val postJpaRepository: PostJpaRepository,
    private val postMediaJpaRepository: PostMediaJpaRepository,
    private val postHashtagJpaRepository: PostHashtagJpaRepository,
    private val userSavedPostJpaRepository: UserSavedPostJpaRepository,
    private val placeParsingJobJpaRepository: PlaceParsingJobJpaRepository,
    private val postPlaceJpaRepository: PostPlaceJpaRepository,
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
        val parsingJob = placeParsingJobJpaRepository.findByPostId(requireNotNull(post.id))
            ?: return null
        return ExistingPost(parsingJob.status)
    }

    @Transactional
    override fun create(userId: Long, post: Post, memo: String?, groupIds: Set<Long>): CreatedPost {
        validateOwnedGroups(userId, groupIds)
        val postEntity = postJpaRepository.findBySourceForUpdate(
            post.source.type,
            post.source.externalPostId,
        ) ?: saveNewPost(post)
        val sourcePostId = requireNotNull(postEntity.id)
        val existingJob = placeParsingJobJpaRepository.findByPostId(sourcePostId)
        val parsingJob = existingJob
            ?: placeParsingJobJpaRepository.save(
                PlaceParsingJobEntity(
                    postId = sourcePostId,
                    status = PlaceParsingStatus.PENDING,
                ),
            )
        val userPost = findOrCreateUserPost(userId, sourcePostId, memo)
        addToGroups(requireNotNull(userPost.id), groupIds)
        if (existingJob == null) {
            eventPublisher.publishEvent(PlaceParsingJobRequestedEvent(sourcePostId))
        } else if (parsingJob.status == PlaceParsingStatus.FAILED) {
            restart(parsingJob)
        }

        return CreatedPost(
            postId = requireNotNull(userPost.id),
            placeParsingStatus = parsingJob.status,
        )
    }

    @Transactional
    override fun reuse(userId: Long, source: PostSource, memo: String?, groupIds: Set<Long>): CreatedPost {
        validateOwnedGroups(userId, groupIds)
        val post = requireNotNull(
            postJpaRepository.findBySourceForUpdate(source.type, source.externalPostId),
        )
        val sourcePostId = requireNotNull(post.id)
        val parsingJob = requireNotNull(placeParsingJobJpaRepository.findByPostId(sourcePostId))
        val userPost = findOrCreateUserPost(userId, sourcePostId, memo)
        addToGroups(requireNotNull(userPost.id), groupIds)
        if (parsingJob.status == PlaceParsingStatus.FAILED) {
            restart(parsingJob)
        }
        return CreatedPost(
            postId = requireNotNull(userPost.id),
            placeParsingStatus = parsingJob.status,
        )
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
        val parsingJob = placeParsingJobJpaRepository.findByPostId(userPost.postId) ?: return null
        val postPlaces = postPlaceJpaRepository.findAllByPostIdOrderBySequenceAsc(userPost.postId)
        val bookmarkedPlaceIds = if (postPlaces.isEmpty()) {
            emptySet()
        } else {
            userPlaceBookmarkJpaRepository
                .findAllByUserIdAndPlaceIdIn(userId, postPlaces.map { it.placeId })
                .mapTo(mutableSetOf()) { it.placeId }
        }
        val placesById = placeJpaRepository.findAllById(postPlaces.map { it.placeId })
            .associateBy { requireNotNull(it.id) }

        return PostPlaceParsingSnapshot(
            postId = postId,
            placeParsingStatus = parsingJob.status,
            failureReason = parsingJob.failureReason,
            places = postPlaces.mapNotNull { postPlace ->
                placesById[postPlace.placeId]?.toDomain()?.let { place ->
                    PostPlaceParsingSnapshot.RelatedPlace(
                        place = place,
                        bookmarked = postPlace.placeId in bookmarkedPlaceIds,
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
        val postId = requireNotNull(postEntity.id)

        postMediaJpaRepository.saveAll(
            post.media.map { media ->
                PostMediaEntity(
                    postId = postId,
                    mediaType = media.type,
                    mediaUrl = media.url,
                    sequence = media.sequence,
                )
            },
        )
        postHashtagJpaRepository.saveAll(
            post.hashtags.mapIndexed { sequence, hashtag ->
                PostHashtagEntity(
                    postId = postId,
                    hashtag = hashtag,
                    sequence = sequence,
                )
            },
        )

        return postEntity
    }

    private fun findOrCreateUserPost(userId: Long, postId: Long, memo: String?): UserSavedPostEntity =
        userSavedPostJpaRepository.findByUserIdAndPostId(userId, postId)
            ?: userSavedPostJpaRepository.save(
                UserSavedPostEntity(
                    userId = userId,
                    postId = postId,
                    memo = memo,
                ),
            )

    private fun addToGroups(userSavedPostId: Long, groupIds: Set<Long>) {
        val existingGroupIds = groupPostJpaRepository.findAllByUserSavedPostId(userSavedPostId)
            .mapTo(mutableSetOf(), GroupPostEntity::groupId)
        groupPostJpaRepository.saveAll(
            (groupIds - existingGroupIds).map { groupId ->
                GroupPostEntity(
                    groupId = groupId,
                    userSavedPostId = userSavedPostId,
                )
            },
        )
    }

    private fun restart(job: PlaceParsingJobEntity) {
        job.status = PlaceParsingStatus.PENDING
        job.failureReason = null
        job.attemptCount = 0
        job.nextAttemptAt = clock.instant()
        eventPublisher.publishEvent(PlaceParsingJobRequestedEvent(job.postId))
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
        category = category,
        phoneNumber = phoneNumber,
        id = id,
    )
}
