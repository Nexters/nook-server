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
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? = fetchAll(listOf(request)).single()

    override fun fetchAll(requests: List<PlaceThumbnailProvider.Request>): List<PlaceSupplement?> =
        fetchAll(requests) { _, _ -> }

    override fun fetchAll(
        requests: List<PlaceThumbnailProvider.Request>,
        onPhotosResolved: (PlaceThumbnailProvider.Request, PlaceSupplement) -> Unit,
    ): List<PlaceSupplement?> {
        if (requests.isEmpty()) return emptyList()
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
                sourcePostId = requests.first().sourcePostId,
                fields = mapOf(
                    "provider.chain" to chain.joinToString(","),
                    "provider.request_count" to requests.size,
                ),
            ),
        )

        val accumulated = MutableList<PlaceSupplement?>(requests.size) { null }
        val remaining = requests.indices.toMutableSet()
        val activeProviders = chain.takeWhile { it != PlaceThumbnailProviderType.DISABLED }
            .mapNotNull { type -> providers[type]?.let { type to it } }
        for ((type, provider) in activeProviders) {
            val pendingIndexes = remaining.toList()
            val pendingRequests = pendingIndexes.map(requests::get)
            val supplements = runCatching { provider.fetchAll(pendingRequests) }
                .onFailure { exception ->
                    pendingRequests.forEach { request -> logFallback(request, type, "failure", exception) }
                }
                .getOrElse { List(pendingRequests.size) { null } }
            require(supplements.size == pendingRequests.size) { "Thumbnail provider result size mismatch" }
            pendingIndexes.zip(supplements).forEach { (index, supplement) ->
                accumulated[index] = accumulated[index].merge(supplement)
                if (!supplement?.photoUrls.isNullOrEmpty()) {
                    remaining.remove(index)
                    onPhotosResolved(requests[index], requireNotNull(accumulated[index]))
                } else {
                    logFallback(requests[index], type, "empty", null)
                }
            }
            if (remaining.isEmpty()) break
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
