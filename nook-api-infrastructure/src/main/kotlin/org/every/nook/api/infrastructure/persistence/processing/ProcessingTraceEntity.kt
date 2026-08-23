package org.every.nook.api.infrastructure.persistence.processing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "post_processing_traces",
    indexes = [
        Index(name = "idx_post_id_created_at", columnList = "post_id, created_at"),
    ],
)
class ProcessingTraceEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "flow", nullable = false, length = FLOW_LENGTH)
    val flow: String,
    @Column(name = "stage", nullable = false, length = STAGE_LENGTH)
    val stage: String,
    @Column(name = "action", nullable = false, length = ACTION_LENGTH)
    val action: String,
    @Column(name = "outcome", nullable = false, length = OUTCOME_LENGTH)
    val outcome: String,
    @Column(name = "attempt", nullable = true)
    val attempt: Int?,
    @Column(name = "duration_ms", nullable = true)
    val durationMs: Long?,
    @Column(name = "details", nullable = true, columnDefinition = "TEXT")
    val details: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val FLOW_LENGTH = 30
        const val STAGE_LENGTH = 50
        const val ACTION_LENGTH = 80
        const val OUTCOME_LENGTH = 20
    }
}
