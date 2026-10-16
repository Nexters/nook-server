package org.every.nook.api.infrastructure.persistence.admin

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PlaceReprocessingJobJpaRepository : JpaRepository<PlaceReprocessingJobEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM PlaceReprocessingJobEntity job WHERE job.placeId = :placeId")
    fun findForUpdate(@Param("placeId") placeId: Long): PlaceReprocessingJobEntity?

    @Query(
        """
        SELECT job.placeId FROM PlaceReprocessingJobEntity job
        WHERE job.status = org.every.nook.api.application.admin.PlaceReprocessingStatus.PENDING
           OR (job.status = org.every.nook.api.application.admin.PlaceReprocessingStatus.PROCESSING
               AND job.updatedAt <= :timeoutAt)
        ORDER BY job.updatedAt, job.placeId
        """,
    )
    fun findClaimableIds(@Param("timeoutAt") timeoutAt: Instant, pageable: Pageable): List<Long>
}
