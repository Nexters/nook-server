package org.every.nook.api.infrastructure.persistence.group

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "group_posts",
    indexes = [Index(name = "idx_user_saved_post_id", columnList = "user_saved_post_id")],
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_group_id_user_saved_post_id",
            columnNames = ["group_id", "user_saved_post_id"],
        ),
    ],
)
class GroupPostEntity(
    @Column(name = "group_id", nullable = false)
    val groupId: Long,
    @Column(name = "user_saved_post_id", nullable = false)
    val userSavedPostId: Long,
) : BaseEntity() {
    @Column(name = "deleted_at", nullable = true)
    var deletedAt: Instant? = null
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun softDelete(now: Instant) {
        deletedAt = now
    }

    fun restore() {
        deletedAt = null
    }
}
