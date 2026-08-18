package org.every.nook.api.infrastructure.persistence.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(
    name = "admin_audit_logs",
    indexes = [
        Index(name = "idx_target_type_target_id_created_at", columnList = "target_type, target_id, created_at"),
        Index(name = "idx_actor_email_created_at", columnList = "actor_email, created_at"),
    ],
)
class AdminAuditLogEntity(
    @Column(name = "actor_subject", nullable = false, length = ACTOR_SUBJECT_LENGTH)
    val actorSubject: String,
    @Column(name = "actor_email", nullable = false, length = ACTOR_EMAIL_LENGTH)
    val actorEmail: String,
    @Column(name = "action", nullable = false, length = ACTION_LENGTH)
    val action: String,
    @Column(name = "target_type", nullable = false, length = TARGET_TYPE_LENGTH)
    val targetType: String,
    @Column(name = "target_id", nullable = false, length = TARGET_ID_LENGTH)
    val targetId: String,
    @Column(name = "reason", nullable = false, length = REASON_LENGTH)
    val reason: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", nullable = true, columnDefinition = "JSON")
    val beforeValue: String?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", nullable = true, columnDefinition = "JSON")
    val afterValue: String?,
    @Column(name = "request_id", nullable = true, length = REQUEST_ID_LENGTH)
    val requestId: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val ACTOR_SUBJECT_LENGTH = 255
        const val ACTOR_EMAIL_LENGTH = 320
        const val ACTION_LENGTH = 100
        const val TARGET_TYPE_LENGTH = 100
        const val TARGET_ID_LENGTH = 255
        const val REASON_LENGTH = 500
        const val REQUEST_ID_LENGTH = 100
    }
}
