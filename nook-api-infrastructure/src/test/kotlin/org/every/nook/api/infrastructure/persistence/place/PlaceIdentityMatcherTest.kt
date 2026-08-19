package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceIdentityMatcherTest {
    private val matcher = PlaceIdentityMatcher()

    @Test
    fun `matches the half house provider variants`() {
        val kakao = place(
            name = "하프하우스 강남역점",
            address = "서울 강남구 강남대로84길 13",
            latitude = "37.4968714",
            longitude = "127.0295841",
        )
        val naver = candidate(
            name = "하프하우스",
            address = "서울특별시 강남구 강남대로84길 13 강남역 KR 타워 1층, 2층",
            latitude = "37.4967981",
            longitude = "127.0296710",
        )

        assertTrue(matcher.matches(kakao, naver))
    }

    @Test
    fun `does not merge another store in the same building`() {
        val halfHouse = place(
            name = "하프하우스 강남역점",
            address = "서울 강남구 강남대로84길 13",
            latitude = "37.4968714",
            longitude = "127.0295841",
        )
        val anotherStore = candidate(
            name = "강남역 베이커리",
            address = "서울특별시 강남구 강남대로84길 13 KR 타워 2층",
            latitude = "37.4968000",
            longitude = "127.0296700",
        )

        assertFalse(matcher.matches(halfHouse, anotherStore))
    }

    @Test
    fun `does not merge a same-name store outside the nearby radius`() {
        val gangnam = place(
            name = "하프하우스 강남역점",
            address = "서울 강남구 강남대로84길 13",
            latitude = "37.4968714",
            longitude = "127.0295841",
        )
        val distant = candidate(
            name = "하프하우스",
            address = "서울 강남구 강남대로84길 13",
            latitude = "37.4978714",
            longitude = "127.0295841",
        )

        assertFalse(matcher.matches(gangnam, distant))
    }

    private fun place(name: String, address: String, latitude: String, longitude: String) = PlaceEntity(
        provider = "KAKAO",
        externalPlaceId = "170705999",
        name = name,
        address = address,
        latitude = BigDecimal(latitude),
        longitude = BigDecimal(longitude),
    )

    private fun candidate(name: String, address: String, latitude: String, longitude: String) = PlaceCandidate(
        provider = "NAVER",
        externalPlaceId = "naver-half-house",
        name = name,
        address = address,
        latitude = BigDecimal(latitude),
        longitude = BigDecimal(longitude),
        category = "음식점",
        phoneNumber = null,
        providerUrl = null,
    )
}
