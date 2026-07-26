package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.application.save.port.FindSavedPostPlaceParsingPort
import org.every.nook.api.application.save.port.SaveInstagramPostPort
import org.every.nook.api.application.save.port.SavedInstagramPost
import org.every.nook.api.application.save.port.SavedPostPlaceParsingSnapshot
import org.every.nook.api.application.save.port.UpdateSavedPostPlaceBookmarkPort
import org.every.nook.api.domain.place.GeoPoint
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.place.PlaceProviderReference
import org.every.nook.api.domain.post.Post
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagEntity
import org.every.nook.api.infrastructure.persistence.post.PostHashtagJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostMediaEntity
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SavedPostPersistenceAdapter(
    private val postJpaRepository: PostJpaRepository,
    private val postMediaJpaRepository: PostMediaJpaRepository,
    private val postHashtagJpaRepository: PostHashtagJpaRepository,
    private val userSavedPostJpaRepository: UserSavedPostJpaRepository,
    private val placeParsingJobJpaRepository: PlaceParsingJobJpaRepository,
    private val postPlaceJpaRepository: PostPlaceJpaRepository,
    private val placeJpaRepository: PlaceJpaRepository,
) : SaveInstagramPostPort,
    FindSavedPostPlaceParsingPort,
    UpdateSavedPostPlaceBookmarkPort {
    @Transactional
    override fun save(userId: Long, post: Post): SavedInstagramPost {
        val postEntity = saveNewPost(post)
        val postId = requireNotNull(postEntity.id)
        val parsingJob = placeParsingJobJpaRepository.findByPostId(postId)
            ?: placeParsingJobJpaRepository.save(
                PlaceParsingJobEntity(
                    postId = postId,
                    status = PlaceParsingStatus.PENDING,
                ),
            )
        val savedPost = userSavedPostJpaRepository.save(
            UserSavedPostEntity(
                userId = userId,
                postId = postId,
            ),
        )

        return SavedInstagramPost(
            savedPostId = requireNotNull(savedPost.id),
            postId = postId,
            placeParsingStatus = parsingJob.status,
        )
    }

    @Transactional(readOnly = true)
    override fun find(userId: Long, savedPostId: Long): SavedPostPlaceParsingSnapshot? {
        val savedPost = userSavedPostJpaRepository.findByIdAndUserId(savedPostId, userId) ?: return null
        val parsingJob = placeParsingJobJpaRepository.findByPostId(savedPost.postId) ?: return null
        val postPlaces = postPlaceJpaRepository.findAllByPostIdOrderBySequenceAsc(savedPost.postId)
        val placesById = placeJpaRepository.findAllById(postPlaces.map { it.placeId })
            .associateBy { requireNotNull(it.id) }

        return SavedPostPlaceParsingSnapshot(
            savedPostId = savedPostId,
            postId = savedPost.postId,
            placeParsingStatus = parsingJob.status,
            failureReason = parsingJob.failureReason,
            places = postPlaces.mapNotNull { postPlace ->
                placesById[postPlace.placeId]?.toDomain()?.let { place ->
                    SavedPostPlaceParsingSnapshot.SavedPlace(
                        place = place,
                        bookmarked = postPlace.bookmarked,
                    )
                }
            },
        )
    }

    @Transactional
    override fun update(userId: Long, savedPostId: Long, placeId: Long, bookmarked: Boolean): Boolean {
        val savedPost = userSavedPostJpaRepository.findByIdAndUserId(savedPostId, userId) ?: return false
        val postPlace = postPlaceJpaRepository.findByPostIdAndPlaceId(savedPost.postId, placeId) ?: return false
        postPlace.bookmarked = bookmarked
        return true
    }

    private fun saveNewPost(post: Post): PostEntity {
        val postEntity = postJpaRepository.save(
            PostEntity(
                sourceType = post.source.type,
                externalPostId = post.source.externalPostId,
                canonicalUrl = post.canonicalUrl,
                authorIdentifier = post.authorIdentifier,
                title = post.title,
                memo = post.memo,
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
