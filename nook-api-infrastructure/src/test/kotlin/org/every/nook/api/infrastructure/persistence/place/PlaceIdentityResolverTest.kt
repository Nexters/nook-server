package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceIdentityResolverTest {
    private val placeRepository = mock(PlaceJpaRepository::class.java)
    private val referenceRepository = mock(PlaceProviderReferenceJpaRepository::class.java)
    private val resolver = PlaceIdentityResolver(placeRepository, referenceRepository, PlaceIdentityMatcher())

    @Test
    fun `provider reference overrides a legacy duplicate place`() {
        val canonical = mockPlace(928, "KAKAO", "170705999")
        val reference = PlaceProviderReferenceEntity(928, "NAVER", "naver-half-house")
        `when`(referenceRepository.findByProviderAndExternalPlaceId("NAVER", "naver-half-house"))
            .thenReturn(reference)
        `when`(placeRepository.findById(928)).thenReturn(Optional.of(canonical))

        val resolved = resolver.resolve(candidate())

        assertEquals(canonical, resolved)
        verify(placeRepository, never()).findByProviderAndExternalPlaceId("NAVER", "naver-half-house")
    }

    @Test
    fun `registers a naver identifier on the matching nearby kakao place`() {
        val candidate = candidate()
        val canonical = mockPlace(928, "KAKAO", "170705999")
        val alias = PlaceProviderReferenceEntity(928, "NAVER", "naver-half-house")
        `when`(referenceRepository.findByProviderAndExternalPlaceId("NAVER", "naver-half-house"))
            .thenReturn(null, alias)
        `when`(referenceRepository.findByProviderAndExternalPlaceId("KAKAO", "170705999"))
            .thenReturn(null)
        `when`(placeRepository.findByProviderAndExternalPlaceId("NAVER", "naver-half-house"))
            .thenReturn(null)
        `when`(
            placeRepository.findAllByLatitudeBetweenAndLongitudeBetween(
                BigDecimal("37.4962981"),
                BigDecimal("37.4972981"),
                BigDecimal("127.0289710"),
                BigDecimal("127.0303710"),
            ),
        ).thenReturn(listOf(canonical))
        `when`(placeRepository.findById(928)).thenReturn(Optional.of(canonical))

        val resolved = resolver.resolve(candidate)

        assertEquals(canonical, resolved)
        verify(referenceRepository).insertIgnore(928, "NAVER", "naver-half-house")
        verify(placeRepository, never()).insertIgnore(
            candidate.provider,
            candidate.externalPlaceId,
            candidate.name,
            candidate.address,
            candidate.city,
            candidate.latitude,
            candidate.longitude,
            candidate.category,
            candidate.phoneNumber,
        )
    }

    @Test
    fun `registers an adjacent naver road number on the exact-name kakao place`() {
        val candidate = candidate(
            name = "감나무집기사식당",
            externalPlaceId = "naver-gamnamu",
            address = "서울특별시 마포구 연남로 25",
            latitude = "37.5617165",
            longitude = "126.9221264",
        )
        val canonical = mockPlace(
            id = 717,
            provider = "KAKAO",
            externalPlaceId = "1641347883",
            name = "감나무집기사식당",
            address = "서울 마포구 연남로 23",
            latitude = "37.5617089",
            longitude = "126.9221585",
        )
        val alias = PlaceProviderReferenceEntity(717, "NAVER", "naver-gamnamu")
        `when`(referenceRepository.findByProviderAndExternalPlaceId("NAVER", "naver-gamnamu"))
            .thenReturn(null, alias)
        `when`(referenceRepository.findByProviderAndExternalPlaceId("KAKAO", "1641347883"))
            .thenReturn(null)
        `when`(placeRepository.findByProviderAndExternalPlaceId("NAVER", "naver-gamnamu"))
            .thenReturn(null)
        `when`(
            placeRepository.findAllByLatitudeBetweenAndLongitudeBetween(
                BigDecimal("37.5612165"),
                BigDecimal("37.5622165"),
                BigDecimal("126.9214264"),
                BigDecimal("126.9228264"),
            ),
        ).thenReturn(listOf(canonical))
        `when`(placeRepository.findById(717)).thenReturn(Optional.of(canonical))

        val resolved = resolver.resolve(candidate)

        assertEquals(canonical, resolved)
        verify(referenceRepository).insertIgnore(717, "NAVER", "naver-gamnamu")
        verify(placeRepository, never()).insertIgnore(
            candidate.provider,
            candidate.externalPlaceId,
            candidate.name,
            candidate.address,
            candidate.city,
            candidate.latitude,
            candidate.longitude,
            candidate.category,
            candidate.phoneNumber,
        )
    }

    @Test
    fun `creates a separate place for another store in the same building`() {
        val candidate = candidate(name = "강남역 베이커리", externalPlaceId = "another-store")
        val halfHouse = mockPlace(928, "KAKAO", "170705999")
        val created = mockPlace(930, "NAVER", "another-store", name = "강남역 베이커리")
        val alias = PlaceProviderReferenceEntity(930, "NAVER", "another-store")
        `when`(referenceRepository.findByProviderAndExternalPlaceId("NAVER", "another-store"))
            .thenReturn(null, alias)
        `when`(referenceRepository.findByProviderAndExternalPlaceId("KAKAO", "170705999"))
            .thenReturn(null)
        `when`(placeRepository.findByProviderAndExternalPlaceId("NAVER", "another-store"))
            .thenReturn(null, created)
        `when`(placeRepository.findById(930)).thenReturn(Optional.of(created))
        `when`(
            placeRepository.findAllByLatitudeBetweenAndLongitudeBetween(
                BigDecimal("37.4962981"),
                BigDecimal("37.4972981"),
                BigDecimal("127.0289710"),
                BigDecimal("127.0303710"),
            ),
        ).thenReturn(listOf(halfHouse))

        val resolved = resolver.resolve(candidate)

        assertEquals(created, resolved)
        verify(placeRepository).insertIgnore(
            candidate.provider,
            candidate.externalPlaceId,
            candidate.name,
            candidate.address,
            candidate.city,
            candidate.latitude,
            candidate.longitude,
            candidate.category,
            candidate.phoneNumber,
        )
        verify(referenceRepository).insertIgnore(930, "NAVER", "another-store")
    }

    private fun candidate(
        name: String = "하프하우스",
        externalPlaceId: String = "naver-half-house",
        address: String = "서울특별시 강남구 강남대로84길 13 강남역 KR 타워 1층, 2층",
        latitude: String = "37.4967981",
        longitude: String = "127.0296710",
    ) = PlaceCandidate(
        provider = "NAVER",
        externalPlaceId = externalPlaceId,
        name = name,
        address = address,
        latitude = BigDecimal(latitude),
        longitude = BigDecimal(longitude),
        category = "음식점",
        phoneNumber = null,
        providerUrl = null,
    )

    private fun mockPlace(
        id: Long,
        provider: String,
        externalPlaceId: String,
        name: String = "하프하우스 강남역점",
        address: String = "서울 강남구 강남대로84길 13",
        latitude: String = "37.4968714",
        longitude: String = "127.0295841",
    ): PlaceEntity = mock(PlaceEntity::class.java).also { place ->
        `when`(place.id).thenReturn(id)
        `when`(place.provider).thenReturn(provider)
        `when`(place.externalPlaceId).thenReturn(externalPlaceId)
        `when`(place.name).thenReturn(name)
        `when`(place.address).thenReturn(address)
        `when`(place.latitude).thenReturn(BigDecimal(latitude))
        `when`(place.longitude).thenReturn(BigDecimal(longitude))
    }
}
