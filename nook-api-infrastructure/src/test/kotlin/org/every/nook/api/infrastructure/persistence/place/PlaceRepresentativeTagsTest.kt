package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.domain.place.PlaceTag
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceRepresentativeTagsTest {
    @Test
    fun `keeps at most two representative tags per category`() {
        val place = PlaceEntity(
            provider = "KAKAO",
            externalPlaceId = "1",
            name = "누크",
            address = "서울",
            latitude = BigDecimal("37.0"),
            longitude = BigDecimal("127.0"),
        )

        place.updateRepresentativeTags(
            listOf(
                PlaceTag.AESTHETIC,
                PlaceTag.COZY,
                PlaceTag.QUIET,
                PlaceTag.DATE,
                PlaceTag.PARKING,
            ).map { it.name },
        )

        assertEquals(
            listOf(PlaceTag.AESTHETIC, PlaceTag.COZY, PlaceTag.DATE, PlaceTag.PARKING).map { it.name },
            place.representativeTags,
        )
    }
}
