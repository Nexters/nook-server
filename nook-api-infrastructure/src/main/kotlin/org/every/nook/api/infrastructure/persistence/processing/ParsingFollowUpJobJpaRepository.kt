package org.every.nook.api.infrastructure.persistence.processing

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ParsingFollowUpJobJpaRepository : JpaRepository<ParsingFollowUpJobEntity, Long> {
    fun findAllByPostIdInOrderByPostIdDescIdAsc(postIds: List<Long>): List<ParsingFollowUpJobEntity>

    fun countByStatusAndNextAttemptAtLessThanEqual(status: ParsingFollowUpJobStatus, now: Instant): Long

    fun findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        status: ParsingFollowUpJobStatus,
        now: Instant,
    ): ParsingFollowUpJobEntity?

    fun countByStatus(status: ParsingFollowUpJobStatus): Long

    fun countByStatusAndUpdatedAtLessThanEqual(status: ParsingFollowUpJobStatus, cutoff: Instant): Long

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

    @Query(
        """
        SELECT job FROM ParsingFollowUpJobEntity job
        WHERE (:postId IS NULL OR job.postId = :postId)
          AND (:status IS NULL OR job.status = :status)
          AND (:beforeId IS NULL OR job.id < :beforeId)
        ORDER BY job.id DESC
        """,
    )
    fun findForAdmin(
        @Param("postId") postId: Long?,
        @Param("status") status: ParsingFollowUpJobStatus?,
        @Param("beforeId") beforeId: Long?,
        pageable: Pageable,
    ): List<ParsingFollowUpJobEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ParsingFollowUpJobEntity job WHERE job.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): ParsingFollowUpJobEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ParsingFollowUpJobEntity job WHERE job.postId = :postId ORDER BY job.id")
    fun findByPostIdForUpdate(@Param("postId") postId: Long): List<ParsingFollowUpJobEntity>
}
