package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository

interface PlaceParsingJobJpaRepository : JpaRepository<PlaceParsingJobEntity, Long> {
    fun findByPostId(postId: Long): PlaceParsingJobEntity?
}
