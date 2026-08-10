package org.every.nook.api.domain.place

object KoreanCityNameExtractor {
    fun extract(address: String): String? {
        val tokens = address.trim().split(WHITESPACE).filter(String::isNotBlank)
        val first = tokens.firstOrNull()
        val locality = when {
            first == null -> null
            first in METROPOLITAN_AREAS -> METROPOLITAN_AREAS[first]
            first in PROVINCES -> tokens.getOrNull(1)?.takeIf { it.isCityOrCounty() }
            first.isCityOrCounty() -> first
            else -> null
        }

        return locality?.removeLocalitySuffix()
            ?.takeIf(String::isNotBlank)
            ?.takeIf { it.length <= Place.MAX_CITY_LENGTH }
    }

    private fun String.isCityOrCounty(): Boolean = endsWith(CITY_SUFFIX) || endsWith(COUNTY_SUFFIX)

    private fun String.removeLocalitySuffix(): String = when {
        endsWith(CITY_SUFFIX) -> removeSuffix(CITY_SUFFIX)
        endsWith(COUNTY_SUFFIX) -> removeSuffix(COUNTY_SUFFIX)
        else -> this
    }

    private val METROPOLITAN_AREAS = mapOf(
        "서울" to "서울",
        "서울시" to "서울",
        "서울특별시" to "서울",
        "부산" to "부산",
        "부산시" to "부산",
        "부산광역시" to "부산",
        "대구" to "대구",
        "대구시" to "대구",
        "대구광역시" to "대구",
        "인천" to "인천",
        "인천시" to "인천",
        "인천광역시" to "인천",
        "광주" to "광주",
        "광주시" to "광주",
        "광주광역시" to "광주",
        "대전" to "대전",
        "대전시" to "대전",
        "대전광역시" to "대전",
        "울산" to "울산",
        "울산시" to "울산",
        "울산광역시" to "울산",
        "세종" to "세종",
        "세종시" to "세종",
        "세종특별자치시" to "세종",
    )

    private val PROVINCES = setOf(
        "경기",
        "경기도",
        "강원",
        "강원도",
        "강원특별자치도",
        "충북",
        "충청북도",
        "충남",
        "충청남도",
        "전북",
        "전라북도",
        "전북특별자치도",
        "전남",
        "전라남도",
        "경북",
        "경상북도",
        "경남",
        "경상남도",
        "제주",
        "제주도",
        "제주특별자치도",
    )

    private val WHITESPACE = Regex("\\s+")
    private const val CITY_SUFFIX = "시"
    private const val COUNTY_SUFFIX = "군"
}
