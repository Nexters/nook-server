package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.LockModeType
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlaceParsingJobJpaRepository : JpaRepository<PlaceParsingJobEntity, Long> {
    fun findByPostId(postId: Long): PlaceParsingJobEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM PlaceParsingJobEntity job WHERE job.postId = :postId")
    fun findByPostIdForUpdate(@Param("postId") postId: Long): PlaceParsingJobEntity?

    fun findAllByStatusIn(statuses: Collection<PlaceParsingStatus>): List<PlaceParsingJobEntity>
}
