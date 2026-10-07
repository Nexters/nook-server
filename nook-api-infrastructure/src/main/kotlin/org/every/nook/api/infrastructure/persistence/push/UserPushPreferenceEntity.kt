package org.every.nook.api.infrastructure.persistence.push

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
    name = "user_push_preferences",
    uniqueConstraints = [
        UniqueConstraint(name = "idx_u_user_id", columnNames = ["user_id"]),
    ],
)
class UserPushPreferenceEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "post_processing_enabled", nullable = false)
    var postProcessingEnabled: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}
