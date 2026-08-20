package org.every.nook.api.infrastructure.persistence.place

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceTagBackfillPersistenceAdapterTest {
    @Test
    fun `builds one event containing every place for each source post`() {
        val tagRepository = mock(PostPlaceTagJpaRepository::class.java)
        val placeRepository = mock(PlaceJpaRepository::class.java)
        val firstTarget = target(postId = 11, placeId = 21)
        val secondTarget = target(postId = 11, placeId = 22)
        val firstPlace = place(id = 21, name = "첫 장소")
        val secondPlace = place(id = 22, name = "둘째 장소")
        `when`(tagRepository.findAllBackfillTargets()).thenReturn(listOf(firstTarget, secondTarget))
        `when`(placeRepository.findAllById(listOf(21, 22))).thenReturn(listOf(firstPlace, secondPlace))

        val events = PlaceTagBackfillPersistenceAdapter(tagRepository, placeRepository).findAll()

        assertEquals(1, events.size)
        assertEquals(listOf("첫 장소", "둘째 장소"), events.single().places.map { it.candidate.name })
    }

    private fun target(postId: Long, placeId: Long) = mock(PlaceTagBackfillProjection::class.java).also { target ->
        `when`(target.postId).thenReturn(postId)
        `when`(target.placeId).thenReturn(placeId)
    }

    private fun place(id: Long, name: String) = PlaceEntity(
        provider = "KAKAO",
        externalPlaceId = id.toString(),
        name = name,
        address = "서울",
        latitude = BigDecimal("37.0"),
        longitude = BigDecimal("127.0"),
    ).also { place ->
        val idField = PlaceEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(place, id)
    }
}
