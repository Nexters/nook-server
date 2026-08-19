package org.every.nook.api.infrastructure.place

import mu.KotlinLogging
import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider

class RuntimePlaceSearchProvider(
    private val providers: Map<PlaceParsingProviderType, PlaceSearchProvider>,
    private val configurationReader: RuntimeConfigurationReader,
) : PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        val configured = configurationReader.findValue(PlaceParsingProviderType.CONFIGURATION_KEY)
        val chain = PlaceParsingProviderType.parse(configured).ifEmpty { listOf(PlaceParsingProviderType.LEGACY) }
        for (type in chain) {
            if (type == PlaceParsingProviderType.DISABLED) return emptyList()
            val provider = providers[type] ?: continue
            val candidates = runCatching { provider.search(request) }
                .onFailure { logger.warn(it) { "Place parsing provider failed: provider=$type" } }
                .getOrDefault(emptyList())
            if (candidates.isNotEmpty()) return candidates
            logger.info { "Place parsing provider fallback: provider=$type, reason=empty" }
        }
        return emptyList()
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
