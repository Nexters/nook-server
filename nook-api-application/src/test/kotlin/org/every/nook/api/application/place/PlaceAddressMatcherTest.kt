package org.every.nook.api.application.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceAddressMatcherTest {
    @Test
    fun `accepts a one-syllable OCR typo in a road name at the same district and building number`() {
        assertTrue(
            PlaceAddressMatcher.isCompatible(
                "서울 광진구 가람로32길 50 1층",
                "서울 광진구 나람로32길 50",
            ),
        )
    }

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
    fun `accepts a road address when the source omits the final gil suffix`() {
        assertTrue(
            PlaceAddressMatcher.isCompatible(
                "서울 용산구 이태원로20가 11 4층",
                "서울특별시 용산구 이태원로20가길 11 4층",
            ),
        )
        assertTrue(
            PlaceAddressMatcher.isCompatible(
                "서울 용산구 이태원로20가 11 4층",
                "서울 용산구 이태원로20가길 11",
            ),
        )
    }

    @Test
    fun `accepts a building number and floor concatenated by OCR`() {
        assertTrue(
            PlaceAddressMatcher.isCompatible(
                "서울 중구 삼일대로 3018층",
                "서울특별시 중구 삼일대로 301 8층",
            ),
        )
        assertTrue(PlaceAddressMatcher.hasLocationDetail("서울 중구 삼일대로 3018층"))
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
    fun `searches detailed and base address variants before the OCR store name`() {
        val clue = PlaceClue(
            name = "파티오피즈",
            region = "서울 용산구",
            queries = listOf("파티오피즈", "이태원 파티오피즈", "patio fizz", "파티오 피즈"),
            addressHint = "서울 용산구 이태원로20가길 11 4층",
        )

        assertEquals(
            listOf(
                "서울 용산구 이태원로20가길 11 4층",
                "서울 용산구 이태원로20가길 11",
                "용산구 이태원로20가길 11",
                "파티오피즈",
            ),
            clue.searchQueries(),
        )
    }

    @Test
    fun `keeps a bare store name after the detailed address query`() {
        val clue = PlaceClue(
            name = "도원",
            region = "홍대입구역",
            queries = listOf(
                "dowon.kr 홍대입구역",
                "도원 동교로38길 27-19 지1층",
                "도원 홍대입구 1층",
                "Dowon 홍대입구",
            ),
            addressHint = "서울 마포구 동교로38길 27-19 지1층 좌측",
        )

        assertEquals(
            listOf(
                "서울 마포구 동교로38길 27-19 지1층 좌측",
                "서울 마포구 동교로38길 27-19",
                "마포구 동교로38길 27-19",
                "도원",
            ),
            clue.searchQueries(),
        )
    }

    @Test
    fun `removes basement details from an address without a province prefix`() {
        val clue = PlaceClue(
            name = "홈",
            region = "마포구",
            queries = emptyList(),
            addressHint = "마포구 와우산로37길 1 지1층",
        )

        assertEquals(
            listOf(
                "마포구 와우산로37길 1 지1층",
                "마포구 와우산로37길 1",
                "홈",
            ),
            clue.searchQueries(),
        )
    }

    @Test
    fun `uses regional aliases before inferred queries for multilingual store names`() {
        val clue = PlaceClue(
            name = "라벤다 lavender",
            region = "중곡역",
            queries = listOf("lavender lavender_seoul", "라벤다 능동로50길 24 1층"),
            addressHint = "서울 광진구 능동로50길 24 1층",
        )

        assertEquals(
            listOf(
                "서울 광진구 능동로50길 24 1층",
                "서울 광진구 능동로50길 24",
                "광진구 능동로50길 24",
                "라벤다 lavender",
            ),
            clue.searchQueries(),
        )
    }

    @Test
    fun `limits candidate selection to addresses compatible with the explicit clue`() {
        val clue = PlaceClue(
            name = "도원",
            region = "서울 마포구",
            queries = listOf("도원", "홍대 도원"),
            addressHint = "서울 마포구 동교로38길 27-19 지1층 좌측",
        )
        val correct = candidate("도원", "서울 마포구 동교로38길 27-19")
        val wrong = candidate("도원", "서울 마포구 월드컵북로5가길 34")

        assertEquals(
            listOf(correct),
            listOf(correct, wrong)
                .map { PlaceCandidateSelector.Candidate(it, listOf("도원")) }
                .compatibleWith(clue)
                .map(PlaceCandidateSelector.Candidate::place),
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
        assertTrue(clue.isSupportedBy(candidate("SHEEP", "서울 중구 마른내로 55")))

        val transliteratedClue = PlaceClue(
            name = "noob.store",
            region = "서울 중구",
            queries = listOf("noobstore", "을지로 noobstore"),
            addressHint = "서울 중구 을지로18길 25-2 4층",
        )
        assertTrue(
            transliteratedClue.isSupportedBy(
                candidate("눕스토어", "서울 중구 을지로18길 25-2 4층 흰색 문 noobstore"),
            ),
        )

        val differentlyNamedDetailedAddressClue = PlaceClue(
            name = "TOOL 3",
            region = "서울 중구",
            queries = listOf("TOOL 3", "마른내로6길 18-1 2층"),
            addressHint = "서울 중구 마른내로6길 18-1 2층",
        )
        assertFalse(
            differentlyNamedDetailedAddressClue.isSupportedBy(
                candidate("툴3", "서울 중구 마른내로6길 18-1"),
            ),
        )

        val roomClue = PlaceClue(
            name = "KOZELNANT",
            region = "서울 종로구",
            queries = listOf("KOZELNANT", "삼일대로 437 인사관 407호"),
            addressHint = "서울 종로구 삼일대로 437 인사관 407호",
        )
        assertFalse(roomClue.isSupportedBy(candidate("세보가", "서울 종로구 삼일대로 437")))

        val basementClue = PlaceClue(
            name = "도원 Dowon",
            region = "서울 마포구",
            queries = listOf("Dowon dowon.kr", "홍대입구역 Dowon"),
            evidence = listOf(
                PlaceClueEvidence(
                    imageIndex = 1,
                    evidenceText = "도원 @dowon.kr 홍대입구역 서울 마포구 동교로38길 27-19 지1층 좌측",
                ),
            ),
            addressHint = "서울 마포구 동교로38길 27-19 지1층 좌측",
        )
        assertTrue(basementClue.isSupportedBy(candidate("도원", "서울 마포구 동교로38길 27-19")))
        assertFalse(basementClue.isSupportedBy(candidate("도원", "서울 마포구 월드컵북로5가길 34")))
    }

    @Test
    fun `grounds a one-character store name from OCR evidence at the exact address`() {
        val clue = PlaceClue(
            name = "몬",
            region = "서울 마포구",
            queries = listOf("몬"),
            addressHint = "서울 마포구 가람로37길 1 지1층",
            evidence = listOf(
                PlaceClueEvidence(7, "몬 서울특별시 마포구 가람로37길 1 지1층"),
            ),
        )

        assertTrue(
            clue.isSupportedBy(
                candidate("문", "서울 마포구 가람로37길 1"),
                matchedQueries = listOf("서울 마포구 가람로37길 1 지1층"),
            ),
        )
        assertFalse(clue.isSupportedBy(candidate("문", "서울 마포구 가람로37길 1")))
    }

    @Test
    fun `grounds a one-jamo OCR typo found by a normalized address search`() {
        val clue = PlaceClue(
            name = "홈",
            region = "서울 마포구",
            queries = listOf("홈"),
            addressHint = "마포구 와우산로37길 1 지1층",
            evidence = listOf(
                PlaceClueEvidence(7, "홈 서울특별시 마포구 와우산로37길 1 지1층"),
            ),
        )
        val normalizedAddressQuery = listOf("마포구 와우산로37길 1")

        assertTrue(clue.isSupportedBy(candidate("홉", "서울 마포구 와우산로37길 1"), normalizedAddressQuery))
        assertFalse(clue.isSupportedBy(candidate("컬러뮤즈", "서울 마포구 와우산로37길 1"), normalizedAddressQuery))
    }

    @Test
    fun `grounds a provider localized name only through an explicit OCR name search and exact address`() {
        val clue = PlaceClue(
            name = "umoae",
            region = "서울 용산구",
            queries = listOf("umoae"),
            addressHint = "서울 용산구 한강대로84길 21-17 1층",
            evidence = listOf(PlaceClueEvidence(3, "Knewnew 2umoae | 숙대입구역 채광 좋은 창가")),
        )
        val localizedCandidate = candidate("우모에", "서울 용산구 한강대로84길 21-17")

        assertTrue(clue.hasGroundedExplicitNameSearch(localizedCandidate, listOf("umoae")))
        assertFalse(clue.hasGroundedExplicitNameSearch(localizedCandidate, listOf("용산구 한강대로84길 21-17")))
    }

    @Test
    fun `grounds short OCR name typos when the road address has one OCR typo`() {
        val clue = PlaceClue(
            name = "모먼트",
            region = "서울 광진구",
            queries = listOf("모먼트"),
            addressHint = "서울 광진구 가람로32길 50 1층",
            evidence = listOf(
                PlaceClueEvidence(10, "모먼트 서울 광진구 가람로32길 50 1층"),
            ),
        )

        assertTrue(clue.isSupportedBy(candidate("모멘트", "서울 광진구 나람로32길 50")))
    }

    @Test
    fun `accepts a near OCR store name when the road address matches`() {
        val clue = PlaceClue(
            name = "가람커퍼로스터스",
            region = "서울 성동구",
            queries = listOf("가람커퍼로스터스"),
            addressHint = "서울 성동구 푸른로11길 10 1층",
        )

        assertTrue(clue.isSupportedBy(candidate("가람커피로스터스", "서울 성동구 푸른로11길 10")))
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
