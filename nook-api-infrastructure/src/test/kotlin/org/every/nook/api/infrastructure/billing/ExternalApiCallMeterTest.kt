package org.every.nook.api.infrastructure.billing

import org.every.nook.api.application.billing.ExternalApiUsageMeter
import org.every.nook.api.application.billing.ExternalApiUsageStatus
import org.every.nook.api.application.billing.ReserveExternalApiUsage
import org.every.nook.api.application.billing.SettleExternalApiUsage
import org.every.nook.api.application.billing.UsageReservation
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalApiCallMeterTest {
    @Test
    fun `reserves before call and settles successful usage`() {
        val usageMeter = RecordingUsageMeter()
        val meter = ExternalApiCallMeter(usageMeter)

        val result = meter.measure(
            provider = "openai",
            sku = "gpt-test",
            feature = "content-inference",
            usage = { SettledUsage(BigDecimal.TEN, inputTokens = 7, outputTokens = 3) },
        ) { "response" }

        assertEquals("response", result)
        assertEquals("openai", usageMeter.reserved.single().provider)
        assertEquals(ExternalApiUsageStatus.SUCCEEDED, usageMeter.settled.single().status)
        assertEquals(BigDecimal.TEN, usageMeter.settled.single().actualUnits)
        assertEquals(7, usageMeter.settled.single().inputTokens)
        assertEquals(3, usageMeter.settled.single().outputTokens)
    }

    @Test
    fun `settles failed usage and rethrows provider failure`() {
        val usageMeter = RecordingUsageMeter()
        val meter = ExternalApiCallMeter(usageMeter)

        assertFailsWith<IllegalStateException> {
            meter.measure(provider = "google-places", sku = "place-details", feature = "supplement") {
                error("provider failed")
            }
        }

        assertEquals(ExternalApiUsageStatus.FAILED, usageMeter.settled.single().status)
        assertEquals("IllegalStateException", usageMeter.settled.single().failureCode)
    }

    private class RecordingUsageMeter : ExternalApiUsageMeter {
        val reserved = mutableListOf<ReserveExternalApiUsage>()
        val settled = mutableListOf<SettleExternalApiUsage>()

        override fun reserve(command: ReserveExternalApiUsage): UsageReservation {
            reserved += command
            return UsageReservation(1, command.idempotencyKey)
        }

        override fun settle(command: SettleExternalApiUsage) {
            settled += command
        }
    }
}
