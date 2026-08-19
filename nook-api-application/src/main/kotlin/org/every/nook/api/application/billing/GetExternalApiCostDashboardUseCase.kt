package org.every.nook.api.application.billing

class GetExternalApiCostDashboardUseCase(private val port: ExternalApiCostManagementPort) {
    operator fun invoke(query: ExternalApiUsageQuery): ExternalApiCostDashboard {
        require(query.from < query.to) { "Dashboard start must precede end" }
        return port.dashboard(query)
    }
}
