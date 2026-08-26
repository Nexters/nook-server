package org.every.nook.api.application.providerusage

data class ExternalProviderCatalogEntry(
    val provider: String,
    val displayName: String,
    val category: String,
    val purpose: String,
    val runtimes: List<String>,
    val credentialConfigured: Boolean,
    val operationalState: String,
    val stateReason: String,
    val policy: String,
)

fun interface ExternalProviderCatalogPort {
    fun get(): List<ExternalProviderCatalogEntry>
}

data class ExternalProviderOverview(val providers: List<ExternalProviderCatalogEntry>)

class GetExternalProviderOverviewUseCase(private val catalog: ExternalProviderCatalogPort) {
    operator fun invoke() = ExternalProviderOverview(catalog.get())
}
