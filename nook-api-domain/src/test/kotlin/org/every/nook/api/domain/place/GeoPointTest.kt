package org.every.nook.api.domain.place

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeoPointTest {
    @Test
    fun `accepts boundary coordinates`() {
        val southWest = GeoPoint(BigDecimal("-90"), BigDecimal("-180"))
        val northEast = GeoPoint(BigDecimal("90"), BigDecimal("180"))

        assertEquals(BigDecimal("-90"), southWest.latitude)
        assertEquals(BigDecimal("180"), northEast.longitude)
    }

    @Test
    fun `rejects latitude outside its range`() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(BigDecimal("90.0000001"), BigDecimal.ZERO)
        }
    }

    @Test
    fun `rejects longitude outside its range`() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(BigDecimal.ZERO, BigDecimal("-180.0000001"))
        }
    }
}
