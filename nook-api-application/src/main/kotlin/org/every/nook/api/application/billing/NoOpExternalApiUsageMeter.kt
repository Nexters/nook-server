package org.every.nook.api.application.billing

object NoOpExternalApiUsageMeter : ExternalApiUsageMeter {
    override fun reserve(command: ReserveExternalApiUsage): UsageReservation = UsageReservation(
        id = 0,
        idempotencyKey = command.idempotencyKey,
    )

    override fun settle(command: SettleExternalApiUsage) = Unit
}
