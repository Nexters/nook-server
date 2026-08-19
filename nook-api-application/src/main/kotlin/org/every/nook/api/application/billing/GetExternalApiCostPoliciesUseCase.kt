package org.every.nook.api.application.billing

class GetExternalApiCostPoliciesUseCase(private val port: ExternalApiCostManagementPort) {
    operator fun invoke(): ExternalApiCostPolicies = port.listPolicies()
}
