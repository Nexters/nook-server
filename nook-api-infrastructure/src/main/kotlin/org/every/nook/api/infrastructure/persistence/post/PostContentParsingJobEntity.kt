package org.every.nook.api.infrastructure.persistence.post

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
import org.every.nook.api.application.processing.ParsingProgress
import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "post_content_parsing_jobs",
    indexes = [
        Index(name = "idx_status_updated_at", columnList = "status, updated_at"),
        Index(name = "idx_status_next_attempt_at", columnList = "status, next_attempt_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_post_id", columnNames = ["post_id"]),
    ],
)
class PostContentParsingJobEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = STATUS_LENGTH,
        columnDefinition = "VARCHAR(20) COLLATE utf8mb4_bin",
    )
    var status: PostContentParsingStatus,
    @Column(name = "failure_reason", nullable = true, length = FAILURE_REASON_MAX_LENGTH)
    var failureReason: String? = null,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    var nextAttemptAt: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "progress_stage", nullable = true, length = PROGRESS_STAGE_LENGTH)
    var progressStage: ParsingProgressStage? = null,
    @Column(name = "progress_stage_started_at", nullable = true, columnDefinition = "TIMESTAMP(6)")
    var progressStageStartedAt: Instant? = null,
    @Column(name = "progress_percent", nullable = false)
    var progressPercent: Int = 5,
    @Column(
        name = "last_failed_at",
        nullable = true,
        columnDefinition = "TIMESTAMP(6) COMMENT '마지막 실제 실패 시각; 과거 미기록은 NULL'",
    )
    var lastFailedAt: Instant? = null,
    @Column(
        name = "retry_attempt_count",
        nullable = false,
        columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '현재 재시도 회차의 실행 횟수'",
    )
    var retryAttemptCount: Int = 0,
    @Column(
        name = "execution_stage",
        nullable = true,
        length = 40,
        columnDefinition = "VARCHAR(40) COMMENT '현재 실행이 진입한 단계'",
    )
    var executionStage: String? = null,
    @Column(
        name = "last_failure_stage",
        nullable = true,
        length = 40,
        columnDefinition = "VARCHAR(40) COMMENT '마지막 실제 실패 단계'",
    )
    var lastFailureStage: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun advanceProgress(stage: ParsingProgressStage, now: Instant) {
        val currentPercent = progress().percentAt(now)
        if (stage == progressStage || stage.startPercent < currentPercent) return
        progressStage = stage
        progressStageStartedAt = now
        progressPercent = stage.startPercent
    }

    fun freezeProgress(now: Instant) {
        progressPercent = progress().percentAt(now)
        progressStageStartedAt = now
    }

    fun resumeProgress(now: Instant) {
        progressStageStartedAt = now.takeIf { progressStage != null }
    }

    fun progress() = ParsingProgress(progressStage, progressStageStartedAt, progressPercent)

    companion object {
        const val STATUS_LENGTH = 20
        const val FAILURE_REASON_MAX_LENGTH = 500
        const val PROGRESS_STAGE_LENGTH = 40
    }
}
