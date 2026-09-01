package org.every.nook.api.infrastructure.persistence.providerusage

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface OpenAiTokenUsageJpaRepository : JpaRepository<OpenAiTokenUsageEntity, Long> {
    fun findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
        from: Instant,
        to: Instant,
    ): List<OpenAiTokenUsageEntity>
}
