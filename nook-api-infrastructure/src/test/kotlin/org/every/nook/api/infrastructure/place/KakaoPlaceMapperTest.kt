package org.every.nook.api.infrastructure.place

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KakaoPlaceMapperTest {
    @Test
    fun `maps Kakao fields and prefers road address`() {
        val result = KakaoPlaceMapper().map(
            KakaoPlaceResponse(
                documents = listOf(
                    document(
                        roadAddress = "서울 성동구 아차산로 1",
                        address = "서울 성동구 성수동 1",
                    ),
                ),
            ),
        ).single()

        assertEquals("KAKAO", result.provider)
        assertEquals("26338954", result.externalPlaceId)
        assertEquals("서울 성동구 아차산로 1", result.address)
        assertEquals("37.5120741", result.latitude.toPlainString())
        assertEquals("127.0590297", result.longitude.toPlainString())
        assertEquals("음식점", result.category)
    }

    @Test
    fun `maps only top level Kakao category`() {
        val result = KakaoPlaceMapper().map(
            KakaoPlaceResponse(
                documents = listOf(
                    document(
                        roadAddress = "서울 성동구 아차산로 1",
                        address = "서울 성동구 성수동 1",
                        category = "음식점 > 간식 > 제과,베이커리",
                    ),
                ),
            ),
        ).single()

        assertEquals("음식점", result.category)
    }

    @Test
    fun `falls back to lot address and normalizes blank optional values`() {
        val result = KakaoPlaceMapper().map(
            KakaoPlaceResponse(
                documents = listOf(
                    document(
                        roadAddress = "",
                        address = "서울 성동구 성수동 1",
                        category = " ",
                        phone = "",
                    ),
                ),
            ),
        ).single()

        assertEquals("서울 성동구 성수동 1", result.address)
        assertNull(result.category)
        assertNull(result.phoneNumber)
    }

    private fun document(
        roadAddress: String,
        address: String,
        category: String = "음식점 > 카페",
        phone: String = "02-1234-5678",
    ): KakaoPlaceResponse.Document = KakaoPlaceResponse.Document(
        id = "26338954",
        placeName = "Nook Cafe",
        categoryName = category,
        phone = phone,
        addressName = address,
        roadAddressName = roadAddress,
        x = "127.0590297",
        y = "37.5120741",
        placeUrl = "https://place.map.kakao.com/26338954",
    )
}
