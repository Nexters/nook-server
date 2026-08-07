package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.application.place.InferredPlaceTag
import org.every.nook.api.application.place.PlaceTagEvidenceSource
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.math.BigDecimal

@Entity
@Table(
    name = "post_place_tags",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_post_id_place_id_tag", columnNames = ["post_id", "place_id", "tag"]),
    ],
)
class PostPlaceTagEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "place_id", nullable = false)
    val placeId: Long,
    @Column(name = "tag", nullable = false, length = TAG_MAX_LENGTH)
    @Enumerated(EnumType.STRING)
    val tag: PlaceTag,
    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    val confidence: BigDecimal,
    @Column(name = "evidence_source", nullable = false, length = EVIDENCE_SOURCE_MAX_LENGTH)
    @Enumerated(EnumType.STRING)
    val evidenceSource: PlaceTagEvidenceSource,
    @Column(name = "evidence_text", nullable = false, length = EVIDENCE_TEXT_MAX_LENGTH)
    val evidenceText: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val TAG_MAX_LENGTH = 30
        const val EVIDENCE_SOURCE_MAX_LENGTH = 30
        const val EVIDENCE_TEXT_MAX_LENGTH = 500
    }
}

fun InferredPlaceTag.toEntity(postId: Long, placeId: Long): PostPlaceTagEntity = PostPlaceTagEntity(
    postId = postId,
    placeId = placeId,
    tag = tag,
    confidence = BigDecimal.valueOf(confidence),
    evidenceSource = evidenceSource,
    evidenceText = evidenceText.take(PostPlaceTagEntity.EVIDENCE_TEXT_MAX_LENGTH),
)
