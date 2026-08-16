package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceAddressMatcherTest {
    @Test
    fun `accepts the same base address when provider omits floor and room details`() {
        val candidateAddress = "서울 중구 마른내로 55"

        listOf(
            "서울 중구 마른내로 55 1층",
            "서울 중구 마른내로 55 4F",
            "서울 중구 마른내로 55 B1",
            "서울 중구 마른내로 55 지하 1층",
            "서울 중구 마른내로 55 201호",
        ).forEach { addressHint ->
            assertTrue(PlaceAddressMatcher.isCompatible(addressHint, candidateAddress))
        }
    }

    @Test
    fun `rejects a different road or building number`() {
        assertFalse(
            PlaceAddressMatcher.isCompatible(
                "서울 마포구 동교로38길 27-19 지1층 좌측",
                "서울 마포구 월드컵북로5가길 34",
            ),
        )
    }

    @Test
    fun `rejects conflicting floor and room details when both addresses provide them`() {
        assertFalse(
            PlaceAddressMatcher.isCompatible(
                "서울 중구 마른내로 55 4층 201호",
                "서울 중구 마른내로 55 3층 201호",
            ),
        )
        assertFalse(
            PlaceAddressMatcher.isCompatible(
                "서울 중구 마른내로 55 B1",
                "서울 중구 마른내로 55 1층",
            ),
        )
        assertFalse(
            PlaceAddressMatcher.isCompatible(
                "서울 중구 마른내로 55 201호",
                "서울 중구 마른내로 55 202호",
            ),
        )
    }

    @Test
    fun `puts the full detailed address first without exceeding the query limit`() {
        val clue = PlaceClue(
            name = "파티오피즈",
            region = "서울 용산구",
            queries = listOf("파티오피즈", "이태원 파티오피즈", "patio fizz", "파티오 피즈"),
            addressHint = "서울 용산구 이태원로20가길 11 4층",
        )

        assertEquals(
            listOf(
                "파티오피즈 서울 용산구 이태원로20가길 11 4층",
                "파티오피즈",
                "이태원 파티오피즈",
                "patio fizz",
            ),
            clue.searchQueries(),
        )
    }

    @Test
    fun `accepts matching store when provider omits floor but rejects wrong address or store`() {
        val clue = PlaceClue(
            name = "SHEET",
            region = "서울 중구",
            queries = listOf("SHEET", "을지로 SHEET"),
            addressHint = "서울 중구 마른내로 55 4F",
        )

        assertTrue(clue.isSupportedBy(candidate("SHEET", "서울 중구 마른내로 55")))
        assertFalse(clue.isSupportedBy(candidate("SHEET", "서울 중구 마른내로 51-4")))
        assertFalse(clue.isSupportedBy(candidate("고은손카드", "서울 중구 마른내로 55")))
        assertFalse(clue.isSupportedBy(candidate("SHEET", "서울 중구 마른내로 55 3층")))

        val basementClue = PlaceClue(
            name = "도원",
            region = "서울 마포구",
            queries = listOf("도원", "홍대 도원"),
            addressHint = "서울 마포구 동교로38길 27-19 지1층 좌측",
        )
        assertFalse(basementClue.isSupportedBy(candidate("도원", "서울 마포구 월드컵북로5가길 34")))
    }

    private fun candidate(name: String, address: String): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1",
        name = name,
        address = address,
        latitude = BigDecimal("37.0"),
        longitude = BigDecimal("127.0"),
        category = null,
        phoneNumber = null,
        providerUrl = null,
    )
}
