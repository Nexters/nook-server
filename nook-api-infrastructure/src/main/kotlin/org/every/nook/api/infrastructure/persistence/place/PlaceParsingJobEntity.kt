package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.place.PlaceParsingJob
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "place_parsing_jobs",
    indexes = [Index(name = "idx_status_updated_at", columnList = "status, updated_at")],
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_post_id", columnNames = ["post_id"]),
    ],
)
class PlaceParsingJobEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = STATUS_LENGTH,
        columnDefinition = "VARCHAR(20) COLLATE utf8mb4_bin",
    )
    var status: PlaceParsingStatus,
    @Column(
        name = "failure_reason",
        nullable = true,
        length = PlaceParsingJob.MAX_FAILURE_REASON_LENGTH,
    )
    var failureReason: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val STATUS_LENGTH = 20
    }
}
