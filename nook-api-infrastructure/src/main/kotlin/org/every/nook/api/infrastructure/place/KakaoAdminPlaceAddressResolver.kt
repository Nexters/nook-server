package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.admin.AdminPlaceAddressProviderException
import org.every.nook.api.application.admin.AdminPlaceAddressProviderTimeoutException
import org.every.nook.api.application.admin.AdminPlaceAddressResolver
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.SocketTimeoutException

class KakaoAdminPlaceAddressResolver(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val properties: KakaoPlaceProperties,
) : AdminPlaceAddressResolver {
    override fun resolve(address: String): AdminPlaceAddressResolver.ResolvedAddress? {
        ensureConfigured()
        val responseBody = request(address)
        return runCatching {
            val response = objectMapper.readValue(responseBody, KakaoAddressResponse::class.java)
            if (response.meta.totalCount != 1 || response.documents.size != 1) {
                return null
            }
            response.documents.single().toResolvedAddress()
        }.getOrElse(::providerFailure)
    }

    private fun request(address: String): String? = try {
        restClient.get()
            .uri { builder -> builder.path(ADDRESS_SEARCH_PATH).queryParam(QUERY, address).build() }
            .header(AUTHORIZATION, "$AUTHORIZATION_PREFIX${properties.restApiKey}")
            .retrieve()
            .body(String::class.java)
    } catch (exception: RestClientResponseException) {
        providerFailure(exception)
    } catch (exception: ResourceAccessException) {
        if (exception.hasSocketTimeoutCause()) {
            providerTimeout(exception)
        }
        providerFailure(exception)
    }

    private fun ensureConfigured() {
        if (properties.restApiKey.isBlank()) providerFailure()
    }

    private fun KakaoAddressResponse.Document.toResolvedAddress(): AdminPlaceAddressResolver.ResolvedAddress? {
        val canonicalAddress = roadAddress?.addressName?.takeIf(String::isNotBlank)
            ?: addressName?.takeIf(String::isNotBlank)
            ?: return null
        val coordinates = x?.toBigDecimalOrNull()?.let { longitude ->
            y?.toBigDecimalOrNull()?.let { latitude -> latitude to longitude }
        } ?: return null
        return AdminPlaceAddressResolver.ResolvedAddress(canonicalAddress, coordinates.first, coordinates.second)
    }

    private fun Throwable.hasSocketTimeoutCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is SocketTimeoutException }

    private fun providerTimeout(cause: Throwable? = null): Nothing =
        throw AdminPlaceAddressProviderTimeoutException(cause)

    private fun providerFailure(cause: Throwable? = null): Nothing = throw AdminPlaceAddressProviderException(cause)

    private companion object {
        const val ADDRESS_SEARCH_PATH = "/v2/local/search/address.json"
        const val QUERY = "query"
        const val AUTHORIZATION = "Authorization"
        const val AUTHORIZATION_PREFIX = "KakaoAK "
    }
}
