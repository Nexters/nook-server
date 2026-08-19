package org.every.nook.api.application.billing

class SaveExternalApiBudgetUseCase(private val port: ExternalApiCostManagementPort) {
    operator fun invoke(command: SaveExternalApiBudgetCommand): ExternalApiBudgetPolicy {
        require(command.provider.isNotBlank()) { "Provider is required" }
        require(command.monthlyBudgetKrw.signum() >= 0) { "Monthly budget must not be negative" }
        require(command.mode in MODES) { "Budget mode must be ALERT_ONLY or BLOCK" }
        return port.saveBudget(command)
    }

    private companion object {
        val MODES = setOf("ALERT_ONLY", "BLOCK")
    }
}
