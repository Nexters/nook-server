package org.every.nook.api.domain.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GeoBoundsTest {
    @Test
    fun `rejects an inverted latitude range`() {
        assertFailsWith<IllegalArgumentException> {
            GeoBounds(
                northLatitude = BigDecimal("37.4"),
                westLongitude = BigDecimal("126.8"),
                southLatitude = BigDecimal("37.6"),
                eastLongitude = BigDecimal("127.2"),
            )
        }
    }

    @Test
    fun `rejects a bounds crossing the date line`() {
        assertFailsWith<IllegalArgumentException> {
            GeoBounds(
                northLatitude = BigDecimal("37.6"),
                westLongitude = BigDecimal("127.2"),
                southLatitude = BigDecimal("37.4"),
                eastLongitude = BigDecimal("126.8"),
            )
        }
    }
}
