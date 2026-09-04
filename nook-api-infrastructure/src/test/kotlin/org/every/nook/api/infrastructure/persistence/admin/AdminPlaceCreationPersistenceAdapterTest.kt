package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPlaceCreationPort
import org.every.nook.api.application.admin.DuplicateAdminPlaceException
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostPlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.save.UserSavedPostPlaceJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AdminPlaceCreationPersistenceAdapterTest {
    @Test
    fun `rejects an existing place with normalized name address and coordinates`() {
        val placeRepository = mock(PlaceJpaRepository::class.java)
        val latitude = BigDecimal("37.5120741")
        val longitude = BigDecimal("127.0590297")
        `when`(placeRepository.findAllByLatitudeAndLongitude(latitude, longitude)).thenReturn(
            listOf(
                PlaceEntity(
                    provider = "KAKAO",
                    externalPlaceId = "kakao-id",
                    name = "누크 카페",
                    address = "서울특별시 성동구 아차산로 1",
                    latitude = latitude,
                    longitude = longitude,
                ),
            ),
        )
        val adapter = AdminPlacePersistenceAdapter(
            placeRepository = placeRepository,
            postRepository = mock(PostJpaRepository::class.java),
            postPlaceRepository = mock(PostPlaceJpaRepository::class.java),
            savedPostPlaceRepository = mock(UserSavedPostPlaceJpaRepository::class.java),
            auditLogPort = mock(AdminAuditLogPort::class.java),
            objectMapper = jacksonObjectMapper(),
        )

        assertFailsWith<DuplicateAdminPlaceException> {
            adapter.create(
                AdminPlaceCreationPort.CreateCommand(
                    provider = "MANUAL",
                    externalPlaceId = "manual-id",
                    name = "누크카페",
                    address = "서울특별시 성동구 아차산로1",
                    latitude = latitude,
                    longitude = longitude,
                    actor = AdminActor("subject", "admin@everynook.co.kr"),
                    reason = "신규 등록",
                    requestId = null,
                ),
            )
        }
    }
}
