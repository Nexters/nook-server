package org.every.nook.api.infrastructure.persistence.place

import com.fasterxml.jackson.databind.ObjectMapper
import org.every.nook.api.application.place.StoredPlaceSearchView
import org.every.nook.api.application.place.port.SearchAllStoredPlacesPort
import org.every.nook.api.application.place.port.SearchMyStoredPlacesPort
import org.every.nook.api.domain.place.PlaceTag
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StoredPlaceSearchPersistenceAdapter(
    private val repository: StoredPlaceSearchJpaRepository,
    private val objectMapper: ObjectMapper,
) : SearchAllStoredPlacesPort,
    SearchMyStoredPlacesPort {
    @Transactional(readOnly = true)
    override fun searchAll(userId: Long, keyword: String, offset: Int, limit: Int): List<StoredPlaceSearchView> =
        repository.searchAll(userId, keyword, offset, limit).map { it.toView() }

    @Transactional(readOnly = true)
    override fun searchMine(userId: Long, keyword: String, offset: Int, limit: Int): List<StoredPlaceSearchView> =
        repository.searchMine(userId, keyword, offset, limit).map { it.toView() }

    private fun StoredPlaceSearchProjection.toView(): StoredPlaceSearchView = StoredPlaceSearchView(
        id = id,
        name = name,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
        thumbnailUrl = thumbnailUrl,
        tags = representativeTags.toDisplayTags(),
        bookmarked = bookmarked,
    )

    private fun String?.toDisplayTags(): List<String> = if (isNullOrBlank()) {
        emptyList()
    } else {
        objectMapper.readValue(this, Array<String>::class.java).map { PlaceTag.valueOf(it).displayName }
    }
}
