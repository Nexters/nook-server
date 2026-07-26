package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PlaceParsingJobJpaRepository : JpaRepository<PlaceParsingJobEntity, Long> {
    fun findByPostId(postId: Long): PlaceParsingJobEntity?

    @Query(
        value = """
            SELECT *
            FROM place_parsing_jobs
            WHERE status = 'PENDING'
            ORDER BY updated_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findNextPendingForUpdate(): PlaceParsingJobEntity?
}
