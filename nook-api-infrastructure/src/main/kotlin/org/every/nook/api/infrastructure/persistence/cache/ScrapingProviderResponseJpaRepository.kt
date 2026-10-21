package org.every.nook.api.infrastructure.persistence.cache

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface ScrapingProviderResponseJpaRepository : JpaRepository<ScrapingProviderResponseEntity, Long> {
    @Modifying
    @Transactional
    @Query(
        "DELETE FROM ScrapingProviderResponseEntity r WHERE r.provider = :provider " +
            "AND r.sourceType = :sourceType AND r.externalPostId = :externalPostId " +
            "AND r.responseBody = :responseBody",
    )
    fun deleteMatchingResponse(provider: String, sourceType: String, externalPostId: String, responseBody: String): Int

    fun findByProviderAndSourceTypeAndExternalPostId(
        provider: String,
        sourceType: String,
        externalPostId: String,
    ): ScrapingProviderResponseEntity?
}
