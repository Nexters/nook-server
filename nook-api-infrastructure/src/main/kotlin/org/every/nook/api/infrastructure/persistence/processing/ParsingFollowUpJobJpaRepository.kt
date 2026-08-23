package org.every.nook.api.infrastructure.persistence.processing

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ParsingFollowUpJobJpaRepository : JpaRepository<ParsingFollowUpJobEntity, Long> {
    @Query(
        """
        SELECT job.id FROM ParsingFollowUpJobEntity job
        WHERE (job.status = org.every.nook.api.infrastructure.persistence.processing.ParsingFollowUpJobStatus.PENDING
               AND job.nextAttemptAt <= :now)
           OR (job.status = org.every.nook.api.infrastructure.persistence.processing.ParsingFollowUpJobStatus.PROCESSING
               AND job.updatedAt <= :timeoutAt)
        ORDER BY job.nextAttemptAt ASC, job.id ASC
        """,
    )
    fun findClaimableIds(
        @Param("now") now: Instant,
        @Param("timeoutAt") timeoutAt: Instant,
        pageable: Pageable,
    ): List<Long>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ParsingFollowUpJobEntity job WHERE job.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): ParsingFollowUpJobEntity?
}
