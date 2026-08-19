package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminAuditLog
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminMappedPlace
import org.every.nook.api.application.admin.AdminPage
import org.every.nook.api.application.admin.AdminPostDetail
import org.every.nook.api.application.admin.AdminPostPlaceCorrectionPort
import org.every.nook.api.application.admin.AdminPostQueryPort
import org.every.nook.api.application.admin.AdminPostSummary
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceEntity
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Component
class AdminPersistenceAdapter(
    private val postRepository: PostJpaRepository,
    private val contentJobRepository: PostContentParsingJobJpaRepository,
    private val placeJobRepository: PlaceParsingJobJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
    private val placeRepository: PlaceJpaRepository,
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val reviewRepository: PostPlaceReviewJpaRepository,
    private val auditRepository: AdminAuditLogJpaRepository,
    private val objectMapper: ObjectMapper,
) : AdminPostQueryPort,
    AdminPostPlaceCorrectionPort,
    AdminAuditLogPort {
    @Transactional(readOnly = true)
    override fun listPosts(
        query: String?,
        parsingStatus: String?,
        offset: Int,
        limit: Int,
    ): AdminPage<AdminPostSummary> {
        val posts = postRepository.findAll().asSequence()
            .filter { post ->
                query == null || listOf(post.title, post.authorIdentifier, post.canonicalUrl)
                    .any { value -> value?.contains(query, ignoreCase = true) == true }
            }
            .sortedByDescending { it.createdAt }
            .toList()
        val contentJobs = contentJobRepository.findAllByPostIdIn(posts.mapNotNull { it.id }).associateBy { it.postId }
        val placeJobs = placeJobRepository.findAllByPostIdIn(posts.mapNotNull { it.id }).associateBy { it.postId }
        val filtered = posts.filter { post ->
            parsingStatus == null || placeJobs[requireNotNull(post.id)]?.status?.name == parsingStatus ||
                contentJobs[post.id]?.status?.name == parsingStatus
        }
        val pagePosts = filtered.drop(offset).take(limit)
        val postIds = pagePosts.map { requireNotNull(it.id) }
        val placeCounts = postPlaceRepository.findAllByPostIdInOrderByPostIdAscSequenceAsc(postIds)
            .groupingBy { it.postId }.eachCount()
        val reviewedIds = reviewRepository.findAllByPostIdIn(postIds).mapTo(mutableSetOf()) { it.postId }
        return AdminPage(
            items = pagePosts.map { post ->
                val postId = requireNotNull(post.id)
                AdminPostSummary(
                    id = postId,
                    canonicalUrl = post.canonicalUrl,
                    authorIdentifier = post.authorIdentifier,
                    title = post.title,
                    contentParsingStatus = contentJobs[postId]?.status?.name ?: "PENDING",
                    placeParsingStatus = placeJobs[postId]?.status?.name,
                    placeCount = placeCounts[postId] ?: 0,
                    savedUserCount = savedPostRepository.findDistinctUserIdsByPostId(postId).size.toLong(),
                    mappingReviewed = postId in reviewedIds,
                    createdAt = post.createdAt,
                )
            },
            total = filtered.size.toLong(),
        )
    }

    @Transactional(readOnly = true)
    override fun find(postId: Long): AdminPostDetail? {
        val post = postRepository.findById(postId).orElse(null) ?: return null
        val places = mappedPlaces(postId)
        val contentJob = contentJobRepository.findByPostId(postId)
        val placeJob = placeJobRepository.findByPostId(postId)
        return AdminPostDetail(
            id = postId,
            canonicalUrl = post.canonicalUrl,
            authorIdentifier = post.authorIdentifier,
            title = post.title,
            body = post.body,
            sourceLocationTag = post.sourceLocationTag,
            contentParsingStatus = contentJob?.status?.name ?: "PENDING",
            contentParsingFailureReason = contentJob?.failureReason,
            placeParsingStatus = placeJob?.status?.name,
            placeParsingFailureReason = placeJob?.failureReason,
            savedUserCount = savedPostRepository.findDistinctUserIdsByPostId(postId).size.toLong(),
            mappingReviewed = reviewRepository.existsByPostId(postId),
            places = places,
        )
    }

    @Transactional
    override fun replace(command: AdminPostPlaceCorrectionPort.ReplaceCommand): AdminPostDetail? {
        postRepository.findByIdForUpdate(command.postId) ?: return null
        val places = placeRepository.findAllById(command.placeIds)
        require(places.size == command.placeIds.size) { "One or more places do not exist" }
        val before = mappedPlaces(command.postId)
        postPlaceRepository.deleteAll(postPlaceRepository.findAllByPostIdOrderBySequenceAsc(command.postId))
        postPlaceRepository.flush()
        postPlaceRepository.saveAll(
            command.placeIds.mapIndexed { sequence, placeId -> PostPlaceEntity(command.postId, placeId, sequence) },
        )
        val review = reviewRepository.findByPostId(command.postId)
        if (review == null) {
            reviewRepository.save(
                PostPlaceReviewEntity(command.postId, command.actor.subject, command.actor.email),
            )
        } else {
            review.reviewedBy(command.actor.subject, command.actor.email)
        }
        val after = mappedPlaces(command.postId)
        append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "POST_PLACE_MAPPING_REPLACED",
                targetType = "POST",
                targetId = command.postId.toString(),
                reason = command.reason,
                beforeValue = objectMapper.writeValueAsString(before),
                afterValue = objectMapper.writeValueAsString(after),
                requestId = command.requestId,
            ),
        )
        return find(command.postId)
    }

    @Transactional(readOnly = true)
    override fun listAuditLogs(
        targetType: String?,
        targetId: String?,
        offset: Int,
        limit: Int,
    ): AdminPage<AdminAuditLog> {
        val page = PageRequest.of(offset / limit, limit)
        val result = when {
            targetType != null && targetId != null ->
                auditRepository.findAllByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, page)

            targetType != null -> auditRepository.findAllByTargetTypeOrderByCreatedAtDesc(targetType, page)

            else -> auditRepository.findAllByOrderByCreatedAtDesc(page)
        }
        return AdminPage(result.content.map(::toView), result.totalElements)
    }

    override fun append(entry: AdminAuditLogPort.Entry) {
        auditRepository.save(
            AdminAuditLogEntity(
                actorSubject = entry.actor.subject.take(AdminAuditLogEntity.ACTOR_SUBJECT_LENGTH),
                actorEmail = entry.actor.email.take(AdminAuditLogEntity.ACTOR_EMAIL_LENGTH),
                action = entry.action.take(AdminAuditLogEntity.ACTION_LENGTH),
                targetType = entry.targetType.take(AdminAuditLogEntity.TARGET_TYPE_LENGTH),
                targetId = entry.targetId.take(AdminAuditLogEntity.TARGET_ID_LENGTH),
                reason = entry.reason.take(AdminAuditLogEntity.REASON_LENGTH),
                beforeValue = entry.beforeValue,
                afterValue = entry.afterValue,
                requestId = entry.requestId?.take(AdminAuditLogEntity.REQUEST_ID_LENGTH),
            ),
        )
    }

    private fun mappedPlaces(postId: Long): List<AdminMappedPlace> {
        val mappings = postPlaceRepository.findAllByPostIdOrderBySequenceAsc(postId)
        val places = placeRepository.findAllById(mappings.map { it.placeId }).associateBy { requireNotNull(it.id) }
        return mappings.mapNotNull { mapping ->
            places[mapping.placeId]?.let { place ->
                AdminMappedPlace(
                    id = requireNotNull(place.id),
                    name = place.name,
                    address = place.address,
                    provider = place.provider,
                    externalPlaceId = place.externalPlaceId,
                    sequence = mapping.sequence,
                )
            }
        }
    }

    private fun toView(entity: AdminAuditLogEntity) = AdminAuditLog(
        id = requireNotNull(entity.id),
        actorSubject = entity.actorSubject,
        actorEmail = entity.actorEmail,
        action = entity.action,
        targetType = entity.targetType,
        targetId = entity.targetId,
        reason = entity.reason,
        beforeValue = entity.beforeValue,
        afterValue = entity.afterValue,
        requestId = entity.requestId,
        createdAt = entity.createdAt,
    )
}
