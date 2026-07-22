package org.every.nook.api.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @CreatedDate
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false,
        columnDefinition = "TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)",
    )
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(
        name = "updated_at",
        nullable = false,
        columnDefinition = "TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)",
    )
    lateinit var updatedAt: Instant
        protected set
}
