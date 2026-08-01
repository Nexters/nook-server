package org.every.nook.api.infrastructure.persistence.cache

import org.springframework.stereotype.Repository

@Repository
class ScrapingProviderResponseCache(private val repository: ScrapingProviderResponseJpaRepository) {
    fun find(provider: String, sourceType: String, externalPostId: String): String? =
        repository.findByProviderAndSourceTypeAndExternalPostId(provider, sourceType, externalPostId)?.responseBody

    fun save(provider: String, sourceType: String, externalPostId: String, responseBody: String) {
        runCatching {
            repository.saveAndFlush(
                ScrapingProviderResponseEntity(provider, sourceType, externalPostId, responseBody),
            )
        }.onFailure { exception ->
            if (find(provider, sourceType, externalPostId) == null) {
                throw exception
            }
        }
    }
}
