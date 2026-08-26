package org.every.nook.api.infrastructure.persistence.providerusage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.time.Instant

@Entity
@Table(
    name = "openai_token_usage_events",
    indexes = [Index(name = "idx_occurred_at_feature_model", columnList = "occurred_at,feature,model")],
)
class OpenAiTokenUsageEntity(
    @Column(name = "feature", nullable = false, length = 50)
    val feature: String,
    @Column(name = "model", nullable = false, length = 100)
    val model: String,
    @Column(name = "input_tokens", nullable = false)
    val inputTokens: Long,
    @Column(name = "cached_input_tokens", nullable = false)
    val cachedInputTokens: Long,
    @Column(name = "output_tokens", nullable = false)
    val outputTokens: Long,
    @Column(name = "total_tokens", nullable = false)
    val totalTokens: Long,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}
