package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.LockModeType
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PlaceParsingJobJpaRepository : JpaRepository<PlaceParsingJobEntity, Long> {
    fun findByPostId(postId: Long): PlaceParsingJobEntity?

    fun findAllByPostIdIn(postIds: Collection<Long>): List<PlaceParsingJobEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM PlaceParsingJobEntity job WHERE job.postId = :postId")
    fun findByPostIdForUpdate(@Param("postId") postId: Long): PlaceParsingJobEntity?

    fun findAllByStatusIn(statuses: Collection<PlaceParsingStatus>): List<PlaceParsingJobEntity>

    fun findAllByStatusInOrderByNextAttemptAtAsc(
        statuses: Collection<PlaceParsingStatus>,
        pageable: Pageable,
    ): List<PlaceParsingJobEntity>

    fun countByStatusAndNextAttemptAtLessThanEqual(status: PlaceParsingStatus, now: Instant): Long

    fun findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        status: PlaceParsingStatus,
        now: Instant,
    ): PlaceParsingJobEntity?

    fun countByStatus(status: PlaceParsingStatus): Long

    fun countByStatusAndUpdatedAtLessThanEqual(status: PlaceParsingStatus, cutoff: Instant): Long
}
