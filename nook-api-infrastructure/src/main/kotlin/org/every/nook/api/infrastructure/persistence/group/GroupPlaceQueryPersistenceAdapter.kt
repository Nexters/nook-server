package org.every.nook.api.infrastructure.persistence.group

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.every.nook.api.application.group.GroupPlacePage
import org.every.nook.api.application.group.GroupPlaceSummary
import org.every.nook.api.application.group.port.GroupPlaceQueryPort
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GroupPlaceQueryPersistenceAdapter(
    private val savedPostRepository: UserSavedPostJpaRepository,
    private val groupRepository: GroupJpaRepository,
    private val memberRepository: MemberJpaRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : GroupPlaceQueryPort {
    @Transactional(readOnly = true)
    override fun findPlaces(userId: Long, groupId: Long, page: Int, size: Int): GroupPlacePage? {
        val group = groupRepository.findByIdAndUserId(groupId, userId) ?: return null
        val owner = memberRepository.findById(group.userId).orElse(null) ?: return null
        val pageable = PageRequest.of(page, size)
        val places = savedPostRepository.findDistinctPlacesByUserIdAndGroupId(userId, groupId, pageable)

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
                    tags = projection.representativeTags.toDisplayTags(),
                )
            },
            page = places.number,
            size = places.size,
            totalElements = places.totalElements,
            totalPages = places.totalPages,
            hasNext = places.hasNext(),
        )
    }

    private fun String?.toDisplayTags(): List<String> = if (this.isNullOrBlank()) {
        emptyList()
    } else {
        objectMapper.readValue(this, Array<String>::class.java).map { PlaceTag.valueOf(it).displayName }
    }
}
