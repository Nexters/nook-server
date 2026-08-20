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
    fun `matches a store when another provider adds a neighborhood suffix`() {
        val kakao = place(
            name = "보후밀 효창공원",
            address = "서울 마포구 효창목2길 30",
            latitude = "37.5459790",
            longitude = "126.9591043",
        )
        val naver = candidate(
            name = "보후밀",
            address = "서울특별시 마포구 효창목2길 30 1층 일부호",
            latitude = "37.5459824",
            longitude = "126.9591035",
        )

        assertTrue(matcher.matches(kakao, naver))
    }

    @Test
    fun `matches an exact store name at an adjacent road number`() {
        val kakao = place(
            name = "감나무집기사식당",
            address = "서울 마포구 연남로 23",
            latitude = "37.5617089",
            longitude = "126.9221585",
        )
        val naver = candidate(
            name = "감나무집기사식당",
            address = "서울특별시 마포구 연남로 25",
            latitude = "37.5617165",
            longitude = "126.9221264",
        )

        assertTrue(matcher.matches(kakao, naver))
    }

    @Test
    fun `does not merge another store at an adjacent road number`() {
        val restaurant = place(
            name = "감나무집기사식당",
            address = "서울 마포구 연남로 23",
            latitude = "37.5617089",
            longitude = "126.9221585",
        )
        val anotherStore = candidate(
            name = "연남동기사식당",
            address = "서울특별시 마포구 연남로 25",
            latitude = "37.5617165",
            longitude = "126.9221264",
        )

        assertFalse(matcher.matches(restaurant, anotherStore))
    }

    @Test
    fun `does not merge an exact store name when road numbers are not adjacent`() {
        val restaurant = place(
            name = "감나무집기사식당",
            address = "서울 마포구 연남로 23",
            latitude = "37.5617089",
            longitude = "126.9221585",
        )
        val distantNumber = candidate(
            name = "감나무집기사식당",
            address = "서울특별시 마포구 연남로 29",
            latitude = "37.5617165",
            longitude = "126.9221264",
        )

        assertFalse(matcher.matches(restaurant, distantNumber))
    }

    @Test
    fun `does not merge an adjacent road number returned by the same provider`() {
        val restaurant = place(
            name = "감나무집기사식당",
            address = "서울 마포구 연남로 23",
            latitude = "37.5617089",
            longitude = "126.9221585",
        )
        val sameProvider = candidate(
            provider = "KAKAO",
            name = "감나무집기사식당",
            address = "서울 마포구 연남로 25",
            latitude = "37.5617165",
            longitude = "126.9221264",
        )

        assertFalse(matcher.matches(restaurant, sameProvider))
    }

    @Test
    fun `does not merge an adjacent road number outside the strict radius`() {
        val restaurant = place(
            name = "감나무집기사식당",
            address = "서울 마포구 연남로 23",
            latitude = "37.5617089",
            longitude = "126.9221585",
        )
        val fartherCandidate = candidate(
            name = "감나무집기사식당",
            address = "서울특별시 마포구 연남로 25",
            latitude = "37.5618500",
            longitude = "126.9221585",
        )

        assertFalse(matcher.matches(restaurant, fartherCandidate))
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

    private fun candidate(
        name: String,
        address: String,
        latitude: String,
        longitude: String,
        provider: String = "NAVER",
    ) = PlaceCandidate(
        provider = provider,
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
