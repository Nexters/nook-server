package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPage
import org.every.nook.api.application.admin.AdminPlaceTagCatalogPort
import org.every.nook.api.application.admin.AdminPlaceTagDefinition
import org.every.nook.api.domain.place.PlaceTagCategory
import org.every.nook.api.infrastructure.persistence.place.PlaceTagCatalogEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceTagCatalogJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Component
class AdminPlaceTagCatalogPersistenceAdapter(
    private val repository: PlaceTagCatalogJpaRepository,
    private val auditLogPort: AdminAuditLogPort,
    private val objectMapper: ObjectMapper,
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
                targetId = command.tagCode.name,
                reason = command.reason,
                beforeValue = objectMapper.writeValueAsString(before),
                afterValue = objectMapper.writeValueAsString(entity.editableValue()),
                requestId = command.requestId,
            ),
        )
        return entity.toAdminView()
    }

    private fun PlaceTagCatalogEntity.toAdminView() = AdminPlaceTagDefinition(
        id = tagCode.name,
        tagCode = tagCode.name,
        category = category.name,
        displayName = displayName,
        matchingKeywords = matchingKeywords,
        enabled = enabled,
        sortOrder = sortOrder,
        updatedAt = updatedAt,
    )

    private fun PlaceTagCatalogEntity.editableValue() = mapOf(
        "tagCode" to tagCode.name,
        "category" to category.name,
        "displayName" to displayName,
        "matchingKeywords" to matchingKeywords,
        "enabled" to enabled,
        "sortOrder" to sortOrder,
    )
}
