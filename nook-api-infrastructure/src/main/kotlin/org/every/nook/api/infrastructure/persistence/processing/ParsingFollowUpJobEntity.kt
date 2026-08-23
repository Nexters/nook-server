package org.every.nook.api.infrastructure.persistence.processing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "parsing_follow_up_jobs",
    indexes = [
        Index(name = "idx_status_next_attempt_at", columnList = "status, next_attempt_at"),
        Index(name = "idx_status_updated_at", columnList = "status, updated_at"),
        Index(name = "idx_job_type_created_at", columnList = "job_type, created_at"),
        Index(name = "idx_post_id", columnList = "post_id"),
    ],
)
class ParsingFollowUpJobEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    val jobType: ParsingFollowUpJobType,
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "payload", nullable = false, columnDefinition = "MEDIUMTEXT")
    val payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ParsingFollowUpJobStatus = ParsingFollowUpJobStatus.PENDING,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    var nextAttemptAt: Instant,
    @Column(name = "failure_reason", nullable = true, length = FAILURE_REASON_LENGTH)
    var failureReason: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val FAILURE_REASON_LENGTH = 500
    }
}

enum class ParsingFollowUpJobType {
    POST_MEDIA,
    PLACE_THUMBNAILS,
    PLACE_TAGS,
}

enum class ParsingFollowUpJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
