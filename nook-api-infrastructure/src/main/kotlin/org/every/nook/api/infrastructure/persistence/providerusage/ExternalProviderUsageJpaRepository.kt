package org.every.nook.api.infrastructure.persistence.providerusage

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ExternalProviderUsageJpaRepository : JpaRepository<ExternalProviderUsageEntity, Long> {
    fun findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): List<ExternalProviderUsageEntity>

    fun findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
        from: Instant,
        to: Instant,
    ): List<ExternalProviderUsageEntity>
}
