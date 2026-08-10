package org.every.nook.api.domain.place

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KoreanCityNameExtractorTest {
    @Test
    fun `extracts normalized metropolitan and local city names`() {
        val cases = mapOf(
            "서울특별시 마포구 연희로1길 55" to "서울",
            "서울 마포구 연희로1길 55" to "서울",
            "부산광역시 해운대구 해운대로 1" to "부산",
            "경기도 이천시 부발읍 경충대로 1" to "이천",
            "경기 성남시 분당구 불정로 6" to "성남",
            "강원특별자치도 춘천시 중앙로 1" to "춘천",
            "전북특별자치도 전주시 완산구 기린대로 1" to "전주",
            "제주특별자치도 서귀포시 중앙로 1" to "서귀포",
            "세종특별자치시 한누리대로 1" to "세종",
            "경기 양평군 양평읍 중앙로 1" to "양평",
            "성남시 분당구 판교역로 1" to "성남",
        )

        cases.forEach { (address, expected) ->
            assertEquals(expected, KoreanCityNameExtractor.extract(address), address)
        }
    }

    @Test
    fun `normalizes repeated whitespace`() {
        assertEquals("성남", KoreanCityNameExtractor.extract("  경기   성남시  분당구 불정로 6  "))
    }

    @Test
    fun `returns null when city cannot be identified`() {
        assertNull(KoreanCityNameExtractor.extract(""))
        assertNull(KoreanCityNameExtractor.extract("강남구 테헤란로 1"))
        assertNull(KoreanCityNameExtractor.extract("경기도 분당구 판교역로 1"))
        assertNull(KoreanCityNameExtractor.extract("Tokyo Shibuya 1-1"))
    }
}
