package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.place.PlaceTagDefinition

fun interface PlaceTagCatalogQueryPort {
    fun findAll(): List<PlaceTagDefinition>
}

class PlaceTagCatalogSnapshot(definitions: List<PlaceTagDefinition>) {
    val enabledDefinitions: List<PlaceTagDefinition> = definitions
        .filter(PlaceTagDefinition::enabled)
        .sortedBy(PlaceTagDefinition::sortOrder)
    private val enabledByTag = enabledDefinitions.associateBy(PlaceTagDefinition::tag)

    fun displayNames(tags: Iterable<PlaceTag>): List<String> = tags.mapNotNull { enabledByTag[it]?.displayName }
}

fun PlaceTagCatalogQueryPort.snapshot(): PlaceTagCatalogSnapshot = PlaceTagCatalogSnapshot(findAll())

fun interface PlaceTagSourcePort {
    fun find(postId: Long): PlaceTagSource?
}

data class PlaceTagSource(val body: String?, val hashtags: List<String>)

fun interface PlaceTagUpdatePort {
    fun replace(postId: Long, placeId: Long, tags: List<InferredPlaceTag>)
}

fun interface PlaceTagBackfillPort {
    fun findAll(): List<PlaceTagsRequestedEvent>
}
