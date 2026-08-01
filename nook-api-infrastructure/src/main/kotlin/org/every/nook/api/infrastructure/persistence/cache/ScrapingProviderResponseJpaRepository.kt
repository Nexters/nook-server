package org.every.nook.api.infrastructure.persistence.cache

import org.springframework.data.jpa.repository.JpaRepository

interface ScrapingProviderResponseJpaRepository : JpaRepository<ScrapingProviderResponseEntity, Long> {
    fun findByProviderAndSourceTypeAndExternalPostId(
        provider: String,
        sourceType: String,
        externalPostId: String,
    ): ScrapingProviderResponseEntity?
}
