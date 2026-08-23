package org.every.nook.api.infrastructure.persistence.processing

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessingTraceJpaRepository : JpaRepository<ProcessingTraceEntity, Long> {
    fun findAllByPostIdOrderByCreatedAtAscIdAsc(postId: Long): List<ProcessingTraceEntity>
}
