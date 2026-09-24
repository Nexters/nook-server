package org.every.nook.api.application.place

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceCategoryGroupTest {
    @Test
    fun `classifies supported provider categories`() {
        val cases = mapOf(
            "음식점 > 카페" to PlaceCategoryGroup.CAFE,
            "가정,생활 > 대형마트" to PlaceCategoryGroup.SHOPPING,
            "음식점 > 한식" to PlaceCategoryGroup.RESTAURANT,
            "음식점 > 카페 > 베이커리" to PlaceCategoryGroup.BAKERY,
            "음식점 > 술집 > 이자카야" to PlaceCategoryGroup.BAR,
            "여행 > 숙박 > 호텔" to PlaceCategoryGroup.LODGING,
            "여행 > 관광명소 > 공원" to PlaceCategoryGroup.TOURISM,
        )

        cases.forEach { (category, expected) ->
            assertEquals(expected, PlaceCategoryGroup.from(category), category)
        }
    }

    @Test
    fun `uses retained provider category before legacy top level category`() {
        assertEquals(
            PlaceCategoryGroup.CAFE,
            PlaceCategoryGroup.from(providerCategory = "음식점 > 카페"),
        )
    }

    @Test
    fun `returns etc when category is unknown`() {
        assertEquals(PlaceCategoryGroup.ETC, PlaceCategoryGroup.from("기타"))
        assertEquals(PlaceCategoryGroup.ETC, PlaceCategoryGroup.from(null))
    }
}
