package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceCandidateDeduplicatorTest {
    @Test
    fun `merges the same logical place across providers and keeps the detailed address`() {
        val kakao = candidate("KAKAO", "kakao-1", "파티오피즈", "서울 용산구 이태원로20가길 11")
        val naver = candidate("NAVER", "naver-1", "파티오 피즈", "서울 용산구 이태원로20가길 11 4층")

        val result = listOf(kakao, naver).distinctLogicalPlaces()

        assertEquals(listOf(naver), result)
    }

    @Test
    fun `does not merge different stores at the same address`() {
        val first = candidate("KAKAO", "1", "파티오피즈", "서울 용산구 이태원로20가길 11")
        val second = candidate("NAVER", "2", "다른가게", "서울 용산구 이태원로20가길 11 4층")

        assertEquals(listOf(first, second), listOf(first, second).distinctLogicalPlaces())
    }

    @Test
    fun `merges matched queries for the same logical candidate across providers`() {
        val kakao = candidate("KAKAO", "kakao-1", "텀 커피하우스", "서울 마포구 월드컵북로1길 74")
        val naver = candidate("NAVER", "naver-1", "텀 커피하우스", "서울 마포구 월드컵북로1길 74 1층")

        val result = listOf(
            PlaceCandidateSelector.Candidate(kakao, listOf("서울 마포구 서교동 376-7")),
            PlaceCandidateSelector.Candidate(naver, listOf("텀 커피하우스")),
        ).distinctLogicalCandidates()

        assertEquals(1, result.size)
        assertEquals(naver, result.single().place)
        assertEquals(
            listOf("서울 마포구 서교동 376-7", "텀 커피하우스"),
            result.single().matchedQueries,
        )
    }

    private fun candidate(provider: String, id: String, name: String, address: String) = PlaceCandidate(
        provider = provider,
        externalPlaceId = id,
        name = name,
        address = address,
        latitude = BigDecimal("37.0"),
        longitude = BigDecimal("127.0"),
        category = null,
        phoneNumber = null,
        providerUrl = null,
    )
}
