package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.infrastructure.auth.JwtProperties
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtPlaceSelectionTokenAdapterTest {
    private val issuedAt = Instant.parse("2026-07-29T00:00:00Z")
    private val properties = JwtProperties(
        issuer = "nook-api",
        accessSecret = "12345678901234567890123456789012",
        refreshSecret = "abcdefghijklmnopqrstuvwxyz123456",
    )

    @Test
    fun `round trips a candidate for the same user`() {
        val adapter = adapter(issuedAt)
        val candidate = candidate()

        val token = adapter.issue(7, candidate)

        assertEquals(candidate, adapter.verify(7, token))
    }

    @Test
    fun `rejects a token issued for another user`() {
        val adapter = adapter(issuedAt)

        val token = adapter.issue(7, candidate())

        assertNull(adapter.verify(8, token))
    }

    @Test
    fun `rejects an expired token`() {
        val token = adapter(issuedAt).issue(7, candidate())

        assertNull(adapter(issuedAt.plusSeconds(601)).verify(7, token))
    }

    @Test
    fun `rejects a modified token`() {
        val adapter = adapter(issuedAt)
        val token = adapter.issue(7, candidate())
        val modified = token.dropLast(1) + if (token.last() == 'a') "b" else "a"

        assertNull(adapter.verify(7, modified))
    }

    private fun adapter(now: Instant): JwtPlaceSelectionTokenAdapter =
        JwtPlaceSelectionTokenAdapter(properties, Clock.fixed(now, ZoneOffset.UTC))

    private fun candidate(): PlaceCandidate = PlaceCandidate(
        provider = "KAKAO",
        externalPlaceId = "1234",
        name = "퍼머넌트해비탯",
        address = "경기 용인시",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        category = "카페",
        phoneNumber = "031-123-4567",
        providerUrl = "https://place.map.kakao.com/1234",
    )
}
