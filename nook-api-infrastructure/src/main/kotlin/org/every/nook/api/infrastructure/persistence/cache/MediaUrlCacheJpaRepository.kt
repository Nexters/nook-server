package org.every.nook.api.infrastructure.persistence.cache

import org.springframework.data.jpa.repository.JpaRepository

interface MediaUrlCacheJpaRepository : JpaRepository<MediaUrlCacheEntity, Long> {
    fun findBySourceUrlHash(sourceUrlHash: String): MediaUrlCacheEntity?
}
