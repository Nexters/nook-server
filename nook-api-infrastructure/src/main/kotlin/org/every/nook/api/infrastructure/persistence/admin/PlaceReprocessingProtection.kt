package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.PlaceReprocessingSource
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class PlaceReprocessingProtection(private val audit: AdminAuditLogJpaRepository, private val mapper: ObjectMapper) {
    fun fields(placeId: Long): Set<String> = audit.findAllByTargetTypeAndTargetIdAndActionIn(
        "PLACE",
        placeId.toString(),
        listOf("PLACE_CREATED", "PLACE_BASIC_INFORMATION_UPDATED"),
    ).flatMap { entry ->
        val before = entry.beforeValue?.let(mapper::readTree)
        val after = entry.afterValue?.let(mapper::readTree)
        PROTECTED_FIELDS.filter { field ->
            val old = before?.get(field)
            val value = after?.get(field)
            if (entry.action == "PLACE_CREATED") {
                value != null && !value.isNull && (!value.isArray || !value.isEmpty)
            } else {
                // Unknown historical snapshots are protected conservatively.
                before == null || after == null || old != value
            }
        }
    }.toSet()

    private companion object {
        val PROTECTED_FIELDS = setOf("thumbnailUrl", "photoUrls", "representativeTags")
    }
}

internal data class ProtectedPlaceFields(val photos: Boolean, val tags: Boolean)

internal fun protectedFields(place: PlaceEntity, source: PlaceReprocessingSource, manual: Set<String>) =
    ProtectedPlaceFields(
        photos = "thumbnailUrl" in manual || "photoUrls" in manual ||
            place.thumbnailUrl != source.thumbnailUrl || place.photoUrls != source.photoUrls,
        tags = "representativeTags" in manual || place.representativeTags != source.representativeTags,
    )
