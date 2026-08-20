package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.GroupPlacePage
import org.every.nook.api.application.group.GroupPlaceSummary
import org.every.nook.api.application.group.port.GroupPlaceQueryPort
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.application.place.PlaceTagCatalogSnapshot
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.place.snapshot
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.every.nook.api.infrastructure.persistence.place.effectiveThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

@Component
class GroupPlaceQueryPersistenceAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val groupRepository: GroupJpaRepository,
    private val memberRepository: MemberJpaRepository,
    private val tagCatalogPort: PlaceTagCatalogQueryPort = PlaceTagCatalogQueryPort { PlaceTag.defaultDefinitions },
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : GroupPlaceQueryPort {
    @Transactional(readOnly = true)
    override fun findPlaces(userId: Long, groupId: Long, page: Int, size: Int): GroupPlacePage? {
        val group = groupRepository.findByIdAndUserId(groupId, userId) ?: return null
        val owner = memberRepository.findById(group.userId).orElse(null) ?: return null
        val pageable = PageRequest.of(page, size)
        val places = savedPostRepository.findDistinctPlacesByUserIdAndGroupId(userId, groupId, pageable)
        val tagCatalog = tagCatalogPort.snapshot()

        return GroupPlacePage(
            ownerNickname = owner.nickname,
            items = places.content.map { projection ->
                GroupPlaceSummary(
                    id = projection.id,
                    name = projection.name,
                    city = projection.city,
                    address = projection.address,
                    category = projection.category,
                    latitude = projection.latitude,
                    longitude = projection.longitude,
                    thumbnailUrl = projection.thumbnailUrl,
                    thumbnailParsingStatus = PlaceThumbnailParsingStatusView.from(
                        effectiveThumbnailParsingStatus(
                            projection.thumbnailUrl,
                            projection.thumbnailParsingStatus?.let(PlaceThumbnailParsingStatus::valueOf),
                        ),
                    ),
                    tags = projection.representativeTags.toDisplayTags(tagCatalog),
                )
            },
            page = places.number,
            size = places.size,
            totalElements = places.totalElements,
            totalPages = places.totalPages,
            hasNext = places.hasNext(),
        )
    }

    private fun String?.toDisplayTags(tagCatalog: PlaceTagCatalogSnapshot): List<String> = if (this.isNullOrBlank()) {
        emptyList()
    } else {
        tagCatalog.displayNames(objectMapper.readValue(this, Array<String>::class.java).asList())
    }
}
