package org.every.nook.api.infrastructure.place

import kotlin.test.Test
import kotlin.test.assertEquals

class NaverPlaceMapperTest {
    @Test
    fun `maps Naver geocoding address to place candidate`() {
        val result = NaverPlaceMapper().map(
            query = "원동미나리삼겹살",
            response = NaverPlaceResponse(
                items = listOf(
                    address(
                        roadAddress = "서울특별시 용산구 한강대로77길 4-1",
                        x = "126.972332",
                        y = "37.543123",
                    ),
                ),
            ),
        )

        val candidate = result.single()
        assertEquals("NAVER", candidate.provider)
        assertEquals("원동미나리삼겹살", candidate.name)
        assertEquals("서울특별시 용산구 한강대로77길 4-1", candidate.address)
        assertEquals("126.972332".toBigDecimal(), candidate.longitude)
        assertEquals("37.543123".toBigDecimal(), candidate.latitude)
        assertEquals("음식점", candidate.category)
    }

    @Test
    fun `scaled WGS84 coordinates are normalized`() {
        val result = NaverPlaceMapper().map(
            query = "Nook Cafe",
            response = NaverPlaceResponse(
                items = listOf(address(x = "1269723320", y = "375431230")),
            ),
        )

        val candidate = result.single()
        assertEquals("126.9723320".toBigDecimal(), candidate.longitude)
        assertEquals("37.5431230".toBigDecimal(), candidate.latitude)
    }

    @Test
    fun `invalid coordinates are skipped`() {
        val result = NaverPlaceMapper().map(
            query = "Nook Cafe",
            response = NaverPlaceResponse(
                items = listOf(address(x = "9999999999", y = "9999999999")),
            ),
        )

        assertEquals(emptyList(), result)
    }

    private fun address(
        roadAddress: String? = "서울특별시 용산구 한강대로77길 4-1",
        address: String? = null,
        x: String? = "126.972332",
        y: String? = "37.543123",
    ): NaverPlaceResponse.Item = NaverPlaceResponse.Item(
        title = "<b>원동미나리삼겹살</b>",
        link = "https://map.naver.com/place/1",
        category = "음식점>한식",
        description = null,
        telephone = null,
        address = address,
        roadAddress = roadAddress,
        mapx = x,
        mapy = y,
    )
}
