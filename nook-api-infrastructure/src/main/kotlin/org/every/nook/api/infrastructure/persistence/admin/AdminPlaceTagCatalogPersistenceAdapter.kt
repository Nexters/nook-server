package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPage
import org.every.nook.api.application.admin.AdminPlaceTagCatalogPort
import org.every.nook.api.application.admin.AdminPlaceTagDefinition
import org.every.nook.api.domain.place.PlaceTagCategory
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceTagCatalogEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceTagCatalogJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PostPlaceTagEntity
import org.every.nook.api.infrastructure.persistence.place.PostPlaceTagJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Component
class AdminPlaceTagCatalogPersistenceAdapter(
    private val repository: PlaceTagCatalogJpaRepository,
    private val auditLogPort: AdminAuditLogPort,
    private val objectMapper: ObjectMapper,
    private val placeRepository: PlaceJpaRepository,
    private val postPlaceTagRepository: PostPlaceTagJpaRepository,
) : AdminPlaceTagCatalogPort {
    @Transactional(readOnly = true)
    override fun list(
        category: PlaceTagCategory?,
        enabled: Boolean?,
        offset: Int,
        limit: Int,
    ): AdminPage<AdminPlaceTagDefinition> {
        val entries = repository.findAllByOrderBySortOrderAscTagCodeAsc().filter { entity ->
            (category == null || entity.category == category) && (enabled == null || entity.enabled == enabled)
        }
        return AdminPage(
            items = entries.drop(offset).take(limit).map { it.toAdminView() },
            total = entries.size.toLong(),
        )
    }

    @Transactional
    override fun update(command: AdminPlaceTagCatalogPort.UpdateCommand): AdminPlaceTagDefinition? {
        val entity = repository.findByTagCode(command.tagCode) ?: return null
        val before = entity.editableValue()
        entity.update(
            category = command.category,
            displayName = command.displayName,
            matchingKeywords = command.matchingKeywords,
            enabled = command.enabled,
            sortOrder = command.sortOrder,
        )
        auditLogPort.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "PLACE_TAG_CATALOG_UPDATED",
                targetType = "PLACE_TAG",
                targetId = command.tagCode,
                reason = command.reason,
                beforeValue = objectMapper.writeValueAsString(before),
                afterValue = objectMapper.writeValueAsString(entity.editableValue()),
                requestId = command.requestId,
            ),
        )
        return entity.toAdminView()
    }

    @Transactional
    override fun create(command: AdminPlaceTagCatalogPort.CreateCommand): AdminPlaceTagDefinition {
        val sortOrder = repository.findAllByOrderBySortOrderAscTagCodeAsc().maxOfOrNull { it.sortOrder }?.plus(1) ?: 1
        val entity = repository.save(
            PlaceTagCatalogEntity(
                tagCode = command.tagCode,
                category = command.category,
                displayName = command.displayName,
                matchingKeywords = command.matchingKeywords,
                enabled = true,
                sortOrder = sortOrder,
            ),
        )
        appendAudit(
            command.actor,
            command.reason,
            command.requestId,
            "PLACE_TAG_CATALOG_CREATED",
            entity.tagCode,
            null,
            entity.editableValue(),
        )
        return entity.toAdminView()
    }

    @Transactional
    override fun reorder(command: AdminPlaceTagCatalogPort.ReorderCommand) {
        val entities = repository.findAllByOrderBySortOrderAscTagCodeAsc()
        require(command.tagCodes.toSet() == entities.map(PlaceTagCatalogEntity::tagCode).toSet()) {
            "Every place tag must be included when reordering"
        }
        val before = entities.associate { it.tagCode to it.sortOrder }
        val orderByCode = command.tagCodes.withIndex().associate { (index, code) -> code to index + 1 }
        entities.forEach { it.sortOrder = orderByCode.getValue(it.tagCode) }
        appendAudit(
            command.actor,
            command.reason,
            command.requestId,
            "PLACE_TAG_CATALOG_REORDERED",
            "ALL",
            before,
            orderByCode,
        )
    }

    @Transactional
    override fun deleteAndReplace(command: AdminPlaceTagCatalogPort.DeleteCommand): Boolean {
        val source = repository.findByTagCode(command.tagCode) ?: return false
        val replacement = repository.findByTagCode(command.replacementTagCode)?.takeIf { it.enabled } ?: return false
        val replacementKeys = postPlaceTagRepository.findAllByTag(replacement.tagCode)
            .map { it.postId to it.placeId }
            .toMutableSet()
        postPlaceTagRepository.findAllByTag(source.tagCode).forEach { attachedTag ->
            postPlaceTagRepository.delete(attachedTag)
            if (replacementKeys.add(attachedTag.postId to attachedTag.placeId)) {
                postPlaceTagRepository.save(
                    PostPlaceTagEntity(
                        postId = attachedTag.postId,
                        placeId = attachedTag.placeId,
                        tag = replacement.tagCode,
                        confidence = attachedTag.confidence,
                        evidenceSource = attachedTag.evidenceSource,
                        evidenceText = attachedTag.evidenceText,
                    ),
                )
            }
        }
        val catalog = repository.findAllByOrderBySortOrderAscTagCodeAsc()
            .filterNot { it.tagCode == source.tagCode }
            .map(PlaceTagCatalogEntity::toDefinition)
        placeRepository.findAll().filter { source.tagCode in it.representativeTags }.forEach { place ->
            place.updateRepresentativeTags(
                place.representativeTags.map { if (it == source.tagCode) replacement.tagCode else it },
                catalog,
            )
        }
        val before = source.editableValue()
        repository.delete(source)
        appendAudit(
            command.actor,
            command.reason,
            command.requestId,
            "PLACE_TAG_CATALOG_DELETED",
            source.tagCode,
            before,
            mapOf("replacementTagCode" to replacement.tagCode),
        )
        return true
    }

    private fun appendAudit(
        actor: AdminActor,
        reason: String,
        requestId: String?,
        action: String,
        targetId: String,
        before: Any?,
        after: Any?,
    ) {
        auditLogPort.append(
            AdminAuditLogPort.Entry(
                actor = actor,
                action = action,
                targetType = "PLACE_TAG",
                targetId = targetId,
                reason = reason,
                beforeValue = before?.let(objectMapper::writeValueAsString),
                afterValue = after?.let(objectMapper::writeValueAsString),
                requestId = requestId,
            ),
        )
    }

    private fun PlaceTagCatalogEntity.toAdminView() = AdminPlaceTagDefinition(
        id = tagCode,
        tagCode = tagCode,
        category = category.name,
        displayName = displayName,
        matchingKeywords = matchingKeywords,
        enabled = enabled,
        sortOrder = sortOrder,
        updatedAt = updatedAt,
    )

    private fun PlaceTagCatalogEntity.editableValue() = mapOf(
        "tagCode" to tagCode,
        "category" to category.name,
        "displayName" to displayName,
        "matchingKeywords" to matchingKeywords,
        "enabled" to enabled,
        "sortOrder" to sortOrder,
    )
}
