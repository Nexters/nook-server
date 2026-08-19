package org.every.nook.api.application.billing

class SaveExternalApiPriceUseCase(private val port: ExternalApiCostManagementPort) {
    operator fun invoke(command: SaveExternalApiPriceCommand): ExternalApiPricePolicy {
        require(command.provider.isNotBlank()) { "Provider is required" }
        require(command.sku.isNotBlank()) { "SKU is required" }
        require(command.unitPriceKrw.signum() >= 0) { "Unit price must not be negative" }
        require(command.unitSize.signum() > 0) { "Unit size must be positive" }
        return port.savePrice(command)
    }
}
