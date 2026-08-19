package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.config.RuntimeConfigurationReader
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.warn
import org.slf4j.LoggerFactory

class RuntimePlaceThumbnailProvider(
    private val providers: Map<PlaceThumbnailProviderType, PlaceThumbnailProvider>,
    private val configurationReader: RuntimeConfigurationReader,
    private val legacyChain: List<PlaceThumbnailProviderType>,
) : PlaceThumbnailProvider {
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? {
        val configuredValue = configurationReader.findValue(PlaceThumbnailProviderType.CONFIGURATION_KEY)
        val configuredChain = PlaceThumbnailProviderType.parse(configuredValue)
        val chain = configuredChain.ifEmpty { legacyChain }
        val unknownProviders = configuredValue.unknownProviders()
        if (unknownProviders.isNotEmpty()) {
            logger.warn("Unknown place thumbnail providers {}; ignoring them", unknownProviders)
        }
        if (configuredValue != null && configuredChain.isEmpty()) {
            logger.warn("Unknown place thumbnail provider chain '{}'; using {}", configuredValue, legacyChain)
        }
        logger.info(
            ProcessingLogEvent(
                action = "place.thumbnail.provider.chain.selected",
                flow = "place-thumbnail",
                stage = "fetch",
                outcome = "success",
                sourcePostId = request.sourcePostId,
                fields = mapOf("provider.chain" to chain.joinToString(",")),
            ),
        )

        var accumulated: PlaceSupplement? = null
        chain.forEach { type ->
            if (type == PlaceThumbnailProviderType.DISABLED) return accumulated
            val provider = providers[type] ?: return@forEach
            val supplement = runCatching { provider.fetch(request) }
                .onFailure { exception -> logFallback(request, type, "failure", exception) }
                .getOrNull()
            accumulated = accumulated.merge(supplement)
            if (!supplement?.photoUrls.isNullOrEmpty()) {
                return accumulated
            }
            logFallback(request, type, "empty", null)
        }
        return accumulated
    }

    private fun String?.unknownProviders(): List<String> = orEmpty().split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { token -> PlaceThumbnailProviderType.entries.none { it.name == token.uppercase() } }

    private fun PlaceSupplement?.merge(next: PlaceSupplement?): PlaceSupplement? {
        if (this == null) return next
        if (next == null) return this
        return PlaceSupplement(
            openingHours = openingHours ?: next.openingHours,
            photoUrls = if (photoUrls.isNotEmpty()) photoUrls else next.photoUrls,
            googlePlaceId = googlePlaceId ?: next.googlePlaceId,
            replaceThumbnailUrl = replaceThumbnailUrl ?: next.replaceThumbnailUrl,
        )
    }

    private fun logFallback(
        request: PlaceThumbnailProvider.Request,
        provider: PlaceThumbnailProviderType,
        reason: String,
        exception: Throwable?,
    ) {
        logger.warn(
            ProcessingLogEvent(
                action = "place.thumbnail.provider.fallback",
                flow = "place-thumbnail",
                stage = "fetch",
                outcome = "fallback",
                sourcePostId = request.sourcePostId,
                fields = mapOf(
                    "provider.name" to provider.name,
                    "failure.reason" to reason,
                    "place.external_id" to request.place.externalPlaceId,
                ),
            ),
            exception,
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(RuntimePlaceThumbnailProvider::class.java)
    }
}
