package org.every.nook.api.infrastructure.place

import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSearchProvider
import org.every.nook.api.application.place.PlaceSearchProviderException
import org.every.nook.api.application.place.PlaceSearchProviderTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class CompositePlaceSearchProvider(
    private val providers: List<NamedPlaceSearchProvider>,
    private val executor: Executor,
) : PlaceSearchProvider {
    override fun search(request: PlaceSearchProvider.Request): List<PlaceCandidate> {
        val pending = providers.map { provider ->
            CompletableFuture.supplyAsync({ provider.searchSafely(request) }, executor)
        }.toMutableList()
        val results = mutableListOf<ProviderResult>()

        while (pending.isNotEmpty()) {
            CompletableFuture.anyOf(*pending.toTypedArray()).join()
            val completed = pending.filter(CompletableFuture<ProviderResult>::isDone)
            completed.forEach { future ->
                val result = future.join()
                results += result
                if (result is ProviderResult.Success && result.candidates.isNotEmpty()) {
                    return result.candidates
                }
            }
            pending.removeAll(completed)
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
