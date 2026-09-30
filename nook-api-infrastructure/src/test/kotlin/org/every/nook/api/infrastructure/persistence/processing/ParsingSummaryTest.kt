package org.every.nook.api.infrastructure.persistence.processing

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParsingSummaryTest {
    @Test
    fun `completed jobs do not change a failure alert fingerprint`() {
        val failure = job(id = 1, status = "FAILED", attempt = 4, failedAt = FAILED_AT)
        val before = requireNotNull(ParsingSummary.from(listOf(failure)))
        val after = requireNotNull(
            ParsingSummary.from(listOf(failure, job(id = 2, status = "COMPLETED", attempt = 1))),
        )

        assertEquals(before.fingerprint, after.fingerprint)
        assertNotEquals(before.legacyFingerprint, after.legacyFingerprint)
    }

    @Test
    fun `a new failed attempt changes the alert fingerprint`() {
        val before = requireNotNull(
            ParsingSummary.from(
                listOf(
                    job(id = 1, status = "FAILED", attempt = 4, failedAt = FAILED_AT),
                ),
            ),
        )
        val after = requireNotNull(
            ParsingSummary.from(
                listOf(
                    job(id = 1, status = "FAILED", attempt = 5, failedAt = FAILED_AT.plusSeconds(1)),
                ),
            ),
        )

        assertNotEquals(before.fingerprint, after.fingerprint)
    }

    private fun job(id: Long, status: String, attempt: Int, failedAt: Instant? = null) = ParsingSummaryJob(
        type = "POST_MEDIA",
        id = id,
        status = status,
        attempt = attempt,
        stage = "POST_MEDIA",
        reason = if (status == "FAILED") "download failed" else null,
        lastFailedAt = failedAt,
    )

    private companion object {
        val FAILED_AT: Instant = Instant.parse("2026-09-13T00:00:00Z")
    }
}
