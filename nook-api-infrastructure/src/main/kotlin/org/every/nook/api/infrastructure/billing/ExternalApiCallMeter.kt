package org.every.nook.api.infrastructure.billing

import org.every.nook.api.application.billing.ExternalApiUsageMeter
import org.every.nook.api.application.billing.ExternalApiUsageStatus
import org.every.nook.api.application.billing.ReserveExternalApiUsage
import org.every.nook.api.application.billing.SettleExternalApiUsage
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class ExternalApiCallMeter(private val usageMeter: ExternalApiUsageMeter) {
    @Suppress("TooGenericExceptionCaught") // Every provider failure must settle the reserved usage event.
    fun <T> measure(
        provider: String,
        sku: String,
        feature: String,
        estimatedUnits: BigDecimal = BigDecimal.ONE,
        metadata: Map<String, String> = emptyMap(),
        usage: (T) -> SettledUsage = { SettledUsage(estimatedUnits) },
        action: () -> T,
    ): T {
        val reservation = usageMeter.reserve(
            ReserveExternalApiUsage(
                idempotencyKey = UUID.randomUUID().toString(),
                provider = provider,
                sku = sku,
                feature = feature,
                estimatedUnits = estimatedUnits,
                metadata = metadata,
            ),
        )
        return try {
            action().also { result ->
                val settled = usage(result)
                usageMeter.settle(
                    SettleExternalApiUsage(
                        reservationId = reservation.id,
                        status = ExternalApiUsageStatus.SUCCEEDED,
                        actualUnits = settled.units,
                        inputTokens = settled.inputTokens,
                        cachedInputTokens = settled.cachedInputTokens,
                        outputTokens = settled.outputTokens,
                    ),
                )
            }
        } catch (exception: RuntimeException) {
            usageMeter.settle(
                SettleExternalApiUsage(
                    reservationId = reservation.id,
                    status = ExternalApiUsageStatus.FAILED,
                    actualUnits = estimatedUnits,
                    failureCode = exception::class.simpleName,
                ),
            )
            throw exception
        }
    }
}

data class SettledUsage(
    val units: BigDecimal,
    val inputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val outputTokens: Long? = null,
)
