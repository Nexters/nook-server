package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.SavedPlaceSearchItemView
import org.every.nook.api.application.place.SavedPlaceSearchPageView
import org.every.nook.api.application.place.port.SavedPlaceSearchPort
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SavedPlaceSearchPersistenceAdapter(private val bookmarkRepository: UserPlaceBookmarkJpaRepository) :
    SavedPlaceSearchPort {
    @Transactional(readOnly = true)
    override fun search(userId: Long, keyword: String, page: Int, size: Int): SavedPlaceSearchPageView {
        val result = bookmarkRepository.searchSavedPlaces(
            userId = userId,
            pattern = keyword.toLikePattern(),
            pageable = PageRequest.of(page, size),
        )
        return SavedPlaceSearchPageView(
            items = result.content.map { row ->
                SavedPlaceSearchItemView(
                    id = row.id,
                    name = row.name,
                    address = row.address,
                    category = row.category,
                )
            },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext(),
        )
    }

    private fun String.toLikePattern(): String = "%" +
        replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_") +
        "%"
}
