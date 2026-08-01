package org.every.nook.api.infrastructure.place

import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.PlaceSearchProviderException
import org.every.nook.api.application.place.PlaceSearchProviderTimeoutException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService

class CompositePlaceSearchProvider(
    private val providers: List<NamedPlaceSearchProvider>,
    private val executor: ExecutorService,
) : PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        val completionService = ExecutorCompletionService<ProviderResult>(executor)
        val pending = providers.map { provider ->
            completionService.submit { provider.searchSafely(request) }
        }
        val results = mutableListOf<ProviderResult>()

        repeat(pending.size) {
            val result = completionService.take().get()
            results += result
            if (result is ProviderResult.Success && result.candidates.isNotEmpty()) {
                pending.asSequence()
                    .filterNot { future -> future.isDone }
                    .forEach { future -> future.cancel(true) }
                return result.candidates
            }
        }

        val firstNonEmptySuccess = results
            .filterIsInstance<ProviderResult.Success>()
            .firstOrNull { it.candidates.isNotEmpty() }

        if (firstNonEmptySuccess != null) {
            return firstNonEmptySuccess.candidates
        }

        val emptySuccess = results.filterIsInstance<ProviderResult.Success>().firstOrNull()
        if (emptySuccess != null) {
            return emptySuccess.candidates
        }

        if (results.all { it is ProviderResult.Timeout }) {
            throw PlaceSearchProviderTimeoutException()
        }

        throw PlaceSearchProviderException()
    }

    private fun NamedPlaceSearchProvider.searchSafely(request: PlaceSearchProvider.Request): ProviderResult = try {
        ProviderResult.Success(name, provider.search(request))
    } catch (exception: PlaceSearchProviderTimeoutException) {
        logger.warn(exception) { "Place search provider timed out: provider=$name, query=${request.query}" }
        ProviderResult.Timeout(name)
    } catch (exception: PlaceSearchProviderException) {
        logger.warn(exception) { "Place search provider failed: provider=$name, query=${request.query}" }
        ProviderResult.Failure(name)
    }

    data class NamedPlaceSearchProvider(val name: String, val provider: PlaceSearchProvider)

    private sealed interface ProviderResult {
        val provider: String

        data class Success(override val provider: String, val candidates: List<PlaceCandidate>) : ProviderResult
        data class Failure(override val provider: String) : ProviderResult
        data class Timeout(override val provider: String) : ProviderResult
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
