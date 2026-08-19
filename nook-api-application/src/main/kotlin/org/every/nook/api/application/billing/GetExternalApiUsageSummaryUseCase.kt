package org.every.nook.api.application.billing

class GetExternalApiUsageSummaryUseCase(private val queryPort: ExternalApiUsageQueryPort) {
    operator fun invoke(query: ExternalApiUsageQuery): List<ExternalApiUsageSummary> {
        require(query.from < query.to) { "Usage query start must precede end" }
        return queryPort.summarize(query)
    }
}
