package org.every.nook.api.infrastructure.persistence.cache

import org.springframework.data.jpa.repository.JpaRepository

interface BrightDataResponseJpaRepository : JpaRepository<BrightDataResponseEntity, Long> {
    fun findBySourceTypeAndExternalPostId(sourceType: String, externalPostId: String): BrightDataResponseEntity?
}
