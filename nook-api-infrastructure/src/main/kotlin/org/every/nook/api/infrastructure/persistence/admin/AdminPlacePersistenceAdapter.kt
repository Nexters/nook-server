package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminLinkedPost
import org.every.nook.api.application.admin.AdminPage
import org.every.nook.api.application.admin.AdminPlaceCorrectionPort
import org.every.nook.api.application.admin.AdminPlaceDetail
import org.every.nook.api.application.admin.AdminPlaceQueryPort
import org.every.nook.api.application.admin.AdminPlaceSummary
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Component
class AdminPlacePersistenceAdapter(
    private val placeRepository: PlaceJpaRepository,
    private val postRepository: PostJpaRepository,
    private val postPlaceRepository: PostPlaceJpaRepository,
    private val savedPostPlaceRepository: UserSavedPostPlaceJpaRepository,
    private val auditLogPort: AdminAuditLogPort,
    private val objectMapper: ObjectMapper,
) : AdminPlaceQueryPort,
    AdminPlaceCorrectionPort {
    @Transactional(readOnly = true)
    override fun search(query: String, limit: Int): List<AdminPlaceSummary> = placeRepository.findAll().asSequence()
        .filter { it.name.contains(query, true) || it.address.contains(query, true) }
        .take(limit)
        .map { it.toSummary(includeImpact = false) }
        .toList()

    @Transactional(readOnly = true)
    override fun listPlaces(query: String?, offset: Int, limit: Int): AdminPage<AdminPlaceSummary> {
        val filtered = placeRepository.findAll().asSequence()
            .filter { place ->
                query == null || place.name.contains(query, true) || place.address.contains(query, true) ||
                    place.externalPlaceId.contains(query, true)
            }
            .sortedByDescending { it.createdAt }
            .toList()
        return AdminPage(
            items = filtered.drop(offset).take(limit).map { it.toSummary(includeImpact = true) },
            total = filtered.size.toLong(),
        )
    }

    @Transactional(readOnly = true)
    override fun findPlace(placeId: Long): AdminPlaceDetail? {
        val place = placeRepository.findById(placeId).orElse(null) ?: return null
        val mappings = postPlaceRepository.findAllByPlaceId(placeId)
        val postsById = postRepository.findAllById(mappings.map { it.postId })
            .associateBy { requireNotNull(it.id) }
        return AdminPlaceDetail(
            id = placeId,
            name = place.name,
            address = place.address,
            provider = place.provider,
            externalPlaceId = place.externalPlaceId,
            linkedPostCount = mappings.map { it.postId }.distinct().size.toLong(),
            affectedUserCount = savedPostPlaceRepository.countDistinctActiveUsersByPlaceId(placeId),
            posts = mappings.distinctBy { it.postId }.mapNotNull { mapping ->
                postsById[mapping.postId]?.let { post ->
                    AdminLinkedPost(
                        id = requireNotNull(post.id),
                        title = post.title,
                        authorIdentifier = post.authorIdentifier,
                        canonicalUrl = post.canonicalUrl,
                        createdAt = post.createdAt,
                    )
                }
            }.sortedByDescending { it.createdAt },
        )
    }

    @Transactional
    override fun update(command: AdminPlaceCorrectionPort.UpdateCommand): AdminPlaceDetail? {
        val place = placeRepository.findByIdForUpdate(command.placeId) ?: return null
        val before = mapOf("name" to place.name, "address" to place.address)
        place.updateBasicInformation(command.name, command.address)
        val after = mapOf("name" to place.name, "address" to place.address)
        auditLogPort.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "PLACE_BASIC_INFORMATION_UPDATED",
                targetType = "PLACE",
                targetId = command.placeId.toString(),
                reason = command.reason,
                beforeValue = objectMapper.writeValueAsString(before),
                afterValue = objectMapper.writeValueAsString(after),
                requestId = command.requestId,
            ),
        )
        return findPlace(command.placeId)
    }

    private fun PlaceEntity.toSummary(includeImpact: Boolean): AdminPlaceSummary {
        val placeId = requireNotNull(id)
        return AdminPlaceSummary(
            id = placeId,
            name = name,
            address = address,
            provider = provider,
            externalPlaceId = externalPlaceId,
            linkedPostCount = if (includeImpact) linkedPostCount(placeId) else 0,
            affectedUserCount = if (includeImpact) {
                savedPostPlaceRepository.countDistinctActiveUsersByPlaceId(placeId)
            } else {
                0
            },
        )
    }

    private fun linkedPostCount(placeId: Long): Long =
        postPlaceRepository.findAllByPlaceId(placeId).map { it.postId }.distinct().size.toLong()
}
