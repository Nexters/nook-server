package org.every.nook.api.infrastructure.persistence.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "post_place_reviews",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_post_id", columnNames = ["post_id"])],
)
class PostPlaceReviewEntity(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "reviewer_subject", nullable = false, length = AdminAuditLogEntity.ACTOR_SUBJECT_LENGTH)
    var reviewerSubject: String,
    @Column(name = "reviewer_email", nullable = false, length = AdminAuditLogEntity.ACTOR_EMAIL_LENGTH)
    var reviewerEmail: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun reviewedBy(subject: String, email: String) {
        reviewerSubject = subject
        reviewerEmail = email
    }
}
