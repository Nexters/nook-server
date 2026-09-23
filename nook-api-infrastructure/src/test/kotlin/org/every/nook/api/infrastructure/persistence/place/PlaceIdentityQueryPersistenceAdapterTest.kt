package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.domain.place.PlaceProviderReference
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceIdentityQueryPersistenceAdapterTest {
    private val repository = mock(PlaceJpaRepository::class.java)
    private val adapter = PlaceIdentityQueryPersistenceAdapter(repository)

    @Test
    fun `finds only exact provider and external id pairs`() {
        val kakao = mock(PlaceEntity::class.java)
        val crossProductOnly = mock(PlaceEntity::class.java)
        `when`(kakao.id).thenReturn(17)
        `when`(kakao.provider).thenReturn("KAKAO")
        `when`(kakao.externalPlaceId).thenReturn("1234")
        `when`(crossProductOnly.id).thenReturn(18)
        `when`(crossProductOnly.provider).thenReturn("NAVER")
        `when`(crossProductOnly.externalPlaceId).thenReturn("1234")
        val references = setOf(
            PlaceProviderReference("KAKAO", "1234"),
            PlaceProviderReference("NAVER", "5678"),
        )
        `when`(
            repository.findAllByProviderInAndExternalPlaceIdIn(
                setOf("KAKAO", "NAVER"),
                setOf("1234", "5678"),
            ),
        ).thenReturn(listOf(kakao, crossProductOnly))

        val result = adapter.findIds(references)

        assertEquals(mapOf(PlaceProviderReference("KAKAO", "1234") to 17L), result)
        verify(repository).findAllByProviderInAndExternalPlaceIdIn(
            setOf("KAKAO", "NAVER"),
            setOf("1234", "5678"),
        )
    }

    @Test
    fun `does not query storage for an empty search page`() {
        assertEquals(emptyMap(), adapter.findIds(emptySet()))
        verifyNoInteractions(repository)
    }
}
